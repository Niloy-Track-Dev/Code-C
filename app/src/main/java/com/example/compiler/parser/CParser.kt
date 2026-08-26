package com.example.compiler.parser

import com.example.compiler.lexer.Token
import com.example.compiler.lexer.TokenType
import com.example.compiler.model.Diagnostic
import com.example.compiler.model.DiagnosticSeverity

class CParseException(val diagnostic: Diagnostic) : Exception(diagnostic.message)

class CParser(
    private val tokens: List<Token>,
    private val sourceCode: String = ""
) {
    private var current = 0
    val diagnostics = mutableListOf<Diagnostic>()
    private val typedefs = mutableMapOf<String, CType>()
    private val sourceLines = sourceCode.lines()

    fun parse(): TranslationUnit? {
        val declarations = mutableListOf<TopLevelDeclaration>()
        while (!isAtEnd()) {
            try {
                val decl = parseTopLevelDeclaration()
                if (decl != null) {
                    declarations.add(decl)
                } else {
                    advance() // recover
                }
            } catch (e: CParseException) {
                diagnostics.add(e.diagnostic)
                synchronizeTopLevel()
            }
        }
        return if (diagnostics.none { it.severity == DiagnosticSeverity.ERROR }) {
            TranslationUnit(declarations)
        } else {
            null
        }
    }

    private fun parseTopLevelDeclaration(): TopLevelDeclaration? {
        // Skip stray semicolons
        while (match(TokenType.SEMICOLON)) {}

        if (isAtEnd()) return null

        val startToken = peek()

        // Typedef
        if (match(TokenType.KEYWORD_TYPEDEF)) {
            return parseTypedefDeclaration(startToken)
        }

        // Struct
        if (check(TokenType.KEYWORD_STRUCT) && peekNext()?.type == TokenType.IDENTIFIER && peekThird()?.type == TokenType.LBRACE) {
            advance() // struct
            val nameToken = consume(TokenType.IDENTIFIER, "expected struct name")
            consume(TokenType.LBRACE, "expected '{' after struct name")
            val fields = parseStructFields()
            consume(TokenType.RBRACE, "expected '}' after struct body")
            consume(TokenType.SEMICOLON, "expected ';' after struct declaration")
            return StructDeclaration(nameToken.text, fields, startToken.line, startToken.column)
        }

        // Type specifiers (static, const, inline, etc.)
        var isStatic = false
        var isInline = false
        var isConst = false

        while (true) {
            if (match(TokenType.KEYWORD_STATIC)) isStatic = true
            else if (match(TokenType.KEYWORD_INLINE)) isInline = true
            else if (match(TokenType.KEYWORD_CONST)) isConst = true
            else break
        }

        val type = parseType() ?: throw error(peek(), "expected type specifier")

        val nameToken = consume(TokenType.IDENTIFIER, "expected identifier")

        // Function definition or declaration: name(...)
        if (match(TokenType.LPAREN)) {
            val params = parseParameterList()
            consume(TokenType.RPAREN, "expected ')' after parameter list")

            if (match(TokenType.SEMICOLON)) {
                // Function prototype (forward declaration) - ignore or record
                return null
            }

            if (check(TokenType.LBRACE)) {
                val body = parseCompoundStatement()
                return FunctionDefinition(
                    returnType = type,
                    name = nameToken.text,
                    params = params,
                    body = body,
                    isStatic = isStatic,
                    isInline = isInline,
                    line = startToken.line,
                    column = startToken.column
                )
            }
            throw error(peek(), "expected '{' or ';' after function declarator")
        }

        // Global variable declaration
        var arraySize: Int? = null
        if (match(TokenType.LBRACKET)) {
            if (match(TokenType.RBRACKET)) {
                arraySize = -1 // unsized array
            } else {
                val sizeToken = consume(TokenType.LITERAL_INT, "expected array size integer")
                consume(TokenType.RBRACKET, "expected ']' after array size")
                arraySize = sizeToken.intValue.toInt()
            }
        }

        var initializer: Expression? = null
        if (match(TokenType.EQUAL)) {
            initializer = parseExpression()
        }
        consume(TokenType.SEMICOLON, "expected ';' after declaration")

        return GlobalVarDeclaration(
            type = type,
            name = nameToken.text,
            arraySize = arraySize,
            initializer = initializer,
            isConst = isConst,
            line = startToken.line,
            column = startToken.column
        )
    }

    private fun parseTypedefDeclaration(startToken: Token): TypedefDeclaration {
        val originalType = parseType() ?: throw error(peek(), "expected type in typedef")
        val aliasToken = consume(TokenType.IDENTIFIER, "expected identifier for typedef alias")
        consume(TokenType.SEMICOLON, "expected ';' after typedef")
        typedefs[aliasToken.text] = originalType
        return TypedefDeclaration(originalType, aliasToken.text, startToken.line, startToken.column)
    }

    private fun parseStructFields(): List<ParamDeclaration> {
        val fields = mutableListOf<ParamDeclaration>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            val fieldType = parseType() ?: throw error(peek(), "expected field type in struct")
            val fieldName = consume(TokenType.IDENTIFIER, "expected field name")
            var finalType = fieldType
            if (match(TokenType.LBRACKET)) {
                val sizeToken = consume(TokenType.LITERAL_INT, "expected array size in struct field")
                consume(TokenType.RBRACKET, "expected ']'")
                finalType = CType.ArrayType(fieldType, sizeToken.intValue.toInt())
            }
            consume(TokenType.SEMICOLON, "expected ';' after struct field")
            fields.add(ParamDeclaration(finalType, fieldName.text, fieldName.line, fieldName.column))
        }
        return fields
    }

    private fun parseParameterList(): List<ParamDeclaration> {
        val params = mutableListOf<ParamDeclaration>()
        if (check(TokenType.RPAREN)) return params

        // Check for void param: (void)
        if (check(TokenType.KEYWORD_VOID) && peekNext()?.type == TokenType.RPAREN) {
            advance() // void
            return params
        }

        do {
            val type = parseType() ?: throw error(peek(), "expected parameter type")
            val name = if (check(TokenType.IDENTIFIER)) {
                advance().text
            } else {
                "__param_${params.size}"
            }
            var finalType = type
            if (match(TokenType.LBRACKET)) {
                consume(TokenType.RBRACKET, "expected ']' in array parameter")
                finalType = CType.PointerType(type)
            }
            params.add(ParamDeclaration(finalType, name, peek().line, peek().column))
        } while (match(TokenType.COMMA))

        return params
    }

    fun parseType(): CType? {
        var baseType: CType? = null
        if (match(TokenType.KEYWORD_INT)) baseType = CType.IntType
        else if (match(TokenType.KEYWORD_CHAR)) baseType = CType.CharType
        else if (match(TokenType.KEYWORD_FLOAT)) baseType = CType.FloatType
        else if (match(TokenType.KEYWORD_DOUBLE)) baseType = CType.DoubleType
        else if (match(TokenType.KEYWORD_VOID)) baseType = CType.VoidType
        else if (match(TokenType.KEYWORD_BOOL)) baseType = CType.BoolType
        else if (match(TokenType.KEYWORD_SHORT)) baseType = CType.IntType
        else if (match(TokenType.KEYWORD_LONG)) {
            match(TokenType.KEYWORD_LONG) // long long
            baseType = CType.IntType
        } else if (match(TokenType.KEYWORD_UNSIGNED)) {
            if (match(TokenType.KEYWORD_INT) || match(TokenType.KEYWORD_CHAR) || match(TokenType.KEYWORD_LONG) || match(TokenType.KEYWORD_SHORT)) {
                baseType = CType.IntType
            } else {
                baseType = CType.IntType
            }
        } else if (match(TokenType.KEYWORD_SIGNED)) {
            if (match(TokenType.KEYWORD_INT) || match(TokenType.KEYWORD_CHAR) || match(TokenType.KEYWORD_LONG)) {
                baseType = CType.IntType
            } else {
                baseType = CType.IntType
            }
        } else if (match(TokenType.KEYWORD_STRUCT)) {
            val structName = consume(TokenType.IDENTIFIER, "expected struct name")
            baseType = CType.StructType(structName.text)
        } else if (check(TokenType.IDENTIFIER) && typedefs.containsKey(peek().text)) {
            val name = advance().text
            baseType = typedefs[name] ?: CType.CustomType(name)
        }

        if (baseType == null) return null

        var currentType: CType = baseType
        // Handle pointers (e.g. char**, int*, void*)
        while (match(TokenType.STAR)) {
            currentType = CType.PointerType(currentType)
        }

        return currentType
    }

    private fun parseCompoundStatement(): CompoundStatement {
        val startToken = consume(TokenType.LBRACE, "expected '{'")
        val statements = mutableListOf<Statement>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            try {
                val stmt = parseStatement()
                if (stmt != null) {
                    statements.add(stmt)
                }
            } catch (e: CParseException) {
                diagnostics.add(e.diagnostic)
                synchronizeStatement()
            }
        }
        consume(TokenType.RBRACE, "expected '}' to close block")
        return CompoundStatement(statements, startToken.line, startToken.column)
    }

    private fun parseStatement(): Statement? {
        val token = peek()
        return when (token.type) {
            TokenType.LBRACE -> parseCompoundStatement()
            TokenType.KEYWORD_IF -> parseIfStatement()
            TokenType.KEYWORD_WHILE -> parseWhileStatement()
            TokenType.KEYWORD_DO -> parseDoWhileStatement()
            TokenType.KEYWORD_FOR -> parseForStatement()
            TokenType.KEYWORD_SWITCH -> parseSwitchStatement()
            TokenType.KEYWORD_CASE -> parseCaseStatement()
            TokenType.KEYWORD_DEFAULT -> parseDefaultStatement()
            TokenType.KEYWORD_RETURN -> parseReturnStatement()
            TokenType.KEYWORD_BREAK -> {
                advance()
                consume(TokenType.SEMICOLON, "expected ';' after 'break'")
                BreakStatement(token.line, token.column)
            }
            TokenType.KEYWORD_CONTINUE -> {
                advance()
                consume(TokenType.SEMICOLON, "expected ';' after 'continue'")
                ContinueStatement(token.line, token.column)
            }
            TokenType.SEMICOLON -> {
                advance()
                EmptyStatement(token.line, token.column)
            }
            else -> {
                // Check if variable declaration
                val savedPos = current
                val isConst = match(TokenType.KEYWORD_CONST)
                val type = parseType()
                if (type != null && (check(TokenType.IDENTIFIER) || check(TokenType.STAR))) {
                    val baseType: CType = type
                    val decls = mutableListOf<LocalVarDeclaration>()
                    do {
                        var varType: CType = baseType
                        while (match(TokenType.STAR)) {
                            varType = CType.PointerType(varType)
                        }
                        val name = consume(TokenType.IDENTIFIER, "expected variable name").text
                        var arraySize: Int? = null
                        if (match(TokenType.LBRACKET)) {
                            if (match(TokenType.RBRACKET)) {
                                arraySize = -1
                            } else {
                                val sizeExpr = parseExpression()
                                consume(TokenType.RBRACKET, "expected ']' after array size")
                                arraySize = if (sizeExpr is IntLiteral) sizeExpr.value.toInt() else 100
                            }
                        }
                        var init: Expression? = null
                        if (match(TokenType.EQUAL)) {
                            init = parseExpression()
                        }
                        decls.add(LocalVarDeclaration(varType, name, arraySize, init, isConst, token.line, token.column))
                    } while (match(TokenType.COMMA))

                    consume(TokenType.SEMICOLON, "expected ';' after variable declaration")
                    if (decls.size == 1) decls[0] else MultiVarDeclaration(decls, token.line, token.column)
                } else {
                    current = savedPos
                    val expr = parseExpression()
                    consume(TokenType.SEMICOLON, "expected ';' after expression")
                    ExpressionStatement(expr, token.line, token.column)
                }
            }
        }
    }

    private fun parseIfStatement(): IfStatement {
        val startToken = advance() // if
        consume(TokenType.LPAREN, "expected '(' after 'if'")
        val cond = parseExpression()
        consume(TokenType.RPAREN, "expected ')' after if condition")
        val thenBranch = parseStatement() ?: EmptyStatement(startToken.line, startToken.column)
        var elseBranch: Statement? = null
        if (match(TokenType.KEYWORD_ELSE)) {
            elseBranch = parseStatement()
        }
        return IfStatement(cond, thenBranch, elseBranch, startToken.line, startToken.column)
    }

    private fun parseWhileStatement(): WhileStatement {
        val startToken = advance() // while
        consume(TokenType.LPAREN, "expected '(' after 'while'")
        val cond = parseExpression()
        consume(TokenType.RPAREN, "expected ')' after while condition")
        val body = parseStatement() ?: EmptyStatement(startToken.line, startToken.column)
        return WhileStatement(cond, body, startToken.line, startToken.column)
    }

    private fun parseDoWhileStatement(): DoWhileStatement {
        val startToken = advance() // do
        val body = parseStatement() ?: EmptyStatement(startToken.line, startToken.column)
        consume(TokenType.KEYWORD_WHILE, "expected 'while' after do block")
        consume(TokenType.LPAREN, "expected '(' after 'while'")
        val cond = parseExpression()
        consume(TokenType.RPAREN, "expected ')' after while condition")
        consume(TokenType.SEMICOLON, "expected ';' after do-while")
        return DoWhileStatement(body, cond, startToken.line, startToken.column)
    }

    private fun parseForStatement(): ForStatement {
        val startToken = advance() // for
        consume(TokenType.LPAREN, "expected '(' after 'for'")

        val init: Statement? = if (match(TokenType.SEMICOLON)) {
            null
        } else {
            val type = parseType()
            if (type != null && check(TokenType.IDENTIFIER)) {
                val name = advance().text
                var initExpr: Expression? = null
                if (match(TokenType.EQUAL)) {
                    initExpr = parseExpression()
                }
                consume(TokenType.SEMICOLON, "expected ';' after for-loop init declaration")
                LocalVarDeclaration(type, name, null, initExpr, false, startToken.line, startToken.column)
            } else {
                val expr = parseExpression()
                consume(TokenType.SEMICOLON, "expected ';' after for-loop init expression")
                ExpressionStatement(expr, startToken.line, startToken.column)
            }
        }

        val cond: Expression? = if (check(TokenType.SEMICOLON)) {
            null
        } else {
            parseExpression()
        }
        consume(TokenType.SEMICOLON, "expected ';' after for-loop condition")

        val update: Expression? = if (check(TokenType.RPAREN)) {
            null
        } else {
            parseExpression()
        }
        consume(TokenType.RPAREN, "expected ')' after for-loop clauses")

        val body = parseStatement() ?: EmptyStatement(startToken.line, startToken.column)
        return ForStatement(init, cond, update, body, startToken.line, startToken.column)
    }

    private fun parseSwitchStatement(): SwitchStatement {
        val startToken = advance() // switch
        consume(TokenType.LPAREN, "expected '(' after 'switch'")
        val cond = parseExpression()
        consume(TokenType.RPAREN, "expected ')' after switch condition")
        val body = parseStatement() ?: EmptyStatement(startToken.line, startToken.column)
        return SwitchStatement(cond, body, startToken.line, startToken.column)
    }

    private fun parseCaseStatement(): CaseStatement {
        val startToken = advance() // case
        val value = parseExpression()
        consume(TokenType.COLON, "expected ':' after case value")
        val stmt = if (!check(TokenType.KEYWORD_CASE) && !check(TokenType.KEYWORD_DEFAULT) && !check(TokenType.RBRACE)) {
            parseStatement()
        } else null
        return CaseStatement(value, stmt, startToken.line, startToken.column)
    }

    private fun parseDefaultStatement(): DefaultStatement {
        val startToken = advance() // default
        consume(TokenType.COLON, "expected ':' after default")
        val stmt = if (!check(TokenType.KEYWORD_CASE) && !check(TokenType.KEYWORD_DEFAULT) && !check(TokenType.RBRACE)) {
            parseStatement()
        } else null
        return DefaultStatement(stmt, startToken.line, startToken.column)
    }

    private fun parseReturnStatement(): ReturnStatement {
        val startToken = advance() // return
        var value: Expression? = null
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression()
        }
        consume(TokenType.SEMICOLON, "expected ';' after return statement")
        return ReturnStatement(value, startToken.line, startToken.column)
    }

    // Expressions parsing with standard operator precedence
    fun parseExpression(): Expression {
        return parseAssignment()
    }

    private fun parseAssignment(): Expression {
        val expr = parseTernary()

        if (match(
                TokenType.EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
                TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL, TokenType.PERCENT_EQUAL,
                TokenType.AMPERSAND_EQUAL, TokenType.PIPE_EQUAL, TokenType.CARET_EQUAL,
                TokenType.SHL_EQUAL, TokenType.SHR_EQUAL
            )
        ) {
            val op = previous()
            val value = parseAssignment()
            return AssignmentExpr(expr, op.type, value, op.line, op.column)
        }

        return expr
    }

    private fun parseTernary(): Expression {
        var expr = parseLogicalOr()
        if (match(TokenType.QUESTION)) {
            val qToken = previous()
            val trueExpr = parseExpression()
            consume(TokenType.COLON, "expected ':' in ternary operator")
            val falseExpr = parseTernary()
            expr = TernaryExpr(expr, trueExpr, falseExpr, qToken.line, qToken.column)
        }
        return expr
    }

    private fun parseLogicalOr(): Expression {
        var expr = parseLogicalAnd()
        while (match(TokenType.LOGICAL_OR)) {
            val op = previous()
            val right = parseLogicalAnd()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseLogicalAnd(): Expression {
        var expr = parseBitwiseOr()
        while (match(TokenType.LOGICAL_AND)) {
            val op = previous()
            val right = parseBitwiseOr()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseBitwiseOr(): Expression {
        var expr = parseBitwiseXor()
        while (match(TokenType.PIPE)) {
            val op = previous()
            val right = parseBitwiseXor()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseBitwiseXor(): Expression {
        var expr = parseBitwiseAnd()
        while (match(TokenType.CARET)) {
            val op = previous()
            val right = parseBitwiseAnd()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseBitwiseAnd(): Expression {
        var expr = parseEquality()
        while (match(TokenType.AMPERSAND)) {
            val op = previous()
            val right = parseEquality()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseEquality(): Expression {
        var expr = parseRelational()
        while (match(TokenType.EQUAL_EQUAL, TokenType.NOT_EQUAL)) {
            val op = previous()
            val right = parseRelational()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseRelational(): Expression {
        var expr = parseShift()
        while (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL)) {
            val op = previous()
            val right = parseShift()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseShift(): Expression {
        var expr = parseAdditive()
        while (match(TokenType.SHL, TokenType.SHR)) {
            val op = previous()
            val right = parseAdditive()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseAdditive(): Expression {
        var expr = parseMultiplicative()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous()
            val right = parseMultiplicative()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseMultiplicative(): Expression {
        var expr = parseUnary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous()
            val right = parseUnary()
            expr = BinaryOpExpr(expr, op.type, right, op.line, op.column)
        }
        return expr
    }

    private fun parseUnary(): Expression {
        val token = peek()
        if (match(TokenType.MINUS, TokenType.PLUS, TokenType.LOGICAL_NOT, TokenType.TILDE, TokenType.STAR, TokenType.AMPERSAND, TokenType.PLUS_PLUS, TokenType.MINUS_MINUS)) {
            val op = previous()
            val right = parseUnary()
            return UnaryOpExpr(op.type, right, isPrefix = true, token.line, token.column)
        }

        // sizeof
        if (match(TokenType.KEYWORD_SIZEOF)) {
            val sToken = previous()
            if (match(TokenType.LPAREN)) {
                val saved = current
                val type = parseType()
                if (type != null && match(TokenType.RPAREN)) {
                    return SizeofExpr(targetType = type, line = sToken.line, column = sToken.column)
                }
                current = saved
                val expr = parseExpression()
                consume(TokenType.RPAREN, "expected ')' after sizeof expression")
                return SizeofExpr(targetExpr = expr, line = sToken.line, column = sToken.column)
            }
            val expr = parseUnary()
            return SizeofExpr(targetExpr = expr, line = sToken.line, column = sToken.column)
        }

        // Cast expression (type)expr
        if (check(TokenType.LPAREN)) {
            val saved = current
            advance() // (
            val type = parseType()
            if (type != null && match(TokenType.RPAREN)) {
                val expr = parseUnary()
                return CastExpr(type, expr, token.line, token.column)
            }
            current = saved
        }

        return parsePostfix()
    }

    private fun parsePostfix(): Expression {
        var expr = parsePrimary()

        while (true) {
            if (match(TokenType.PLUS_PLUS)) {
                val op = previous()
                expr = UnaryOpExpr(op.type, expr, isPrefix = false, op.line, op.column)
            } else if (match(TokenType.MINUS_MINUS)) {
                val op = previous()
                expr = UnaryOpExpr(op.type, expr, isPrefix = false, op.line, op.column)
            } else if (match(TokenType.LBRACKET)) {
                val index = parseExpression()
                val bracketToken = consume(TokenType.RBRACKET, "expected ']' after array index")
                expr = ArrayAccessExpr(expr, index, bracketToken.line, bracketToken.column)
            } else if (match(TokenType.DOT)) {
                val memberToken = consume(TokenType.IDENTIFIER, "expected member name after '.'")
                expr = MemberAccessExpr(expr, memberToken.text, isArrow = false, memberToken.line, memberToken.column)
            } else if (match(TokenType.ARROW)) {
                val memberToken = consume(TokenType.IDENTIFIER, "expected member name after '->'")
                expr = MemberAccessExpr(expr, memberToken.text, isArrow = true, memberToken.line, memberToken.column)
            } else if (match(TokenType.LPAREN)) {
                // Function call when expr is identifier
                if (expr is IdentifierExpr) {
                    val args = mutableListOf<Expression>()
                    if (!check(TokenType.RPAREN)) {
                        do {
                            args.add(parseExpression())
                        } while (match(TokenType.COMMA))
                    }
                    val parenToken = consume(TokenType.RPAREN, "expected ')' after function arguments")
                    expr = FunctionCallExpr(expr.name, args, expr.line, expr.column)
                } else {
                    break
                }
            } else {
                break
            }
        }

        return expr
    }

    private fun parsePrimary(): Expression {
        val token = peek()
        return when (token.type) {
            TokenType.LITERAL_INT -> {
                advance()
                IntLiteral(token.intValue, token.line, token.column)
            }
            TokenType.LITERAL_FLOAT -> {
                advance()
                FloatLiteral(token.floatValue, token.line, token.column)
            }
            TokenType.LITERAL_STRING -> {
                advance()
                StringLiteral(token.stringValue, token.line, token.column)
            }
            TokenType.LITERAL_CHAR -> {
                advance()
                CharLiteral(token.intValue.toInt().toChar(), token.line, token.column)
            }
            TokenType.IDENTIFIER -> {
                advance()
                IdentifierExpr(token.text, token.line, token.column)
            }
            TokenType.LPAREN -> {
                advance()
                val expr = parseExpression()
                consume(TokenType.RPAREN, "expected ')' after expression")
                expr
            }
            TokenType.LBRACE -> {
                // Array initializer { 1, 2, 3 }
                advance()
                val elements = mutableListOf<Expression>()
                if (!check(TokenType.RBRACE)) {
                    do {
                        elements.add(parseExpression())
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RBRACE, "expected '}' after initializer list")
                ArrayInitializerExpr(elements, token.line, token.column)
            }
            else -> throw error(token, "unexpected token '${token.text}'")
        }
    }

    // Helper functions
    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = if (current < tokens.size) tokens[current] else tokens.last()
    private fun peekNext(): Token? = if (current + 1 < tokens.size) tokens[current + 1] else null
    private fun peekThird(): Token? = if (current + 2 < tokens.size) tokens[current + 2] else null
    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun error(token: Token, message: String): CParseException {
        val srcLine = if (token.line in 1..sourceLines.size) sourceLines[token.line - 1] else null
        val diagnostic = Diagnostic(
            file = "main.c",
            line = token.line,
            column = token.column,
            severity = DiagnosticSeverity.ERROR,
            message = message,
            sourceLine = srcLine
        )
        return CParseException(diagnostic)
    }

    private fun synchronizeTopLevel() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON || previous().type == TokenType.RBRACE) return
            when (peek().type) {
                TokenType.KEYWORD_INT, TokenType.KEYWORD_CHAR, TokenType.KEYWORD_FLOAT,
                TokenType.KEYWORD_DOUBLE, TokenType.KEYWORD_VOID, TokenType.KEYWORD_STRUCT,
                TokenType.KEYWORD_TYPEDEF, TokenType.KEYWORD_STATIC -> return
                else -> advance()
            }
        }
    }

    private fun synchronizeStatement() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            when (peek().type) {
                TokenType.KEYWORD_IF, TokenType.KEYWORD_FOR, TokenType.KEYWORD_WHILE,
                TokenType.KEYWORD_DO, TokenType.KEYWORD_SWITCH, TokenType.KEYWORD_RETURN,
                TokenType.KEYWORD_BREAK, TokenType.KEYWORD_CONTINUE, TokenType.LBRACE -> return
                else -> advance()
            }
        }
    }
}
