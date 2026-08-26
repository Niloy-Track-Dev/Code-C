package com.niloy.compiler.diagnostics

import com.niloy.compiler.model.Diagnostic
import com.niloy.compiler.model.DiagnosticSeverity

object DiagnosticParser {

    // Regex for standard compiler diagnostic lines:
    // e.g. "main.c:5:10: error: expected ';' after expression"
    // or "main.c:12: warning: unused variable 'x'"
    private val DIAGNOSTIC_REGEX = Regex(
        """^(?:(?:\./|[A-Za-z0-9_\-/\\]+[/\\])?([^:]+)):(\d+)(?::(\d+))?:\s*(error|fatal error|warning|note):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses raw compiler diagnostic output into a list of structured [Diagnostic] objects.
     */
    fun parseDiagnostics(rawOutput: String, sourceCode: String = ""): List<Diagnostic> {
        val lines = rawOutput.lines()
        val sourceLines = if (sourceCode.isNotEmpty()) sourceCode.lines() else emptyList()
        val diagnostics = mutableListOf<Diagnostic>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val match = DIAGNOSTIC_REGEX.find(trimmed)
            if (match != null) {
                val file = match.groupValues[1]
                val lineNum = match.groupValues[2].toIntOrNull() ?: 1
                val colNum = match.groupValues[3].toIntOrNull() ?: 1
                val severityStr = match.groupValues[4].lowercase()
                val message = match.groupValues[5].trim()

                val severity = when {
                    severityStr.contains("fatal") || severityStr == "error" -> DiagnosticSeverity.ERROR
                    severityStr == "warning" -> DiagnosticSeverity.WARNING
                    else -> DiagnosticSeverity.NOTE
                }

                val sourceLine = if (lineNum in 1..sourceLines.size) {
                    sourceLines[lineNum - 1]
                } else null

                diagnostics.add(
                    Diagnostic(
                        file = file,
                        line = lineNum,
                        column = colNum,
                        severity = severity,
                        message = message,
                        sourceLine = sourceLine
                    )
                )
            } else if (trimmed.contains("error:", ignoreCase = true)) {
                // Fallback for unstructured error line
                diagnostics.add(
                    Diagnostic(
                        file = "main.c",
                        line = 1,
                        column = 1,
                        severity = DiagnosticSeverity.ERROR,
                        message = trimmed
                    )
                )
            }
        }

        return diagnostics
    }

    /**
     * Formats diagnostics into human-readable summary.
     */
    fun formatSummary(diagnostics: List<Diagnostic>): String {
        val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
        val warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
        val noteCount = diagnostics.count { it.severity == DiagnosticSeverity.NOTE }

        val parts = mutableListOf<String>()
        if (errorCount > 0) parts.add("$errorCount ${if (errorCount == 1) "Error" else "Errors"}")
        if (warningCount > 0) parts.add("$warningCount ${if (warningCount == 1) "Warning" else "Warnings"}")
        if (noteCount > 0) parts.add("$noteCount ${if (noteCount == 1) "Note" else "Notes"}")

        return if (parts.isEmpty()) "No issues found" else parts.joinToString(", ")
    }
}
