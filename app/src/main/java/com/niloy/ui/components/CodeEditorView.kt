package com.niloy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niloy.data.preferences.AppThemeSetting
import com.niloy.data.preferences.UserSettings
import com.niloy.editor.CSyntaxHighlighter
import com.niloy.editor.SyntaxPalettes

@Composable
fun CodeEditorView(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    settings: UserSettings,
    modifier: Modifier = Modifier
) {
    val lines = remember(value.text) { value.text.lines() }
    val lineCount = maxOf(1, lines.size)

    val currentLineIndex = remember(value.selection, value.text) {
        val cursor = value.selection.start
        if (cursor <= 0) 0
        else {
            val textBefore = value.text.substring(0, minOf(cursor, value.text.length))
            textBefore.count { it == '\n' }
        }
    }

    val palette = remember(settings.theme) {
        when (settings.theme) {
            AppThemeSetting.LIGHT_MINIMAL -> SyntaxPalettes.Light
            AppThemeSetting.MONOKAI -> SyntaxPalettes.Monokai
            AppThemeSetting.SOLARIZED_DARK -> SyntaxPalettes.SolarizedDark
            else -> SyntaxPalettes.Dark
        }
    }

    val syntaxHighlighter = remember(palette, settings.syntaxHighlighting) {
        CSyntaxHighlighter(palette, settings.syntaxHighlighting)
    }

    val editorBgColor = remember(settings.theme) {
        when (settings.theme) {
            AppThemeSetting.LIGHT_MINIMAL -> Color(0xFFF8FAFC)
            AppThemeSetting.MONOKAI -> Color(0xFF272822)
            AppThemeSetting.SOLARIZED_DARK -> Color(0xFF002B36)
            else -> Color(0xFF1E1E2E) 
        }
    }

    val gutterBgColor = remember(settings.theme) {
        when (settings.theme) {
            AppThemeSetting.LIGHT_MINIMAL -> Color(0xFFF1F5F9)
            AppThemeSetting.MONOKAI -> Color(0xFF1E1F1C)
            AppThemeSetting.SOLARIZED_DARK -> Color(0xFF073642)
            else -> Color(0xFF181825)
        }
    }

    val gutterTextColor = remember(settings.theme) {
        when (settings.theme) {
            AppThemeSetting.LIGHT_MINIMAL -> Color(0xFF94A3B8)
            AppThemeSetting.MONOKAI -> Color(0xFF75715E)
            AppThemeSetting.SOLARIZED_DARK -> Color(0xFF586E75)
            else -> Color(0xFF6C7086)
        }
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(editorBgColor)
            .testTag("code_editor_container")
    ) {
        // Line Numbers Gutter
        if (settings.showLineNumbers) {
            Column(
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .fillMaxHeight()
                    .background(gutterBgColor)
                    .verticalScroll(verticalScrollState)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                for (i in 1..lineCount) {
                    val isCurrentLine = (i - 1) == currentLineIndex && settings.highlightCurrentLine
                    Text(
                        text = "$i",
                        fontSize = settings.fontSizeSp.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isCurrentLine) MaterialTheme.colorScheme.primary else gutterTextColor,
                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        lineHeight = (settings.fontSizeSp * 1.4f).sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Code Input Field
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                .verticalScroll(verticalScrollState)
                .then(
                    if (!settings.wordWrap) Modifier.horizontalScroll(horizontalScrollState)
                    else Modifier
                )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 400.dp)
                    .testTag("code_text_field"),
                textStyle = TextStyle(
                    color = palette.defaultTextColor,
                    fontSize = settings.fontSizeSp.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (settings.fontSizeSp * 1.4f).sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = syntaxHighlighter
            )
        }
    }
}
