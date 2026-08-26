package com.example.editor

data class CodeStatistics(
    val totalLines: Int = 0,
    val codeLines: Int = 0,
    val commentLines: Int = 0,
    val blankLines: Int = 0,
    val totalChars: Int = 0,
    val totalWords: Int = 0,
    val estimatedFunctions: Int = 0
) {
    companion object {
        fun calculate(code: String): CodeStatistics {
            val lines = code.lines()
            var codeLines = 0
            var commentLines = 0
            var blankLines = 0
            var inMultiLineComment = false

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    blankLines++
                    continue
                }

                if (inMultiLineComment) {
                    commentLines++
                    if (trimmed.contains("*/")) {
                        inMultiLineComment = false
                    }
                    continue
                }

                if (trimmed.startsWith("/*")) {
                    commentLines++
                    if (!trimmed.contains("*/")) {
                        inMultiLineComment = true
                    }
                    continue
                }

                if (trimmed.startsWith("//")) {
                    commentLines++
                    continue
                }

                codeLines++
            }

            val words = code.split(Regex("\\s+")).count { it.isNotBlank() }
            val funcCount = Regex("""\b(int|void|char|float|double|long|short)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\([^)]*\)\s*\{""").findAll(code).count()

            return CodeStatistics(
                totalLines = lines.size,
                codeLines = codeLines,
                commentLines = commentLines,
                blankLines = blankLines,
                totalChars = code.length,
                totalWords = words,
                estimatedFunctions = maxOf(1, funcCount)
            )
        }
    }
}
