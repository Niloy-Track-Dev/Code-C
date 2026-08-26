package com.example.compiler.diagnostics

import com.example.compiler.model.CompilerConfig
import com.example.compiler.model.Diagnostic
import com.example.compiler.model.DiagnosticSeverity
import com.example.compiler.model.WarningLevel
import com.example.compiler.parser.*

class CSemanticAnalyzer(
    private val ast: TranslationUnit,
    private val config: CompilerConfig,
    private val sourceCode: String = ""
) {
    val diagnostics = mutableListOf<Diagnostic>()
    private val sourceLines = sourceCode.lines()

    fun analyze(): List<Diagnostic> {
        val functionMap = mutableMapOf<String, FunctionDefinition>()
        val globalVars = mutableMapOf<String, GlobalVarDeclaration>()
        val structs = mutableMapOf<String, StructDeclaration>()

        // Collect top-level definitions
        for (decl in ast.declarations) {
            when (decl) {
                is FunctionDefinition -> {
                    if (functionMap.containsKey(decl.name)) {
                        addError(decl.line, decl.column, "redefinition of function '${decl.name}'")
                    } else {
                        functionMap[decl.name] = decl
                    }
                }
                is GlobalVarDeclaration -> {
                    if (globalVars.containsKey(decl.name)) {
                        addError(decl.line, decl.column, "redefinition of global variable '${decl.name}'")
                    } else {
                        globalVars[decl.name] = decl
                    }
                }
                is StructDeclaration -> {
                    structs[decl.name] = decl
                }
                is TypedefDeclaration -> {}
            }
        }

        // Check for main function
        val mainFunc = functionMap["main"]
        if (mainFunc == null) {
            addError(1, 1, "undefined reference to 'main'")
        }

        // Analyze function bodies
        for (func in functionMap.values) {
            val scope = mutableMapOf<String, CType>()
            val usedVars = mutableSetOf<String>()

            for (param in func.params) {
                scope[param.name] = param.type
            }

            analyzeCompoundStatement(func.body, scope, usedVars, functionMap, globalVars, structs, func.returnType)

            // Warning for unused variables if -Wall or -Wextra
            if (config.warningLevel == WarningLevel.WALL || config.warningLevel == WarningLevel.WEXTRA) {
                for (param in func.params) {
                    if (!usedVars.contains(param.name) && !param.name.startsWith("__")) {
                        addWarning(param.line, param.column, "unused parameter '${param.name}'")
                    }
                }
            }
        }

        return diagnostics
    }

    private fun analyzeCompoundStatement(
        compound: CompoundStatement,
        parentScope: Map<String, CType>,
        usedVars: MutableSet<String>,
        functions: Map<String, FunctionDefinition>,
        globals: Map<String, GlobalVarDeclaration>,
        structs: Map<String, StructDeclaration>,
        expectedReturnType: CType
    ) {
        val currentScope = parentScope.toMutableMap()
        val localDeclared = mutableSetOf<String>()

        for (stmt in compound.statements) {
            analyzeStatement(stmt, currentScope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
        }

        if (config.warningLevel == WarningLevel.WALL || config.warningLevel == WarningLevel.WEXTRA) {
            for (v in localDeclared) {
                if (!usedVars.contains(v)) {
                    val srcLine = compound.statements.filterIsInstance<LocalVarDeclaration>().firstOrNull { it.name == v }
                    if (srcLine != null) {
                        addWarning(srcLine.line, srcLine.column, "unused variable '$v'")
                    }
                }
            }
        }
    }

    private fun analyzeStatement(
        stmt: Statement,
        scope: MutableMap<String, CType>,
        localDeclared: MutableSet<String>,
        usedVars: MutableSet<String>,
        functions: Map<String, FunctionDefinition>,
        globals: Map<String, GlobalVarDeclaration>,
        structs: Map<String, StructDeclaration>,
        expectedReturnType: CType
    ) {
        when (stmt) {
            is LocalVarDeclaration -> {
                if (scope.containsKey(stmt.name) && localDeclared.contains(stmt.name)) {
                    addError(stmt.line, stmt.column, "redeclaration of '${stmt.name}' with no linkage")
                }
                scope[stmt.name] = stmt.type
                localDeclared.add(stmt.name)
                if (stmt.initializer != null) {
                    analyzeExpression(stmt.initializer, scope, usedVars, functions, globals, structs)
                }
            }
            is MultiVarDeclaration -> {
                for (decl in stmt.declarations) {
                    analyzeStatement(decl, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                }
            }
            is CompoundStatement -> {
                analyzeCompoundStatement(stmt, scope, usedVars, functions, globals, structs, expectedReturnType)
            }
            is IfStatement -> {
                analyzeExpression(stmt.condition, scope, usedVars, functions, globals, structs)
                analyzeStatement(stmt.thenBranch, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                if (stmt.elseBranch != null) {
                    analyzeStatement(stmt.elseBranch, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                }
            }
            is WhileStatement -> {
                analyzeExpression(stmt.condition, scope, usedVars, functions, globals, structs)
                analyzeStatement(stmt.body, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
            }
            is DoWhileStatement -> {
                analyzeStatement(stmt.body, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                analyzeExpression(stmt.condition, scope, usedVars, functions, globals, structs)
            }
            is ForStatement -> {
                if (stmt.init != null) {
                    analyzeStatement(stmt.init, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                }
                if (stmt.condition != null) {
                    analyzeExpression(stmt.condition, scope, usedVars, functions, globals, structs)
                }
                if (stmt.update != null) {
                    analyzeExpression(stmt.update, scope, usedVars, functions, globals, structs)
                }
                analyzeStatement(stmt.body, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
            }
            is SwitchStatement -> {
                analyzeExpression(stmt.condition, scope, usedVars, functions, globals, structs)
                analyzeStatement(stmt.body, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
            }
            is CaseStatement -> {
                analyzeExpression(stmt.value, scope, usedVars, functions, globals, structs)
                if (stmt.statement != null) {
                    analyzeStatement(stmt.statement, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                }
            }
            is DefaultStatement -> {
                if (stmt.statement != null) {
                    analyzeStatement(stmt.statement, scope, localDeclared, usedVars, functions, globals, structs, expectedReturnType)
                }
            }
            is ReturnStatement -> {
                if (stmt.value != null) {
                    analyzeExpression(stmt.value, scope, usedVars, functions, globals, structs)
                }
            }
            is ExpressionStatement -> {
                analyzeExpression(stmt.expression, scope, usedVars, functions, globals, structs)
            }
            is BreakStatement, is ContinueStatement, is EmptyStatement -> {}
        }
    }

    private fun analyzeExpression(
        expr: Expression,
        scope: Map<String, CType>,
        usedVars: MutableSet<String>,
        functions: Map<String, FunctionDefinition>,
        globals: Map<String, GlobalVarDeclaration>,
        structs: Map<String, StructDeclaration>
    ) {
        when (expr) {
            is IdentifierExpr -> {
                if (!scope.containsKey(expr.name) && !globals.containsKey(expr.name)) {
                    // Check standard C identifiers
                    val builtinIdentifiers = setOf("stdin", "stdout", "stderr", "NULL", "true", "false", "EOF")
                    if (!builtinIdentifiers.contains(expr.name)) {
                        addError(expr.line, expr.column, "use of undeclared identifier '${expr.name}'")
                    }
                } else {
                    usedVars.add(expr.name)
                }
            }
            is AssignmentExpr -> {
                analyzeExpression(expr.target, scope, usedVars, functions, globals, structs)
                analyzeExpression(expr.value, scope, usedVars, functions, globals, structs)
            }
            is BinaryOpExpr -> {
                analyzeExpression(expr.left, scope, usedVars, functions, globals, structs)
                analyzeExpression(expr.right, scope, usedVars, functions, globals, structs)
                // Division by zero check on constant
                if (expr.op == com.example.compiler.lexer.TokenType.SLASH || expr.op == com.example.compiler.lexer.TokenType.PERCENT) {
                    if (expr.right is IntLiteral && expr.right.value == 0L) {
                        addWarning(expr.line, expr.column, "division by zero is undefined")
                    }
                }
            }
            is UnaryOpExpr -> {
                analyzeExpression(expr.expr, scope, usedVars, functions, globals, structs)
            }
            is FunctionCallExpr -> {
                val stdlibFuncs = setOf(
                    "printf", "scanf", "puts", "gets", "getchar", "putchar", "sprintf", "sscanf",
                    "malloc", "free", "realloc", "calloc", "exit", "abs", "labs", "atoi", "atof", "atol",
                    "rand", "srand", "qsort", "bsearch", "strlen", "strcpy", "strncpy", "strcat", "strncat",
                    "strcmp", "strncmp", "strchr", "strstr", "memset", "memcpy", "memcmp", "sqrt", "pow",
                    "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "exp", "log", "log10", "ceil",
                    "floor", "fabs", "fmod", "round", "hypot", "isalpha", "isdigit", "isalnum", "isspace",
                    "isupper", "islower", "toupper", "tolower", "time", "clock", "difftime", "system",
                    "fopen", "fclose", "fread", "fwrite", "fgets", "fputs", "fgetc", "fputc", "feof", "fflush"
                )
                if (!functions.containsKey(expr.name) && !stdlibFuncs.contains(expr.name)) {
                    addWarning(expr.line, expr.column, "implicit declaration of function '${expr.name}'")
                }
                for (arg in expr.args) {
                    analyzeExpression(arg, scope, usedVars, functions, globals, structs)
                }
            }
            is ArrayAccessExpr -> {
                analyzeExpression(expr.array, scope, usedVars, functions, globals, structs)
                analyzeExpression(expr.index, scope, usedVars, functions, globals, structs)
            }
            is MemberAccessExpr -> {
                analyzeExpression(expr.obj, scope, usedVars, functions, globals, structs)
            }
            is TernaryExpr -> {
                analyzeExpression(expr.condition, scope, usedVars, functions, globals, structs)
                analyzeExpression(expr.trueExpr, scope, usedVars, functions, globals, structs)
                analyzeExpression(expr.falseExpr, scope, usedVars, functions, globals, structs)
            }
            is CastExpr -> {
                analyzeExpression(expr.expr, scope, usedVars, functions, globals, structs)
            }
            is SizeofExpr -> {
                if (expr.targetExpr != null) {
                    analyzeExpression(expr.targetExpr, scope, usedVars, functions, globals, structs)
                }
            }
            is ArrayInitializerExpr -> {
                for (elem in expr.elements) {
                    analyzeExpression(elem, scope, usedVars, functions, globals, structs)
                }
            }
            is IntLiteral, is FloatLiteral, is StringLiteral, is CharLiteral -> {}
        }
    }

    private fun addError(line: Int, column: Int, message: String) {
        val srcLine = if (line in 1..sourceLines.size) sourceLines[line - 1] else null
        diagnostics.add(
            Diagnostic(
                file = "main.c",
                line = line,
                column = column,
                severity = DiagnosticSeverity.ERROR,
                message = message,
                sourceLine = srcLine
            )
        )
    }

    private fun addWarning(line: Int, column: Int, message: String) {
        val srcLine = if (line in 1..sourceLines.size) sourceLines[line - 1] else null
        val severity = if (config.treatWarningsAsErrors) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
        diagnostics.add(
            Diagnostic(
                file = "main.c",
                line = line,
                column = column,
                severity = severity,
                message = message,
                sourceLine = srcLine
            )
        )
    }
}
