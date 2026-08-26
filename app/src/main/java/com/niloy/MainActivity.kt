package com.niloy

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
import com.niloy.ui.navigation.AppNavigation
import com.niloy.ui.theme.CCompilerTheme
import com.niloy.ui.viewmodel.EditorViewModel
import com.niloy.ui.viewmodel.ProjectsViewModel
import com.niloy.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()
    private val projectsViewModel: ProjectsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
