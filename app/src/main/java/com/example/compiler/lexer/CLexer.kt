package com.example.compiler.lexer

enum class TokenType {
    // Keywords
    KEYWORD_INT, KEYWORD_CHAR, KEYWORD_FLOAT, KEYWORD_DOUBLE, KEYWORD_VOID,
    KEYWORD_SHORT, KEYWORD_LONG, KEYWORD_SIGNED, KEYWORD_UNSIGNED,
    KEYWORD_STRUCT, KEYWORD_UNION, KEYWORD_ENUM, KEYWORD_TYPEDEF,
    KEYWORD_IF, KEYWORD_ELSE, KEYWORD_SWITCH, KEYWORD_CASE, KEYWORD_DEFAULT,
    KEYWORD_FOR, KEYWORD_WHILE, KEYWORD_DO, KEYWORD_BREAK, KEYWORD_CONTINUE, KEYWORD_RETURN,
    KEYWORD_SIZEOF, KEYWORD_STATIC, KEYWORD_EXTERN, KEYWORD_CONST, KEYWORD_INLINE,
    KEYWORD_RESTRICT, KEYWORD_VOLATILE, KEYWORD_BOOL,

    // Literals
    LITERAL_INT, LITERAL_FLOAT, LITERAL_STRING, LITERAL_CHAR,

    // Identifiers
    IDENTIFIER,

    // Operators & Symbols
    PLUS, MINUS, STAR, SLASH, PERCENT,
    PLUS_PLUS, MINUS_MINUS,
    EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL,
    EQUAL_EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
    LOGICAL_AND, LOGICAL_OR, LOGICAL_NOT,
    AMPERSAND, PIPE, CARET, TILDE, SHL, SHR,
    AMPERSAND_EQUAL, PIPE_EQUAL, CARET_EQUAL, SHL_EQUAL, SHR_EQUAL,
    QUESTION, COLON, ARROW, DOT, COMMA, SEMICOLON,
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,

    EOF, INVALID
}

data class Token(
    val type: TokenType,
    val text: String,
    val line: Int,
    val column: Int,
    val intValue: Long = 0,
    val floatValue: Double = 0.0,
    val stringValue: String = ""
)

class CLexer(private val source: String, private val lineMapping: Map<Int, Int> = emptyMap()) {
    private var index = 0
    private var currentLine = 1
    private var currentColumn = 1

    private val keywords = mapOf(
        "int" to TokenType.KEYWORD_INT,
        "char" to TokenType.KEYWORD_CHAR,
        "float" to TokenType.KEYWORD_FLOAT,
        "double" to TokenType.KEYWORD_DOUBLE,
        "void" to TokenType.KEYWORD_VOID,
        "short" to TokenType.KEYWORD_SHORT,
        "long" to TokenType.KEYWORD_LONG,
        "signed" to TokenType.KEYWORD_SIGNED,
        "unsigned" to TokenType.KEYWORD_UNSIGNED,
        "struct" to TokenType.KEYWORD_STRUCT,
        "union" to TokenType.KEYWORD_UNION,
        "enum" to TokenType.KEYWORD_ENUM,
        "typedef" to TokenType.KEYWORD_TYPEDEF,
        "if" to TokenType.KEYWORD_IF,
        "else" to TokenType.KEYWORD_ELSE,
        "switch" to TokenType.KEYWORD_SWITCH,
        "case" to TokenType.KEYWORD_CASE,
        "default" to TokenType.KEYWORD_DEFAULT,
        "for" to TokenType.KEYWORD_FOR,
        "while" to TokenType.KEYWORD_WHILE,
        "do" to TokenType.KEYWORD_DO,
        "break" to TokenType.KEYWORD_BREAK,
        "continue" to TokenType.KEYWORD_CONTINUE,
        "return" to TokenType.KEYWORD_RETURN,
        "sizeof" to TokenType.KEYWORD_SIZEOF,
        "static" to TokenType.KEYWORD_STATIC,
        "extern" to TokenType.KEYWORD_EXTERN,
        "const" to TokenType.KEYWORD_CONST,
        "inline" to TokenType.KEYWORD_INLINE,
        "restrict" to TokenType.KEYWORD_RESTRICT,
        "volatile" to TokenType.KEYWORD_VOLATILE,
        "bool" to TokenType.KEYWORD_BOOL,
        "_Bool" to TokenType.KEYWORD_BOOL,
        "size_t" to TokenType.KEYWORD_LONG,
        "int8_t" to TokenType.KEYWORD_CHAR,
        "int16_t" to TokenType.KEYWORD_SHORT,
        "int32_t" to TokenType.KEYWORD_INT,
        "int64_t" to TokenType.KEYWORD_LONG,
        "uint8_t" to TokenType.KEYWORD_CHAR,
        "uint16_t" to TokenType.KEYWORD_SHORT,
        "uint32_t" to TokenType.KEYWORD_INT,
        "uint64_t" to TokenType.KEYWORD_LONG
    )

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (index < source.length) {
            val c = source[index]

            // Whitespace
            if (c.isWhitespace()) {
                advance()
                continue
            }

            // Single line comment
            if (c == '/' && peek() == '/') {
                advance() // /
                advance() // /
                while (index < source.length && source[index] != '\n') {
                    advance()
                }
                continue
            }

            // Multi-line comment
            if (c == '/' && peek() == '*') {
                advance() // /
                advance() // *
                while (index < source.length && !(source[index] == '*' && peek() == '/')) {
                    advance()
                }
                if (index < source.length) {
                    advance() // *
                    advance() // /
                }
                continue
            }

            val startLine = getMappedLine(currentLine)
            val startCol = currentColumn

            // Identifiers / Keywords
            if (c.isLetter() || c == '_') {
                val startIdx = index
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
                    advance()
                }
                val word = source.substring(startIdx, index)
                val type = keywords[word] ?: TokenType.IDENTIFIER
                tokens.add(Token(type, word, startLine, startCol))
                continue
            }

