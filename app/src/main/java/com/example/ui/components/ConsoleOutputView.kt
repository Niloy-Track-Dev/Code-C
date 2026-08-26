package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.model.Diagnostic
import com.example.compiler.model.DiagnosticSeverity
import com.example.editor.CodeStatistics
import com.example.ui.viewmodel.EditorConsoleTab

@Composable
fun ConsoleOutputView(
    activeTab: EditorConsoleTab,
    onTabSelected: (EditorConsoleTab) -> Unit,
    consoleOutput: String,
    stdinInput: String,
    onStdinChanged: (String) -> Unit,
    diagnostics: List<Diagnostic>,
    onJumpToDiagnostic: (Int) -> Unit,
    stats: CodeStatistics,
    exitCode: Int?,
    executionTimeMs: Long,
    memoryBytes: Long,
    isRunning: Boolean,
    onStopExecution: () -> Unit,
    onClearConsole: () -> Unit,
    onCloseConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("console_output_panel"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar with Tab Navigation & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Selection Pills
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ConsoleTabPill(
                        title = "Output",
                        icon = Icons.Default.Terminal,
                        isSelected = activeTab == EditorConsoleTab.OUTPUT,
                        onClick = { onTabSelected(EditorConsoleTab.OUTPUT) },
                        testTag = "tab_output"
                    )

                    ConsoleTabPill(
                        title = "Stdin",
                        icon = Icons.Default.Keyboard,
                        isSelected = activeTab == EditorConsoleTab.INPUT,
                        onClick = { onTabSelected(EditorConsoleTab.INPUT) },
                        testTag = "tab_stdin"
                    )

                    val errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
                    val warnCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
                    ConsoleTabPill(
                        title = if (errorCount > 0) "Issues ($errorCount)" else if (warnCount > 0) "Issues ($warnCount)" else "Issues",
                        icon = if (errorCount > 0) Icons.Default.Cancel else Icons.Default.CheckCircle,
                        isSelected = activeTab == EditorConsoleTab.DIAGNOSTICS,
                        badgeColor = if (errorCount > 0) MaterialTheme.colorScheme.error else null,
                        onClick = { onTabSelected(EditorConsoleTab.DIAGNOSTICS) },
                        testTag = "tab_diagnostics"
                    )

                    ConsoleTabPill(
                        title = "Stats",
                        icon = Icons.Default.Analytics,
                        isSelected = activeTab == EditorConsoleTab.STATS,
                        onClick = { onTabSelected(EditorConsoleTab.STATS) },
                        testTag = "tab_stats"
                    )
                }

                // Execution Status & Actions
                if (isRunning) {
                    FilledTonalButton(
                        onClick = onStopExecution,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("btn_stop_execution")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(consoleOutput)) },
                    modifier = Modifier.size(32.dp).testTag("btn_copy_output")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Output", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onClearConsole,
                    modifier = Modifier.size(32.dp).testTag("btn_clear_console")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Output", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onCloseConsole,
                    modifier = Modifier.size(32.dp).testTag("btn_close_console")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize Console", modifier = Modifier.size(20.dp))
                }
            }

            // Tab Content Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F172A)) // Terminal Dark
                    .padding(8.dp)
            ) {
                when (activeTab) {
                    EditorConsoleTab.OUTPUT -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (exitCode != null || executionTimeMs > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (exitCode != null) {
                                        Surface(
                                            color = if (exitCode == 0) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Exit: $exitCode",
                                                color = if (exitCode == 0) Color(0xFF34D399) else Color(0xFFF87171),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (executionTimeMs > 0) {
                                        Text(
                                            text = "Time: ${executionTimeMs}ms",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    if (memoryBytes > 0) {
                                        Text(
                                            text = "Mem: ${memoryBytes / 1024} KB",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = consoleOutput.ifEmpty { "Ready. Press 'Run' (▶) to compile and execute offline." },
                                    color = if (consoleOutput.isEmpty()) Color(0xFF64748B) else Color(0xFFF8FAFC),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    EditorConsoleTab.INPUT -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Standard Input (stdin) - provide inputs for scanf/getchar:",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = stdinInput,
                                onValueChange = onStdinChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("stdin_input_field"),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = Color.White
                                ),
                                placeholder = {
                                    Text(
                                        "Enter input values separated by spaces or newlines (e.g. 'John 25')",
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B)
                                )
                            )
                        }
                    }
                    EditorConsoleTab.DIAGNOSTICS -> {
                        if (diagnostics.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No compiler issues found. Clean build!",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(diagnostics) { diag ->
                                    DiagnosticCard(diag = diag, onClick = { onJumpToDiagnostic(diag.line) })
                                }
                            }
                        }
                    }
                    EditorConsoleTab.STATS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Code Metrics & Analysis",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Total Lines", "${stats.totalLines}", Modifier.weight(1f))
                                StatCard("Code Lines", "${stats.codeLines}", Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Comments", "${stats.commentLines}", Modifier.weight(1f))
                                StatCard("Blank Lines", "${stats.blankLines}", Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Total Characters", "${stats.totalChars}", Modifier.weight(1f))
                                StatCard("Functions", "${stats.estimatedFunctions}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTabPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badgeColor: Color? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.height(28.dp).testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (badgeColor != null) badgeColor else if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiagnosticCard(diag: Diagnostic, onClick: () -> Unit) {
    val isError = diag.severity == DiagnosticSeverity.ERROR
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isError) Color(0xFF7F1D1D).copy(alpha = 0.4f) else Color(0xFF78350F).copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isError) Color(0xFFDC2626).copy(alpha = 0.6f) else Color(0xFFD97706).copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isError) Icons.Default.Error else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isError) Color(0xFFEF4444) else Color(0xFFFBBF24),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${diag.file}:${diag.line}:${diag.column}",
                    color = Color(0xFF93C5FD),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isError) "error" else "warning",
                    color = if (isError) Color(0xFFF87171) else Color(0xFFFCD34D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = diag.message,
                color = Color.White,
                fontSize = 12.sp
            )
            if (diag.sourceLine != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFF020617),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = " ${diag.line} | ${diag.sourceLine}",
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
