package com.niloy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.niloy.ui.screens.*
import com.niloy.ui.viewmodel.EditorViewModel
import com.niloy.ui.viewmodel.ProjectsViewModel
import com.niloy.ui.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
object RouteProjects

@Serializable
object RouteTemplates

@Serializable
data class RouteEditor(val projectId: String)

@Serializable
object RouteSettings

@Serializable
object RouteAbout

@Composable
fun AppNavigation(
    editorViewModel: EditorViewModel,
    projectsViewModel: ProjectsViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = RouteProjects
    ) {
        composable<RouteProjects> {
            ProjectsScreen(
                viewModel = projectsViewModel,
                onNavigateToEditor = { id -> navController.navigate(RouteEditor(id)) },
                onNavigateToTemplates = { navController.navigate(RouteTemplates) },
                onNavigateToSettings = { navController.navigate(RouteSettings) },
                onNavigateToAbout = { navController.navigate(RouteAbout) }
            )
        }

        composable<RouteTemplates> {
            TemplatesScreen(
                onTemplateSelected = { name, templateId ->
                    projectsViewModel.createProject(name, templateId) { id ->
                        navController.navigate(RouteEditor(id)) {
                            popUpTo(RouteTemplates) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<RouteEditor> { backStackEntry ->
            val route: RouteEditor = backStackEntry.toRoute()
            EditorScreen(
                projectId = route.projectId,
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(RouteSettings) }
            )
        }

        composable<RouteSettings> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<RouteAbout> {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
