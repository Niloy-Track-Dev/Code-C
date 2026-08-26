package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.CCompilerTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ProjectsViewModel
import com.example.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()
    private val projectsViewModel: ProjectsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load active or default project on startup
        editorViewModel.loadDefaultOrNewProject()

        setContent {
            val settings by settingsViewModel.settings.collectAsState()

            CCompilerTheme(themeSetting = settings.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        editorViewModel = editorViewModel,
                        projectsViewModel = projectsViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        editorViewModel.saveProject()
    }
}
