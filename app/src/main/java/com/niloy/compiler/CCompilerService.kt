package com.niloy.compiler

import com.niloy.compiler.diagnostics.CSemanticAnalyzer
import com.niloy.compiler.lexer.CLexer
import com.niloy.compiler.model.*
import com.niloy.compiler.parser.CParser
import com.niloy.compiler.preprocessor.CPreprocessor
import com.niloy.compiler.runtime.CExecutable
import com.niloy.compiler.runtime.CRuntimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CCompilerService(
    private var config: CompilerConfig = CompilerConfig()
) {
    private var activeRuntimeEngine: CRuntimeEngine? = null

    fun updateConfig(newConfig: CompilerConfig) {
        this.config = newConfig
    }

    suspend fun compile(sourceCode: String, fileName: String = "main.c"): CompilationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (sourceCode.isBlank()) {
            return@withContext CompilationResult(
                isSuccess = false,
                diagnostics = listOf(
                    Diagnostic(
                        file = fileName,
                        line = 1,
                        column = 1,
                        severity = DiagnosticSeverity.ERROR,
                        message = "Source file is empty."
                    )
                ),
                rawOutput = "$fileName:1:1: error: Source file is empty.",
                compilationTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 1. Preprocessing stage
        val preprocessorResult = CPreprocessor.process(sourceCode)
        val diagnostics = mutableListOf<Diagnostic>()
        diagnostics.addAll(preprocessorResult.diagnostics)

        // 2. Lexical analysis stage
        val lexer = CLexer(preprocessorResult.processedSource, preprocessorResult.lineMapping)
        val tokens = lexer.tokenize()

        // 3. Syntax analysis & AST generation stage
        val parser = CParser(tokens, sourceCode)
        val ast = parser.parse()
        diagnostics.addAll(parser.diagnostics)

        if (ast == null || diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            val errorOutput = diagnostics.joinToString("\n") { it.toFormattedString() }
            return@withContext CompilationResult(
                isSuccess = false,
                diagnostics = diagnostics,
                rawOutput = errorOutput,
                compilationTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 4. Semantic analysis & Diagnostics stage
        val analyzer = CSemanticAnalyzer(ast, config, sourceCode)
        val semanticDiagnostics = analyzer.analyze()
        diagnostics.addAll(semanticDiagnostics)

        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        val outputStr = if (diagnostics.isEmpty()) {
            "Compilation successful. Generated binary executable."
        } else {
            diagnostics.joinToString("\n") { it.toFormattedString() }
        }

        val executable = if (!hasErrors) {
            CExecutable(
                programName = fileName.removeSuffix(".c"),
                ast = ast
            )
        } else null

        return@withContext CompilationResult(
            isSuccess = !hasErrors,
            diagnostics = diagnostics,
            rawOutput = outputStr,
            compilationTimeMs = System.currentTimeMillis() - startTime,
            executable = executable
        )
    }

    suspend fun execute(
        executable: CompiledExecutable,
        stdinInput: String = ""
    ): ExecutionResult = withContext(Dispatchers.Default) {
        val cExec = executable as? CExecutable
            ?: return@withContext ExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = "Invalid executable format.",
                executionTimeMs = 0L
            )

        val engine = CRuntimeEngine(config)
        activeRuntimeEngine = engine
        try {
            engine.execute(cExec, stdinInput)
        } finally {
            activeRuntimeEngine = null
        }
    }

    fun stopExecution() {
        activeRuntimeEngine?.cancel()
    }
}
