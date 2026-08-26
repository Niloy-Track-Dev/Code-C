package com.niloy.editor

object CCodeFormatter {

    fun format(code: String, tabSize: Int = 4, useSpaces: Boolean = true): String {
        val indentUnit = if (useSpaces) " ".repeat(tabSize) else "\t"
        val lines = code.lines()
        val formattedLines = mutableListOf<String>()
        var indentLevel = 0

        for (rawLine in lines) {
            val line = rawLine.trim()

            if (line.isEmpty()) {
                formattedLines.add("")
                continue
            }

            // If line starts with closing brace, decrease indent before printing
            if (line.startsWith("}") || line.startsWith(")") || line.startsWith("]")) {
                indentLevel = maxOf(0, indentLevel - 1)
            } else if (line.startsWith("case ") || line.startsWith("default:")) {
                val tempIndent = maxOf(0, indentLevel - 1)
                formattedLines.add(indentUnit.repeat(tempIndent) + line)
                continue
            }

            // Preprocessor directives stay unindented
            if (line.startsWith("#")) {
                formattedLines.add(line)
                continue
            }

            val currentIndent = indentUnit.repeat(indentLevel)
            formattedLines.add(currentIndent + line)

            // Adjust indent for subsequent lines
            val openBraces = countOccurrences(line, '{')
            val closeBraces = countOccurrences(line, '}')

            if (!line.startsWith("}")) {
                indentLevel = maxOf(0, indentLevel + (openBraces - closeBraces))
            } else {
                // Already decremented at start, so only add remaining open braces
                indentLevel = maxOf(0, indentLevel + openBraces)
            }
        }

        return formattedLines.joinToString("\n")
    }

    private fun countOccurrences(s: String, ch: Char): Int {
        var count = 0
        var inString = false
        var inChar = false
        for (i in s.indices) {
            val c = s[i]
            if (c == '"' && (i == 0 || s[i - 1] != '\\')) inString = !inString
            if (c == '\'' && (i == 0 || s[i - 1] != '\\')) inChar = !inChar
            if (!inString && !inChar && c == ch) {
                count++
            }
        }
        return count
    }
}
