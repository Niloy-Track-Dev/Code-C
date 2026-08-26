package com.example.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Syntax Highlighting Color Palettes
data class SyntaxColorPalette(
    val keywordColor: Color,
    val typeColor: Color,
    val stringColor: Color,
    val numberColor: Color,
    val commentColor: Color,
    val preprocessorColor: Color,
    val symbolColor: Color,
    val functionColor: Color,
    val defaultTextColor: Color
)

object SyntaxPalettes {
    val Dark = SyntaxColorPalette(
        keywordColor = Color(0xFF569CD6),     // VS Code Royal Blue
        typeColor = Color(0xFF4EC9B0),        // Teal / Turquoise
        stringColor = Color(0xFFCE9178),      // Coral Orange/Green
        numberColor = Color(0xFFB5CEA8),      // Light Sage Green / Mint
        commentColor = Color(0xFF6A9955),     // Olive Green
        preprocessorColor = Color(0xFFC586C0),// Magenta / Purple
        symbolColor = Color(0xFFD4D4D4),      // Light Grey
        functionColor = Color(0xFFDCDCAA),    // Soft Yellow
        defaultTextColor = Color(0xFFE2E8F0)  // Slate Light
    )

    val Light = SyntaxColorPalette(
        keywordColor = Color(0xFF0000FF),     // Classic Blue
        typeColor = Color(0xFF267F99),        // Dark Teal
        stringColor = Color(0xFFA31515),      // Dark Red
        numberColor = Color(0xFF098658),      // Dark Green
        commentColor = Color(0xFF008000),     // Green
        preprocessorColor = Color(0xFF795E26),// Brown/Gold
        symbolColor = Color(0xFF1E293B),      // Dark Slate
        functionColor = Color(0xFF795E26),    // Ochre
        defaultTextColor = Color(0xFF0F172A)  // Dark
    )

    val Monokai = SyntaxColorPalette(
        keywordColor = Color(0xFFF92672),     // Pink/Red
        typeColor = Color(0xFF66D9EF),        // Cyan
        stringColor = Color(0xFFE6DB74),      // Yellow
        numberColor = Color(0xFFAE81FF),      // Purple
        commentColor = Color(0xFF75715E),     // Muted Brown-Grey
        preprocessorColor = Color(0xFFA6E22E),// Lime Green
        symbolColor = Color(0xFFF8F8F2),      // Off-White
        functionColor = Color(0xFFA6E22E),    // Lime Green
        defaultTextColor = Color(0xFFF8F8F2)
    )

    val SolarizedDark = SyntaxColorPalette(
        keywordColor = Color(0xFF859900),     // Green
        typeColor = Color(0xFF268BD2),        // Blue
        stringColor = Color(0xFF2AA198),      // Cyan
        numberColor = Color(0xFFD33682),      // Magenta
        commentColor = Color(0xFF586E75),     // Muted Blue-Grey
        preprocessorColor = Color(0xFFCB4B16),// Orange
        symbolColor = Color(0xFF839496),      // Base 0
        functionColor = Color(0xFFB58900),    // Yellow
        defaultTextColor = Color(0xFF93A1A1)
    )
}

