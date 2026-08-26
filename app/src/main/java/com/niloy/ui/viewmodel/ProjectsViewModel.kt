package com.niloy.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niloy.data.database.AppDatabase
import com.niloy.data.entity.ProjectEntity
import com.niloy.data.repository.ProjectRepository
import com.niloy.data.storage.ProjectFileManager
import com.niloy.templates.TemplatesData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ProjectsUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val isCreatingProject: Boolean = false,
    val projectToRename: ProjectEntity? = null,
    val projectToDelete: ProjectEntity? = null,
    val exportedFile: File? = null
)

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val fileManager = ProjectFileManager(application)
    private val repository = ProjectRepository(db.projectDao(), db.snippetDao(), fileManager)

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    val allTemplates = TemplatesData.ALL_TEMPLATES

    init {
        viewModelScope.launch {
            repository.allProjects.collect { projList ->
                _uiState.update { it.copy(projects = projList) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun createProject(name: String, templateId: String? = null, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val proj = repository.createProject(name, templateId)
            onSuccess(proj.id)
        }
    }

    fun renameProject(id: String, newName: String) {
        viewModelScope.launch {
            repository.renameProject(id, newName)
            _uiState.update { it.copy(projectToRename = null) }
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            repository.duplicateProject(id)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            _uiState.update { it.copy(projectToDelete = null) }
        }
    }

    fun prepareRename(project: ProjectEntity) {
        _uiState.update { it.copy(projectToRename = project) }
    }

    fun prepareDelete(project: ProjectEntity) {
        _uiState.update { it.copy(projectToDelete = project) }
    }

    fun dismissModals() {
        _uiState.update { it.copy(projectToRename = null, projectToDelete = null, exportedFile = null) }
    }

    fun exportProject(project: ProjectEntity, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = repository.exportProjectFile(project.id, project.name)
            onReady(file)
        }
    }
}
