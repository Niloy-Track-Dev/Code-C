package com.niloy.ui.viewmodel

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niloy.compiler.CCompilerService
import com.niloy.compiler.model.CompilationResult
import com.niloy.compiler.model.CompiledExecutable
import com.niloy.compiler.model.Diagnostic
import com.niloy.compiler.model.ExecutionResult
import com.niloy.data.database.AppDatabase
import com.niloy.data.entity.ProjectEntity
import com.niloy.data.entity.SnippetEntity
import com.niloy.data.preferences.EditorPreferences
import com.niloy.data.preferences.UserSettings
import com.niloy.data.repository.ProjectRepository
import com.niloy.data.storage.ProjectFileManager
import com.niloy.editor.CCodeFormatter
import com.niloy.editor.CodeStatistics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditorUiState(
    val currentProject: ProjectEntity? = null,
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val isDirty: Boolean = false,
    val isCompiling: Boolean = false,
    val isRunning: Boolean = false,
    val lastCompilationResult: CompilationResult? = null,
    val lastExecutionResult: ExecutionResult? = null,
    val diagnostics: List<Diagnostic> = emptyList(),
    val consoleOutput: String = "",
    val stdinInput: String = "",
    val executionTimeMs: Long = 0L,
    val memoryUsageBytes: Long = 0L,
    val exitCode: Int? = null,
    val showSearchReplace: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val searchMatchCount: Int = 0,
    val currentMatchIndex: Int = 0,
    val activeTab: EditorConsoleTab = EditorConsoleTab.OUTPUT,
    val stats: CodeStatistics = CodeStatistics()
)

