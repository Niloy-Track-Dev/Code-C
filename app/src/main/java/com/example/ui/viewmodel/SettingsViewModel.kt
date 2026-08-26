package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.model.CStandard
import com.example.compiler.model.OptimizationLevel
import com.example.compiler.model.WarningLevel
import com.example.data.preferences.AppThemeSetting
import com.example.data.preferences.EditorPreferences
import com.example.data.preferences.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = EditorPreferences(application)

    val settings: StateFlow<UserSettings> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    fun updateFontSize(size: Int) = viewModelScope.launch { preferences.updateFontSize(size) }
    fun updateTabSize(size: Int) = viewModelScope.launch { preferences.updateTabSize(size) }
    fun updateWordWrap(wrap: Boolean) = viewModelScope.launch { preferences.updateWordWrap(wrap) }
    fun updateLineNumbers(show: Boolean) = viewModelScope.launch { preferences.updateLineNumbers(show) }
    fun updateHighlightLine(highlight: Boolean) = viewModelScope.launch { preferences.updateHighlightLine(highlight) }
    fun updateAutoCloseBrackets(autoClose: Boolean) = viewModelScope.launch { preferences.updateAutoCloseBrackets(autoClose) }
    fun updateSyntaxHighlighting(enabled: Boolean) = viewModelScope.launch { preferences.updateSyntaxHighlighting(enabled) }
    fun updateTheme(theme: AppThemeSetting) = viewModelScope.launch { preferences.updateTheme(theme) }
    fun updateStandard(standard: CStandard) = viewModelScope.launch { preferences.updateStandard(standard) }
    fun updateWarningLevel(level: WarningLevel) = viewModelScope.launch { preferences.updateWarningLevel(level) }
    fun updateTreatWarningsAsErrors(treat: Boolean) = viewModelScope.launch { preferences.updateTreatWarningsAsErrors(treat) }
    fun updateOptimization(opt: OptimizationLevel) = viewModelScope.launch { preferences.updateOptimization(opt) }
    fun updateTimeoutMs(timeout: Long) = viewModelScope.launch { preferences.updateTimeoutMs(timeout) }
    fun updateAutoScroll(autoScroll: Boolean) = viewModelScope.launch { preferences.updateAutoScroll(autoScroll) }
    fun updateClearOnRun(clear: Boolean) = viewModelScope.launch { preferences.updateClearOnRun(clear) }
}
