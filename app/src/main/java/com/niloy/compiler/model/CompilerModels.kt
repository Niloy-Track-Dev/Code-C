package com.niloy.compiler.model

/**
 * Diagnostic severity level according to standard C compiler conventions.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    NOTE
}

/**
 * Parsed compiler diagnostic item.
 */
data class Diagnostic(
    val file: String = "main.c",
    val line: Int,
    val column: Int = 1,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val message: String,
    val sourceLine: String? = null
) {
    fun toFormattedString(): String {
        val sevStr = when (severity) {
            DiagnosticSeverity.ERROR -> "error"
            DiagnosticSeverity.WARNING -> "warning"
            DiagnosticSeverity.NOTE -> "note"
        }
        return "$file:$line:$column: $sevStr: $message"
    }
}

/**
 * Supported C language standards.
 */
enum class CStandard(val displayName: String, val flag: String) {
    C99("C99 (ISO/IEC 9899:1999)", "-std=c99"),
    C11("C11 (ISO/IEC 9899:2011)", "-std=c11"),
    C17("C17 (ISO/IEC 9899:2018)", "-std=c17")
}

/**
 * Compiler warning levels.
 */
enum class WarningLevel(val displayName: String, val flag: String) {
    DEFAULT("Default Warnings", ""),
    WALL("All Warnings (-Wall)", "-Wall"),
    WEXTRA("Extra Warnings (-Wextra)", "-Wextra")
}

/**
 * Optimization levels.
 */
enum class OptimizationLevel(val displayName: String, val flag: String) {
    O0("None (-O0)", "-O0"),
    O1("Basic (-O1)", "-O1"),
    O2("Standard (-O2)", "-O2"),
    O3("Aggressive (-O3)", "-O3")
}

/**
 * Configuration options for the local offline C compiler.
 */
data class CompilerConfig(
    val standard: CStandard = CStandard.C11,
    val warningLevel: WarningLevel = WarningLevel.WALL,
    val treatWarningsAsErrors: Boolean = false,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.O2,
    val timeoutMs: Long = 5000L,
    val maxOutputChars: Int = 65536,
    val autoScrollOutput: Boolean = true,
    val clearOutputBeforeRun: Boolean = true
)

/**
 * Outcome of the offline C compilation step.
 */
data class CompilationResult(
    val isSuccess: Boolean,
    val diagnostics: List<Diagnostic> = emptyList(),
    val rawOutput: String = "",
    val compilationTimeMs: Long = 0L,
    val executable: CompiledExecutable? = null
)

/**
 * Represents a compiled program ready for sandboxed local execution.
 */
interface CompiledExecutable {
    val programName: String
}

/**
 * Outcome of executing a compiled C program locally.
 */
data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTimeMs: Long,
    val isTimeout: Boolean = false,
    val isCancelled: Boolean = false,
    val memoryUsageBytes: Long = 0L
)