enum class EditorConsoleTab {
    OUTPUT,
    INPUT,
    DIAGNOSTICS,
    STATS
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val fileManager = ProjectFileManager(application)
    private val repository = ProjectRepository(db.projectDao(), db.snippetDao(), fileManager)
    private val preferences = EditorPreferences(application)
    private val compilerService = CCompilerService()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val settings: StateFlow<UserSettings> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val snippets: StateFlow<List<SnippetEntity>> = repository.allSnippets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var lastCompiledExecutable: CompiledExecutable? = null
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
        }

        viewModelScope.launch {
            settings.collect { userSettings ->
                compilerService.updateConfig(userSettings.toCompilerConfig())
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val project = repository.getProject(projectId)
            if (project != null) {
                val code = repository.loadProjectSource(projectId)
                undoStack.clear()
                redoStack.clear()
                undoStack.add(code)
                _uiState.update {
                    it.copy(
                        currentProject = project,
                        textFieldValue = TextFieldValue(code),
                        isDirty = false,
                        stats = CodeStatistics.calculate(code)
                    )
                }
            }
        }
    }

    fun loadDefaultOrNewProject() {
        viewModelScope.launch {
            val draft = repository.getDraft()
            if (draft != null) {
                val (projId, code) = draft
                val proj = repository.getProject(projId)
                if (proj != null) {
                    _uiState.update {
                        it.copy(
                            currentProject = proj,
                            textFieldValue = TextFieldValue(code),
                            isDirty = true,
                            stats = CodeStatistics.calculate(code)
                        )
                    }
                    return@launch
                }
            }

            val projects = repository.allProjects.first()
            if (projects.isNotEmpty()) {
                loadProject(projects.first().id)
            } else {
                val newProj = repository.createProject("main.c", "hello_world")
                loadProject(newProj.id)
            }
        }
    }

    fun onCodeChanged(newValue: TextFieldValue) {
        val oldText = _uiState.value.textFieldValue.text
        if (oldText != newValue.text) {
            if (undoStack.isEmpty() || undoStack.last() != oldText) {
                undoStack.add(oldText)
                if (undoStack.size > 100) undoStack.removeAt(0)
            }
            redoStack.clear()
        }

        val updatedValue = handleAutoBrackets(newValue, oldText)
        val stats = CodeStatistics.calculate(updatedValue.text)

        _uiState.update {
            it.copy(
                textFieldValue = updatedValue,
                isDirty = true,
                stats = stats
            )
        }

        scheduleAutosave()
    }

    private fun handleAutoBrackets(newVal: TextFieldValue, oldText: String): TextFieldValue {
        if (!settings.value.autoCloseBrackets) return newVal
        val newText = newVal.text
        val cursor = newVal.selection.start

        if (newText.length == oldText.length + 1 && cursor > 0 && cursor <= newText.length) {
            val typedChar = newText[cursor - 1]
            val closingChar = when (typedChar) {
                '{' -> '}'
                '(' -> ')'
                '[' -> ']'
                '"' -> '"'
                '\'' -> '\''
                else -> null
            }
            if (closingChar != null) {
                val inserted = StringBuilder(newText).insert(cursor, closingChar).toString()
                return newVal.copy(text = inserted, selection = TextRange(cursor))
            }
        }
        return newVal
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1500) // 1.5s debounced autosave
            saveProject()
        }
    }

    fun saveProject() {
        val proj = _uiState.value.currentProject ?: return
        val code = _uiState.value.textFieldValue.text
        viewModelScope.launch {
            repository.saveProjectSource(proj.id, code)
            _uiState.update { it.copy(isDirty = false) }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentCode = _uiState.value.textFieldValue.text
            redoStack.add(currentCode)
            val prevCode = undoStack.removeAt(undoStack.lastIndex)
            _uiState.update {
                it.copy(
                    textFieldValue = TextFieldValue(prevCode, selection = TextRange(prevCode.length)),
                    isDirty = true,
                    stats = CodeStatistics.calculate(prevCode)
                )
            }
            scheduleAutosave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentCode = _uiState.value.textFieldValue.text
            undoStack.add(currentCode)
            val nextCode = redoStack.removeAt(redoStack.lastIndex)
            _uiState.update {
                it.copy(
                    textFieldValue = TextFieldValue(nextCode, selection = TextRange(nextCode.length)),
                    isDirty = true,
                    stats = CodeStatistics.calculate(nextCode)
                )
            }
            scheduleAutosave()
        }
    }

    fun insertTextAtCursor(textToInsert: String) {
        val curVal = _uiState.value.textFieldValue
        val start = curVal.selection.min
        val end = curVal.selection.max
        val newText = curVal.text.replaceRange(start, end, textToInsert)
        val newCursor = start + textToInsert.length
        onCodeChanged(TextFieldValue(newText, selection = TextRange(newCursor)))
    }

    fun formatCode() {
        val curCode = _uiState.value.textFieldValue.text
        val formatted = CCodeFormatter.format(
            curCode,
            tabSize = settings.value.tabSize,
            useSpaces = settings.value.useSpacesForTabs
        )
        if (formatted != curCode) {
            onCodeChanged(TextFieldValue(formatted, selection = _uiState.value.textFieldValue.selection))
        }
    }

    fun compileCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCompiling = true) }
            val code = _uiState.value.textFieldValue.text
            val projName = _uiState.value.currentProject?.name ?: "main.c"
            val result = compilerService.compile(code, projName)

            lastCompiledExecutable = result.executable

            _uiState.update {
                it.copy(
                    isCompiling = false,
                    lastCompilationResult = result,
                    diagnostics = result.diagnostics,
                    consoleOutput = if (result.isSuccess) "Build successful (${result.compilationTimeMs}ms)\n" else result.rawOutput,
                    activeTab = if (result.isSuccess) EditorConsoleTab.OUTPUT else EditorConsoleTab.DIAGNOSTICS
                )
            }
        }
    }

    fun runCode() {
        viewModelScope.launch {
            if (_uiState.value.isRunning) return@launch

            val code = _uiState.value.textFieldValue.text
            val projName = _uiState.value.currentProject?.name ?: "main.c"

            _uiState.update {
                it.copy(
                    isCompiling = true,
                    consoleOutput = if (settings.value.clearOutputBeforeRun) "Compiling...\n" else it.consoleOutput + "\nCompiling...\n"
                )
            }

            val compResult = compilerService.compile(code, projName)
            _uiState.update {
                it.copy(
                    isCompiling = false,
                    lastCompilationResult = compResult,
                    diagnostics = compResult.diagnostics
                )
            }

            if (!compResult.isSuccess || compResult.executable == null) {
                _uiState.update {
                    it.copy(
                        consoleOutput = compResult.rawOutput,
                        activeTab = EditorConsoleTab.DIAGNOSTICS
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isRunning = true,
                    activeTab = EditorConsoleTab.OUTPUT,
                    consoleOutput = "Running ${projName}...\n--------------------------------\n"
                )
            }

            val execResult = compilerService.execute(
                compResult.executable,
                _uiState.value.stdinInput
            )

            val summaryOutput = buildString {
                append(execResult.stdout)
                if (execResult.stderr.isNotBlank()) {
                    append("\n[stderr]:\n").append(execResult.stderr)
                }
                append("\n--------------------------------\n")
                append("Process finished with exit code ${execResult.exitCode} (${execResult.executionTimeMs}ms)")
            }

            _uiState.update {
                it.copy(
                    isRunning = false,
                    lastExecutionResult = execResult,
                    consoleOutput = summaryOutput,
                    exitCode = execResult.exitCode,
                    executionTimeMs = execResult.executionTimeMs,
                    memoryUsageBytes = execResult.memoryUsageBytes
                )
            }
        }
    }

    fun stopExecution() {
        compilerService.stopExecution()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun updateStdin(input: String) {
        _uiState.update { it.copy(stdinInput = input) }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleOutput = "", lastExecutionResult = null, exitCode = null) }
    }

    fun setActiveTab(tab: EditorConsoleTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleSearchReplace(show: Boolean? = null) {
        _uiState.update { it.copy(showSearchReplace = show ?: !it.showSearchReplace) }
    }

    fun updateSearchQuery(query: String) {
        val code = _uiState.value.textFieldValue.text
        val count = if (query.isNotBlank()) Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(code).count() else 0
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchMatchCount = count,
                currentMatchIndex = if (count > 0) 1 else 0
            )
        }
    }

    fun updateReplaceQuery(query: String) {
        _uiState.update { it.copy(replaceQuery = query) }
    }

    fun replaceAll() {
        val q = _uiState.value.searchQuery
        val r = _uiState.value.replaceQuery
        if (q.isNotEmpty()) {
            val code = _uiState.value.textFieldValue.text
            val replaced = code.replace(q, r, ignoreCase = true)
            onCodeChanged(TextFieldValue(replaced))
            updateSearchQuery(q)
        }
    }

    fun insertSnippet(snippet: SnippetEntity) {
        insertTextAtCursor(snippet.code)
    }

    fun jumpToLine(lineNumber: Int) {
        val lines = _uiState.value.textFieldValue.text.lines()
        var charOffset = 0
        val target = (lineNumber - 1).coerceIn(0, maxOf(0, lines.size - 1))
        for (i in 0 until target) {
            charOffset += lines[i].length + 1
        }
        _uiState.update {
            it.copy(
                textFieldValue = it.textFieldValue.copy(selection = TextRange(charOffset))
            )
        }
    }
}
