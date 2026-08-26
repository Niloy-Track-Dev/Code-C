package com.example.compiler.preprocessor

import com.example.compiler.model.Diagnostic
import com.example.compiler.model.DiagnosticSeverity

data class PreprocessorResult(
    val processedSource: String,
    val lineMapping: Map<Int, Int>, // generated line -> original source line
    val diagnostics: List<Diagnostic> = emptyList(),
    val includedHeaders: Set<String> = emptySet()
)

object CPreprocessor {

    // Standard C library header declarations and definitions
    val SUPPORTED_HEADERS = setOf(
        "stdio.h",
        "stdlib.h",
        "string.h",
        "math.h",
        "stdbool.h",
        "stdint.h",
        "ctype.h",
        "time.h",
        "assert.h",
        "limits.h",
        "float.h"
    )

    private val STANDARD_MACROS = mapOf(
        "NULL" to "0",
        "true" to "1",
        "false" to "0",
        "EOF" to "-1",
        "SEEK_SET" to "0",
        "SEEK_CUR" to "1",
        "SEEK_END" to "2",
        "EXIT_SUCCESS" to "0",
        "EXIT_FAILURE" to "1",
        "RAND_MAX" to "32767",
        "INT_MAX" to "2147483647",
        "INT_MIN" to "-2147483648",
        "CHAR_BIT" to "8",
        "CLOCKS_PER_SEC" to "1000000"
    )

    fun process(source: String): PreprocessorResult {
        val originalLines = source.lines()
        val processedLines = mutableListOf<String>()
        val lineMapping = mutableMapOf<Int, Int>()
        val diagnostics = mutableListOf<Diagnostic>()
        val includedHeaders = mutableSetOf<String>()
        val userMacros = mutableMapOf<String, String>()
        userMacros.putAll(STANDARD_MACROS)

        val ifStack = mutableListOf<Boolean>() // true if current block is active

        for ((index, line) in originalLines.withIndex()) {
            val originalLineNum = index + 1
            val trimmed = line.trim()

            // Conditional compilation directives
            if (trimmed.startsWith("#ifdef")) {
                val macro = trimmed.removePrefix("#ifdef").trim()
                val isDefined = userMacros.containsKey(macro)
                val parentActive = ifStack.lastOrNull() ?: true
                ifStack.add(parentActive && isDefined)
                continue
            } else if (trimmed.startsWith("#ifndef")) {
                val macro = trimmed.removePrefix("#ifndef").trim()
                val isDefined = userMacros.containsKey(macro)
                val parentActive = ifStack.lastOrNull() ?: true
                ifStack.add(parentActive && !isDefined)
                continue
            } else if (trimmed.startsWith("#if")) {
                val expr = trimmed.removePrefix("#if").trim()
                val condition = expr == "1" || (userMacros[expr]?.toIntOrNull() ?: 0) != 0
                val parentActive = ifStack.lastOrNull() ?: true
                ifStack.add(parentActive && condition)
                continue
            } else if (trimmed.startsWith("#else")) {
                if (ifStack.isNotEmpty()) {
                    val current = ifStack.removeAt(ifStack.lastIndex)
                    val parentActive = ifStack.lastOrNull() ?: true
                    ifStack.add(parentActive && !current)
                }
                continue
            } else if (trimmed.startsWith("#elif")) {
                if (ifStack.isNotEmpty()) {
                    ifStack.removeAt(ifStack.lastIndex)
                    val expr = trimmed.removePrefix("#elif").trim()
                    val condition = expr == "1" || (userMacros[expr]?.toIntOrNull() ?: 0) != 0
                    val parentActive = ifStack.lastOrNull() ?: true
                    ifStack.add(parentActive && condition)
                }
                continue
            } else if (trimmed.startsWith("#endif")) {
                if (ifStack.isNotEmpty()) {
                    ifStack.removeAt(ifStack.lastIndex)
                }
                continue
            }

            // If inside inactive #ifdef/#if block, skip
            if (ifStack.isNotEmpty() && !ifStack.last()) {
                continue
            }

            // #include
            if (trimmed.startsWith("#include")) {
                val match = Regex("""#include\s*[<"]([^>"]+)[>"]""").find(trimmed)
                if (match != null) {
                    val header = match.groupValues[1].trim()
                    if (SUPPORTED_HEADERS.contains(header)) {
                        includedHeaders.add(header)
                    } else {
                        diagnostics.add(
                            Diagnostic(
                                line = originalLineNum,
                                column = 1,
                                severity = DiagnosticSeverity.WARNING,
                                message = "Header '$header' is not in bundled standard library headers. Compilation will proceed using standard C primitives.",
                                sourceLine = line
                            )
                        )
                    }
                }
                // Don't emit #include line into final C code, but keep line count alignment with empty comment
                val currentGeneratedLine = processedLines.size + 1
                processedLines.add("// include $trimmed")
                lineMapping[currentGeneratedLine] = originalLineNum
                continue
            }

            // #define
            if (trimmed.startsWith("#define")) {
                val rest = trimmed.removePrefix("#define").trim()
                val parts = rest.split(Regex("""\s+"""), limit = 2)
                if (parts.isNotEmpty()) {
                    val name = parts[0]
                    val value = if (parts.size > 1) parts[1] else "1"
                    userMacros[name] = value
                }
                val currentGeneratedLine = processedLines.size + 1
                processedLines.add("// define $trimmed")
                lineMapping[currentGeneratedLine] = originalLineNum
                continue
            }

            // #undef
            if (trimmed.startsWith("#undef")) {
                val name = trimmed.removePrefix("#undef").trim()
                userMacros.remove(name)
                val currentGeneratedLine = processedLines.size + 1
                processedLines.add("// undef $name")
                lineMapping[currentGeneratedLine] = originalLineNum
                continue
            }

            // #pragma
            if (trimmed.startsWith("#pragma")) {
                val currentGeneratedLine = processedLines.size + 1
                processedLines.add("// $trimmed")
                lineMapping[currentGeneratedLine] = originalLineNum
                continue
            }

            // Regular line: replace known user macros if simple word boundaries
            var processedLine = line
            for ((macroName, macroVal) in userMacros) {
                if (macroName.isNotEmpty() && processedLine.contains(macroName)) {
                    // Replace whole words only
                    val regex = Regex("""\b${Regex.escape(macroName)}\b""")
                    processedLine = regex.replace(processedLine, macroVal)
                }
            }

            val currentGeneratedLine = processedLines.size + 1
            processedLines.add(processedLine)
            lineMapping[currentGeneratedLine] = originalLineNum
        }

        return PreprocessorResult(
            processedSource = processedLines.joinToString("\n"),
            lineMapping = lineMapping,
            diagnostics = diagnostics,
            includedHeaders = includedHeaders
        )
    }
}
