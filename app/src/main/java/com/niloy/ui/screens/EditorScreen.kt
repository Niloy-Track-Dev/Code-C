package com.niloy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niloy.compiler.model.DiagnosticSeverity
import com.niloy.ui.components.*
import com.niloy.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val snippets by viewModel.snippets.collectAsState()

    var showSnippetSheet by remember { mutableStateOf(false) }
    var isConsoleExpanded by remember { mutableStateOf(true) }
    val snippetSheetState = rememberModalBottomSheetState()
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { viewModel.formatCode() }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Format")
                    }
                    IconButton(onClick = { showSnippetSheet = true }) {
                        Icon(Icons.Default.Code, contentDescription = "Snippets")
                    }
                    IconButton(onClick = { viewModel.toggleSearchReplace() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Project") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.saveProject()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState.isRunning) viewModel.stopExecution()
                    else viewModel.runCode()
                },
                icon = {
                    if (uiState.isCompiling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else if (uiState.isRunning) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                    }
                },
                text = {
                    Text(if (uiState.isCompiling) "Compiling..." else if (uiState.isRunning) "Stop" else "Run (C)")
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
            if (uiState.showSearchReplace) {
                SearchBarRow(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    replaceQuery = uiState.replaceQuery,
                    onReplaceQueryChange = { viewModel.updateReplaceQuery(it) },
                    matchCount = uiState.searchMatchCount,
                    onReplaceAll = { viewModel.replaceAll() },
                    onClose = { viewModel.toggleSearchReplace() }
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(if (isConsoleExpanded) 1f else 2f)) {
                CodeEditorView(
                    value = uiState.textFieldValue,
                    onValueChange = { viewModel.onCodeChanged(it) },
                    settings = settings,
                    modifier = Modifier.fillMaxSize()
                )
            }

            QuickSymbolBar(
                onInsertSymbol = { viewModel.insertSymbol(it) },
                onTab = { viewModel.insertSymbol(if (settings.useSpacesForTabs) " ".repeat(settings.tabSize) else "\t") },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onFormat = { viewModel.formatCode() }
            )

            if (!isConsoleExpanded) {
                Surface(
                    onClick = { isConsoleExpanded = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Console", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (errorCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("($errorCount errors)", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp)) {
                    ConsoleOutputView(
                        activeTab = uiState.activeTab,
                        onTabSelected = { viewModel.setActiveTab(it) },
                        consoleOutput = uiState.consoleOutput,
                        stdinInput = uiState.stdinInput,
                        onStdinChanged = { viewModel.updateStdin(it) },
                        diagnostics = uiState.diagnostics,
                        onJumpToDiagnostic = { viewModel.jumpToLine(it) },
                        stats = uiState.stats,
                        exitCode = uiState.exitCode,
                        executionTimeMs = uiState.executionTimeMs,
                        memoryBytes = uiState.memoryUsageBytes,
                        isRunning = uiState.isRunning,
                        onStopExecution = { viewModel.stopExecution() },
                        onClearConsole = { viewModel.clearConsole() },
                        onCloseConsole = { isConsoleExpanded = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showSnippetSheet) {
        SnippetBottomSheet(
            snippets = snippets,
            onSelectSnippet = { viewModel.insertSnippet(it) },
            onDismiss = { showSnippetSheet = false },
            sheetState = snippetSheetState
        )
    }
}
