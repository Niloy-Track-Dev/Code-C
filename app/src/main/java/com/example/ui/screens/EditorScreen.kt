package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.model.DiagnosticSeverity
import com.example.data.entity.SnippetEntity
import com.example.data.preferences.UserSettings
import com.example.ui.components.*
import com.example.ui.viewmodel.EditorConsoleTab
import com.example.ui.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uiState: EditorUiState,
    settings: UserSettings,
    snippets: List<SnippetEntity>,
    onCodeChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onSave: () -> Unit,
    onCompile: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onInsertSymbol: (String) -> Unit,
    onInsertSnippet: (SnippetEntity) -> Unit,
    onTabSelected: (EditorConsoleTab) -> Unit,
    onStdinChanged: (String) -> Unit,
    onClearConsole: () -> Unit,
    onJumpToLine: (Int) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onReplaceAll: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    var showSnippetSheet by remember { mutableStateOf(false) }
    var isConsoleExpanded by remember { mutableStateOf(true) }
    val snippetSheetState = rememberModalBottomSheetState()
    var showOverflowMenu by remember { mutableStateOf(false) }

    val errorCount = remember(uiState.diagnostics) {
        uiState.diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.currentProject?.name ?: "main.c",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.isDirty) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp)
                            ) {}
                        }
                    }
                },
                actions = {
                    // Undo & Redo
                    IconButton(
                        onClick = onUndo,
                        modifier = Modifier.testTag("btn_action_undo")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = onRedo,
                        modifier = Modifier.testTag("btn_action_redo")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }

                    // Format code
                    IconButton(
                        onClick = onFormat,
                        modifier = Modifier.testTag("btn_action_format")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Format code")
                    }

                    // Snippets
                    IconButton(
                        onClick = { showSnippetSheet = true },
                        modifier = Modifier.testTag("btn_action_snippets")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Code snippets")
                    }

                    // Search
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.testTag("btn_action_search")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Find and replace")
                    }

                    // Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("btn_action_overflow")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Projects") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToProjects()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("C Templates") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToTemplates()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Compiler & Editor Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("About 100% Offline C") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToAbout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState.isRunning) {
                        onStop()
                    } else {
                        onRun()
                    }
                },
                icon = {
                    if (uiState.isCompiling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (uiState.isRunning) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                    }
                },
                text = {
                    Text(
                        text = if (uiState.isCompiling) "Compiling..." else if (uiState.isRunning) "Stop" else "Run (C)",
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = if (uiState.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (uiState.isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_run_c")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Search & Replace Bar
            if (uiState.showSearchReplace) {
                SearchBarRow(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    replaceQuery = uiState.replaceQuery,
                    onReplaceQueryChange = onReplaceQueryChange,
                    matchCount = uiState.searchMatchCount,
                    onReplaceAll = onReplaceAll,
                    onClose = onToggleSearch
                )
            }

            // Main Editor Section (weights upper portion)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isConsoleExpanded) 1f else 2f)
            ) {
                CodeEditorView(
                    value = uiState.textFieldValue,
                    onValueChange = onCodeChange,
                    settings = settings,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Quick Access Symbol Bar
            QuickSymbolBar(
                onInsertSymbol = onInsertSymbol,
                onTab = { onInsertSymbol(if (settings.useSpacesForTabs) " ".repeat(settings.tabSize) else "\t") },
                onUndo = onUndo,
                onRedo = onRedo,
                onFormat = onFormat
            )

            // Console output toggle bar if minimized
            if (!isConsoleExpanded) {
                Surface(
                    onClick = { isConsoleExpanded = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("btn_expand_console")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Console & Output", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (errorCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("($errorCount errors)", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                // Expanded Console Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                ) {
                    ConsoleOutputView(
                        activeTab = uiState.activeTab,
                        onTabSelected = onTabSelected,
                        consoleOutput = uiState.consoleOutput,
                        stdinInput = uiState.stdinInput,
                        onStdinChanged = onStdinChanged,
                        diagnostics = uiState.diagnostics,
                        onJumpToDiagnostic = onJumpToLine,
                        stats = uiState.stats,
                        exitCode = uiState.exitCode,
                        executionTimeMs = uiState.executionTimeMs,
                        memoryBytes = uiState.memoryUsageBytes,
                        isRunning = uiState.isRunning,
                        onStopExecution = onStop,
                        onClearConsole = onClearConsole,
                        onCloseConsole = { isConsoleExpanded = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Snippets Bottom Sheet
    if (showSnippetSheet) {
        SnippetBottomSheet(
            snippets = snippets,
            onSelectSnippet = { snippet ->
                onInsertSnippet(snippet)
                showSnippetSheet = false
            },
            onDismiss = { showSnippetSheet = false },
            sheetState = snippetSheetState
        )
    }
}