class CSyntaxHighlighter(
    private val palette: SyntaxColorPalette = SyntaxPalettes.Dark,
    private val enabled: Boolean = true
) : VisualTransformation {

    companion object {
        private val KEYWORDS = setOf(
            "int", "char", "float", "double", "void", "short", "long", "signed", "unsigned",
            "struct", "union", "enum", "typedef", "if", "else", "switch", "case", "default",
            "for", "while", "do", "break", "continue", "return", "sizeof", "static", "extern",
            "const", "inline", "restrict", "volatile", "bool", "_Bool"
        )

        private val TYPES = setOf(
            "int", "char", "float", "double", "void", "short", "long", "bool", "size_t",
            "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "FILE", "time_t", "clock_t", "uintptr_t", "intptr_t"
        )

        private val PREPROCESSOR_DIRECTIVES = setOf(
            "#include", "#define", "#undef", "#ifdef", "#ifndef", "#if", "#else", "#elif", "#endif",
            "#pragma", "#error", "#warning", "#line"
        )
    }

    override fun filter(text: AnnotatedString): TransformedText {
        if (!enabled) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val raw = text.text
        val builder = AnnotatedString.Builder(raw)
        var i = 0

        while (i < raw.length) {
            val c = raw[i]

            // Single line comment //
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                val start = i
                while (i < raw.length && raw[i] != '\n') {
                    i++
                }
                builder.addStyle(
                    SpanStyle(color = palette.commentColor, fontStyle = FontStyle.Italic),
                    start,
                    i
                )
                continue
            }

            // Multi-line comment /* */
            if (c == '/' && i + 1 < raw.length && raw[i + 1] == '*') {
                val start = i
                i += 2
                while (i < raw.length && !(raw[i] == '*' && i + 1 < raw.length && raw[i + 1] == '/')) {
                    i++
                }
                if (i < raw.length) i += 2
                builder.addStyle(
                    SpanStyle(color = palette.commentColor, fontStyle = FontStyle.Italic),
                    start,
                    minOf(i, raw.length)
                )
                continue
            }

            // Preprocessor directive #...
            if (c == '#') {
                val start = i
                while (i < raw.length && raw[i].isLetterOrDigit() || raw[i] == '_' || raw[i] == '#') {
                    i++
                }
                val directive = raw.substring(start, i)
                if (PREPROCESSOR_DIRECTIVES.contains(directive) || directive.startsWith("#")) {
                    builder.addStyle(
                        SpanStyle(color = palette.preprocessorColor, fontWeight = FontWeight.Bold),
                        start,
                        i
                    )
                }
                continue
            }

            // String literals "..."
            if (c == '"') {
                val start = i
                i++
                while (i < raw.length && raw[i] != '"') {
                    if (raw[i] == '\\' && i + 1 < raw.length) {
                        i++
                    }
                    i++
                }
                if (i < raw.length) i++ // Include closing quote
                builder.addStyle(
                    SpanStyle(color = palette.stringColor),
                    start,
                    minOf(i, raw.length)
                )
                continue
            }

            // Char literals '...'
            if (c == '\'') {
                val start = i
                i++
                while (i < raw.length && raw[i] != '\'') {
                    if (raw[i] == '\\' && i + 1 < raw.length) {
                        i++
                    }
                    i++
                }
                if (i < raw.length) i++
                builder.addStyle(
                    SpanStyle(color = palette.stringColor),
                    start,
                    minOf(i, raw.length)
                )
                continue
            }

            // Numbers: decimal, hex, float
            if (c.isDigit()) {
                val start = i
                while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '.')) {
                    i++
                }
                builder.addStyle(
                    SpanStyle(color = palette.numberColor),
                    start,
                    i
                )
                continue
            }

            // Words (Identifiers, Keywords, Types, Function Calls)
            if (c.isLetter() || c == '_') {
                val start = i
                while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '_')) {
                    i++
                }
                val word = raw.substring(start, i)

                // Check if followed by '(' -> function call
                var j = i
                while (j < raw.length && raw[j].isWhitespace()) j++
                val isFunc = j < raw.length && raw[j] == '('

                when {
                    TYPES.contains(word) -> {
                        builder.addStyle(
                            SpanStyle(color = palette.typeColor, fontWeight = FontWeight.SemiBold),
                            start,
                            i
                        )
                    }
                    KEYWORDS.contains(word) -> {
                        builder.addStyle(
                            SpanStyle(color = palette.keywordColor, fontWeight = FontWeight.Bold),
                            start,
                            i
                        )
                    }
                    isFunc -> {
                        builder.addStyle(
                            SpanStyle(color = palette.functionColor),
                            start,
                            i
                        )
                    }
                    else -> {
                        builder.addStyle(
                            SpanStyle(color = palette.defaultTextColor),
                            start,
                            i
                        )
                    }
                }
                continue
            }

            // Symbols & Operators
            if (!c.isWhitespace()) {
                builder.addStyle(
                    SpanStyle(color = palette.symbolColor),
                    i,
                    i + 1
                )
            }
            i++
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