            // Numbers
            if (c.isDigit()) {
                tokens.add(readNumber(startLine, startCol))
                continue
            }

            // String literals
            if (c == '"') {
                tokens.add(readString(startLine, startCol))
                continue
            }

            // Character literals
            if (c == '\'') {
                tokens.add(readChar(startLine, startCol))
                continue
            }

            // Multi-character & single-character operators
            tokens.add(readOperator(startLine, startCol))
        }

        tokens.add(Token(TokenType.EOF, "", getMappedLine(currentLine), currentColumn))
        return tokens
    }

    private fun readNumber(line: Int, col: Int): Token {
        val startIdx = index
        var isFloat = false

        if (source[index] == '0' && (peek() == 'x' || peek() == 'X')) {
            // Hexadecimal
            advance()
            advance()
            while (index < source.length && (source[index].isDigit() || source[index] in 'a'..'f' || source[index] in 'A'..'F')) {
                advance()
            }
            val text = source.substring(startIdx, index)
            val hexVal = text.substring(2).toLongOrNull(16) ?: 0L
            return Token(TokenType.LITERAL_INT, text, line, col, intValue = hexVal)
        }

        while (index < source.length && (source[index].isDigit() || source[index] == '.' || source[index] == 'e' || source[index] == 'E' || source[index] == 'f' || source[index] == 'F' || source[index] == 'u' || source[index] == 'U' || source[index] == 'l' || source[index] == 'L')) {
            if (source[index] == '.' || source[index] == 'e' || source[index] == 'E' || source[index] == 'f' || source[index] == 'F') {
                isFloat = true
            }
            advance()
        }

        val text = source.substring(startIdx, index)
        val cleanText = text.trimEnd('f', 'F', 'u', 'U', 'l', 'L')
        return if (isFloat) {
            val dVal = cleanText.toDoubleOrNull() ?: 0.0
            Token(TokenType.LITERAL_FLOAT, text, line, col, floatValue = dVal)
        } else {
            val iVal = cleanText.toLongOrNull() ?: 0L
            Token(TokenType.LITERAL_INT, text, line, col, intValue = iVal)
        }
    }

    private fun readString(line: Int, col: Int): Token {
        advance() // Opening quote
        val sb = StringBuilder()
        val startIdx = index - 1
        while (index < source.length && source[index] != '"') {
            if (source[index] == '\\' && index + 1 < source.length) {
                advance()
                when (source[index]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '0' -> sb.append('\u0000')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    'a' -> sb.append('\u0007')
                    'b' -> sb.append('\b')
                    else -> sb.append(source[index])
                }
            } else {
                sb.append(source[index])
            }
            advance()
        }
        if (index < source.length && source[index] == '"') {
            advance() // Closing quote
        }
        val fullText = source.substring(startIdx, index)
        return Token(TokenType.LITERAL_STRING, fullText, line, col, stringValue = sb.toString())
    }

    private fun readChar(line: Int, col: Int): Token {
        advance() // Opening quote
        var charVal = '\u0000'
        val startIdx = index - 1
        if (index < source.length && source[index] != '\'') {
            if (source[index] == '\\' && index + 1 < source.length) {
                advance()
                charVal = when (source[index]) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    '0' -> '\u0000'
                    '\\' -> '\\'
                    '"' -> '"'
                    '\'' -> '\''
                    else -> source[index]
                }
            } else {
                charVal = source[index]
            }
            advance()
        }
        if (index < source.length && source[index] == '\'') {
            advance() // Closing quote
        }
        val fullText = source.substring(startIdx, index)
        return Token(TokenType.LITERAL_CHAR, fullText, line, col, intValue = charVal.code.toLong())
    }

    private fun readOperator(line: Int, col: Int): Token {
        val c = source[index]
        val p = peek()

        val token = when {
            c == '+' && p == '+' -> { advance(); advance(); Token(TokenType.PLUS_PLUS, "++", line, col) }
            c == '-' && p == '-' -> { advance(); advance(); Token(TokenType.MINUS_MINUS, "--", line, col) }
            c == '-' && p == '>' -> { advance(); advance(); Token(TokenType.ARROW, "->", line, col) }
            c == '+' && p == '=' -> { advance(); advance(); Token(TokenType.PLUS_EQUAL, "+=", line, col) }
            c == '-' && p == '=' -> { advance(); advance(); Token(TokenType.MINUS_EQUAL, "-=", line, col) }
            c == '*' && p == '=' -> { advance(); advance(); Token(TokenType.STAR_EQUAL, "*=", line, col) }
            c == '/' && p == '=' -> { advance(); advance(); Token(TokenType.SLASH_EQUAL, "/=", line, col) }
            c == '%' && p == '=' -> { advance(); advance(); Token(TokenType.PERCENT_EQUAL, "%=", line, col) }
            c == '=' && p == '=' -> { advance(); advance(); Token(TokenType.EQUAL_EQUAL, "==", line, col) }
            c == '!' && p == '=' -> { advance(); advance(); Token(TokenType.NOT_EQUAL, "!=", line, col) }
            c == '<' && p == '<' -> { advance(); advance(); Token(TokenType.SHL, "<<", line, col) }
            c == '>' && p == '>' -> { advance(); advance(); Token(TokenType.SHR, ">>", line, col) }
            c == '<' && p == '=' -> { advance(); advance(); Token(TokenType.LESS_EQUAL, "<=", line, col) }
            c == '>' && p == '=' -> { advance(); advance(); Token(TokenType.GREATER_EQUAL, ">=", line, col) }
            c == '&' && p == '&' -> { advance(); advance(); Token(TokenType.LOGICAL_AND, "&&", line, col) }
            c == '|' && p == '|' -> { advance(); advance(); Token(TokenType.LOGICAL_OR, "||", line, col) }
            c == '+' -> { advance(); Token(TokenType.PLUS, "+", line, col) }
            c == '-' -> { advance(); Token(TokenType.MINUS, "-", line, col) }
            c == '*' -> { advance(); Token(TokenType.STAR, "*", line, col) }
            c == '/' -> { advance(); Token(TokenType.SLASH, "/", line, col) }
            c == '%' -> { advance(); Token(TokenType.PERCENT, "%", line, col) }
            c == '=' -> { advance(); Token(TokenType.EQUAL, "=", line, col) }
            c == '<' -> { advance(); Token(TokenType.LESS, "<", line, col) }
            c == '>' -> { advance(); Token(TokenType.GREATER, ">", line, col) }
            c == '!' -> { advance(); Token(TokenType.LOGICAL_NOT, "!", line, col) }
            c == '&' -> { advance(); Token(TokenType.AMPERSAND, "&", line, col) }
            c == '|' -> { advance(); Token(TokenType.PIPE, "|", line, col) }
            c == '^' -> { advance(); Token(TokenType.CARET, "^", line, col) }
            c == '~' -> { advance(); Token(TokenType.TILDE, "~", line, col) }
            c == '?' -> { advance(); Token(TokenType.QUESTION, "?", line, col) }
            c == ':' -> { advance(); Token(TokenType.COLON, ":", line, col) }
            c == '.' -> { advance(); Token(TokenType.DOT, ".", line, col) }
            c == ',' -> { advance(); Token(TokenType.COMMA, ",", line, col) }
            c == ';' -> { advance(); Token(TokenType.SEMICOLON, ";", line, col) }
            c == '(' -> { advance(); Token(TokenType.LPAREN, "(", line, col) }
            c == ')' -> { advance(); Token(TokenType.RPAREN, ")", line, col) }
            c == '[' -> { advance(); Token(TokenType.LBRACKET, "[", line, col) }
            c == ']' -> { advance(); Token(TokenType.RBRACKET, "]", line, col) }
            c == '{' -> { advance(); Token(TokenType.LBRACE, "{", line, col) }
            c == '}' -> { advance(); Token(TokenType.RBRACE, "}", line, col) }
            else -> {
                val invalidChar = source[index].toString()
                advance()
                Token(TokenType.INVALID, invalidChar, line, col)
            }
        }
        return token
    }

    private fun peek(): Char = if (index + 1 < source.length) source[index + 1] else '\u0000'

    private fun advance() {
        if (index < source.length) {
            if (source[index] == '\n') {
                currentLine++
                currentColumn = 1
            } else {
                currentColumn++
            }
            index++
        }
    }

    private fun getMappedLine(generatedLine: Int): Int {
        return lineMapping[generatedLine] ?: generatedLine
    }
}
