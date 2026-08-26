package com.niloy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niloy.compiler.model.CStandard
import com.niloy.compiler.model.OptimizationLevel
import com.niloy.compiler.model.WarningLevel
import com.niloy.data.preferences.AppThemeSetting
import com.niloy.data.preferences.UserSettings
import com.niloy.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_settings")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Code Editor Settings
            Text(
                text = "Editor Appearance & Behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Font Size Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Font Size", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${settings.fontSizeSp} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = settings.fontSizeSp.toFloat(),
                            onValueChange = { viewModel.updateFontSize(it.toInt()) },
                            valueRange = 12f..24f,
                            steps = 11,
                            modifier = Modifier.testTag("slider_font_size")
                        )
                    }

                    HorizontalDivider()

                    // Tab Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Tab Indent Width", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Number of spaces per tab", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2, 4, 8).forEach { size ->
                                FilterChip(
                                    selected = settings.tabSize == size,
                                    onClick = { viewModel.updateTabSize(size) },
                                    label = { Text("$size") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Line numbers toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Line Numbers", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = settings.showLineNumbers,
                            onCheckedChange = { viewModel.updateLineNumbers(it) },
                            modifier = Modifier.testTag("switch_line_numbers")
                        )
                    }

                    HorizontalDivider()

                    // Auto-close brackets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Auto-Close Brackets & Quotes", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically inserts '}', ')', ']', '\"'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoCloseBrackets,
                            onCheckedChange = { viewModel.updateAutoCloseBrackets(it) },
                            modifier = Modifier.testTag("switch_auto_brackets")
                        )
                    }

                    HorizontalDivider()

                    // Word wrap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Soft Word Wrap", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = settings.wordWrap,
                            onCheckedChange = { viewModel.updateWordWrap(it) },
                            modifier = Modifier.testTag("switch_word_wrap")
                        )
                    }
                }
            }

            // Section 2: Compiler & Sandbox Settings
            Text(
                text = "C Compiler & Sandbox Flags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // C Standard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("C Standard Version", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Language dialect rules", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CStandard.values().forEach { std ->
                                FilterChip(
                                    selected = settings.standard == std,
                                    onClick = { viewModel.updateStandard(std) },
                                    label = { Text(std.name) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Warning Level
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Warning Level", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Diagnostic strictness", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(WarningLevel.DEFAULT, WarningLevel.WALL, WarningLevel.WEXTRA).forEach { lvl ->
                                FilterChip(
                                    selected = settings.warningLevel == lvl,
                                    onClick = { viewModel.updateWarningLevel(lvl) },
                                    label = { Text(lvl.flag.ifEmpty { "Def" }) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Treat Warnings as Errors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Treat Warnings as Errors (-Werror)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Fails build on compiler warnings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.treatWarningsAsErrors,
                            onCheckedChange = { viewModel.updateTreatWarningsAsErrors(it) },
                            modifier = Modifier.testTag("switch_werror")
                        )
                    }

                    HorizontalDivider()

                    // Execution Timeout
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Execution Timeout Limit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${settings.executionTimeoutMs / 1000}s", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = (settings.executionTimeoutMs / 1000).toFloat(),
                            onValueChange = { viewModel.updateTimeoutMs((it * 1000).toLong()) },
                            valueRange = 1f..15f,
                            steps = 13,
                            modifier = Modifier.testTag("slider_timeout")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
