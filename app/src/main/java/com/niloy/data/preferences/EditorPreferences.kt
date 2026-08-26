package com.niloy.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.niloy.compiler.model.CStandard
import com.niloy.compiler.model.CompilerConfig
import com.niloy.compiler.model.OptimizationLevel
import com.niloy.compiler.model.WarningLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "c_compiler_settings")

enum class AppThemeSetting {
    SYSTEM,
    DARK_DEVELOPER,
    LIGHT_MINIMAL,
    MONOKAI,
    SOLARIZED_DARK
}

data class UserSettings(
    val fontSizeSp: Int = 14,
    val tabSize: Int = 4,
    val useSpacesForTabs: Boolean = true,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val syntaxHighlighting: Boolean = true,
    val theme: AppThemeSetting = AppThemeSetting.DARK_DEVELOPER,
    val standard: CStandard = CStandard.C11,
    val warningLevel: WarningLevel = WarningLevel.WALL,
    val treatWarningsAsErrors: Boolean = false,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.O2,
    val executionTimeoutMs: Long = 5000L,
    val maxOutputChars: Int = 65536,
    val autoScrollOutput: Boolean = true,
    val clearOutputBeforeRun: Boolean = true
) {
    fun toCompilerConfig(): CompilerConfig {
        return CompilerConfig(
            standard = standard,
            warningLevel = warningLevel,
            treatWarningsAsErrors = treatWarningsAsErrors,
            optimizationLevel = optimizationLevel,
            timeoutMs = executionTimeoutMs,
            maxOutputChars = maxOutputChars,
            autoScrollOutput = autoScrollOutput,
            clearOutputBeforeRun = clearOutputBeforeRun
        )
    }
}

class EditorPreferences(private val context: Context) {

    private object PreferencesKeys {
        val FONT_SIZE = intPreferencesKey("font_size")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val USE_SPACES = booleanPreferencesKey("use_spaces")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val LINE_NUMBERS = booleanPreferencesKey("line_numbers")
        val HIGHLIGHT_CURRENT_LINE = booleanPreferencesKey("highlight_current_line")
        val AUTO_CLOSE_BRACKETS = booleanPreferencesKey("auto_close_brackets")
        val SYNTAX_HIGHLIGHTING = booleanPreferencesKey("syntax_highlighting")
        val THEME = stringPreferencesKey("theme")
        val C_STANDARD = stringPreferencesKey("c_standard")
        val WARNING_LEVEL = stringPreferencesKey("warning_level")
        val TREAT_WARNINGS_AS_ERRORS = booleanPreferencesKey("treat_warnings_as_errors")
        val OPTIMIZATION = stringPreferencesKey("optimization")
        val TIMEOUT_MS = longPreferencesKey("timeout_ms")
        val MAX_OUTPUT_CHARS = intPreferencesKey("max_output_chars")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val CLEAR_ON_RUN = booleanPreferencesKey("clear_on_run")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserSettings(
                fontSizeSp = prefs[PreferencesKeys.FONT_SIZE] ?: 14,
                tabSize = prefs[PreferencesKeys.TAB_SIZE] ?: 4,
                useSpacesForTabs = prefs[PreferencesKeys.USE_SPACES] ?: true,
                wordWrap = prefs[PreferencesKeys.WORD_WRAP] ?: false,
                showLineNumbers = prefs[PreferencesKeys.LINE_NUMBERS] ?: true,
                highlightCurrentLine = prefs[PreferencesKeys.HIGHLIGHT_CURRENT_LINE] ?: true,
                autoCloseBrackets = prefs[PreferencesKeys.AUTO_CLOSE_BRACKETS] ?: true,
                syntaxHighlighting = prefs[PreferencesKeys.SYNTAX_HIGHLIGHTING] ?: true,
                theme = AppThemeSetting.valueOf(prefs[PreferencesKeys.THEME] ?: AppThemeSetting.DARK_DEVELOPER.name),
                standard = CStandard.valueOf(prefs[PreferencesKeys.C_STANDARD] ?: CStandard.C11.name),
                warningLevel = WarningLevel.valueOf(prefs[PreferencesKeys.WARNING_LEVEL] ?: WarningLevel.WALL.name),
                treatWarningsAsErrors = prefs[PreferencesKeys.TREAT_WARNINGS_AS_ERRORS] ?: false,
                optimizationLevel = OptimizationLevel.valueOf(prefs[PreferencesKeys.OPTIMIZATION] ?: OptimizationLevel.O2.name),
                executionTimeoutMs = prefs[PreferencesKeys.TIMEOUT_MS] ?: 5000L,
                maxOutputChars = prefs[PreferencesKeys.MAX_OUTPUT_CHARS] ?: 65536,
                autoScrollOutput = prefs[PreferencesKeys.AUTO_SCROLL] ?: true,
                clearOutputBeforeRun = prefs[PreferencesKeys.CLEAR_ON_RUN] ?: true
            )
        }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { it[PreferencesKeys.FONT_SIZE] = size }
    }

    suspend fun updateTabSize(size: Int) {
        context.dataStore.edit { it[PreferencesKeys.TAB_SIZE] = size }
    }

    suspend fun updateWordWrap(wrap: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WORD_WRAP] = wrap }
    }

    suspend fun updateLineNumbers(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.LINE_NUMBERS] = show }
    }

    suspend fun updateHighlightLine(highlight: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HIGHLIGHT_CURRENT_LINE] = highlight }
    }

    suspend fun updateAutoCloseBrackets(autoClose: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_CLOSE_BRACKETS] = autoClose }
    }

    suspend fun updateSyntaxHighlighting(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SYNTAX_HIGHLIGHTING] = enabled }
    }

    suspend fun updateTheme(theme: AppThemeSetting) {
        context.dataStore.edit { it[PreferencesKeys.THEME] = theme.name }
    }

    suspend fun updateStandard(standard: CStandard) {
        context.dataStore.edit { it[PreferencesKeys.C_STANDARD] = standard.name }
    }

    suspend fun updateWarningLevel(level: WarningLevel) {
        context.dataStore.edit { it[PreferencesKeys.WARNING_LEVEL] = level.name }
    }

    suspend fun updateTreatWarningsAsErrors(treat: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.TREAT_WARNINGS_AS_ERRORS] = treat }
    }

    suspend fun updateOptimization(opt: OptimizationLevel) {
        context.dataStore.edit { it[PreferencesKeys.OPTIMIZATION] = opt.name }
    }

    suspend fun updateTimeoutMs(timeout: Long) {
        context.dataStore.edit { it[PreferencesKeys.TIMEOUT_MS] = timeout }
    }

    suspend fun updateAutoScroll(autoScroll: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_SCROLL] = autoScroll }
    }

    suspend fun updateClearOnRun(clear: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CLEAR_ON_RUN] = clear }
    }
}
