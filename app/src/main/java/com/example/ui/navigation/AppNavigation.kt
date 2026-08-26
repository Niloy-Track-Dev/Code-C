package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ProjectsViewModel
import com.example.ui.viewmodel.SettingsViewModel

object AppRoutes {
    const val EDITOR = "editor"
    const val PROJECTS = "projects"
    const val TEMPLATES = "templates"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    editorViewModel: EditorViewModel = viewModel(),
    projectsViewModel: ProjectsViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val editorUiState by editorViewModel.uiState.collectAsState()
    val editorSettings by editorViewModel.settings.collectAsState()
    val snippets by editorViewModel.snippets.collectAsState()

    val projectsUiState by projectsViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.settings.collectAsState()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.EDITOR
    ) {
        composable(AppRoutes.EDITOR) {
            EditorScreen(
                uiState = editorUiState,
                settings = editorSettings,
                snippets = snippets,
                onCodeChange = editorViewModel::onCodeChanged,
                onUndo = editorViewModel::undo,
                onRedo = editorViewModel::redo,
                onFormat = editorViewModel::formatCode,
                onSave = editorViewModel::saveProject,
                onCompile = editorViewModel::compileCode,
                onRun = editorViewModel::runCode,
                onStop = editorViewModel::stopExecution,
                onInsertSymbol = editorViewModel::insertTextAtCursor,
                onInsertSnippet = editorViewModel::insertSnippet,
                onTabSelected = editorViewModel::setActiveTab,
                onStdinChanged = editorViewModel::updateStdin,
                onClearConsole = editorViewModel::clearConsole,
                onJumpToLine = editorViewModel::jumpToLine,
                onToggleSearch = { editorViewModel.toggleSearchReplace() },
                onSearchQueryChange = editorViewModel::updateSearchQuery,
                onReplaceQueryChange = editorViewModel::updateReplaceQuery,
                onReplaceAll = editorViewModel::replaceAll,
                onNavigateToProjects = { navController.navigate(AppRoutes.PROJECTS) },
                onNavigateToTemplates = { navController.navigate(AppRoutes.TEMPLATES) },
                onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onNavigateToAbout = { navController.navigate(AppRoutes.ABOUT) }
            )
        }

        composable(AppRoutes.PROJECTS) {
            ProjectsScreen(
                uiState = projectsUiState,
                onSearchQueryChange = projectsViewModel::updateSearchQuery,
                onCreateProject = { name, templateId ->
                    projectsViewModel.createProject(name, templateId) { newId ->
                        editorViewModel.loadProject(newId)
                        navController.navigate(AppRoutes.EDITOR) {
                            popUpTo(AppRoutes.EDITOR) { inclusive = true }
                        }
                    }
                },
                onSelectProject = { projectId ->
                    editorViewModel.loadProject(projectId)
                    navController.navigate(AppRoutes.EDITOR) {
                        popUpTo(AppRoutes.EDITOR) { inclusive = true }
                    }
                },
                onRenameProject = projectsViewModel::renameProject,
                onDuplicateProject = projectsViewModel::duplicateProject,
                onDeleteProject = projectsViewModel::deleteProject,
                onExportProject = projectsViewModel::exportProject,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.TEMPLATES) {
            TemplatesScreen(
                onSelectTemplate = { template ->
                    projectsViewModel.createProject(template.title, template.id) { newId ->
                        editorViewModel.loadProject(newId)
                        navController.navigate(AppRoutes.EDITOR) {
                            popUpTo(AppRoutes.EDITOR) { inclusive = true }
                        }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                settings = settingsState,
                onUpdateFontSize = settingsViewModel::updateFontSize,
                onUpdateTabSize = settingsViewModel::updateTabSize,
                onUpdateWordWrap = settingsViewModel::updateWordWrap,
                onUpdateLineNumbers = settingsViewModel::updateLineNumbers,
                onUpdateHighlightLine = settingsViewModel::updateHighlightLine,
                onUpdateAutoCloseBrackets = settingsViewModel::updateAutoCloseBrackets,
                onUpdateSyntaxHighlighting = settingsViewModel::updateSyntaxHighlighting,
                onUpdateTheme = settingsViewModel::updateTheme,
                onUpdateStandard = settingsViewModel::updateStandard,
                onUpdateWarningLevel = settingsViewModel::updateWarningLevel,
                onUpdateTreatWarningsAsErrors = settingsViewModel::updateTreatWarningsAsErrors,
                onUpdateOptimization = settingsViewModel::updateOptimization,
                onUpdateTimeoutMs = settingsViewModel::updateTimeoutMs,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.ABOUT) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
