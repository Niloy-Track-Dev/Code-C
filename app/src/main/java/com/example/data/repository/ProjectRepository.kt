package com.example.data.repository

import com.example.data.dao.ProjectDao
import com.example.data.dao.SnippetDao
import com.example.data.entity.ProjectEntity
import com.example.data.entity.SnippetEntity
import com.example.data.storage.ProjectFileManager
import com.example.templates.TemplatesData
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val snippetDao: SnippetDao,
    private val fileManager: ProjectFileManager
) {

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val allSnippets: Flow<List<SnippetEntity>> = snippetDao.getAllSnippets()

    suspend fun initializeDefaultsIfNeeded() {
        if (snippetDao.getSnippetCount() == 0) {
            snippetDao.insertAll(TemplatesData.DEFAULT_SNIPPETS)
        }
    }

    suspend fun createProject(name: String, templateId: String? = null, initialCode: String? = null): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val template = TemplatesData.ALL_TEMPLATES.firstOrNull { it.id == templateId }
        val codeToSave = initialCode ?: template?.initialCode ?: """#include <stdio.h>

int main(void) {
    printf("Hello from $name!\n");
    return 0;
}
"""
        val project = ProjectEntity(
            id = id,
            name = name.trim().ifEmpty { "Project" },
            mainFileName = "main.c",
            templateType = templateId
        )

        projectDao.insertProject(project)
        fileManager.saveProjectFile(id, "main.c", codeToSave)
        return project
    }

    suspend fun getProject(id: String): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    suspend fun loadProjectSource(projectId: String): String {
        val code = fileManager.readProjectFile(projectId, "main.c")
        if (code.isNotEmpty()) {
            projectDao.updateLastOpened(projectId, System.currentTimeMillis())
        }
        return code
    }

    suspend fun saveProjectSource(projectId: String, content: String) {
        fileManager.saveProjectFile(projectId, "main.c", content)
        projectDao.updateModifiedTime(projectId, System.currentTimeMillis())
        fileManager.clearDraft()
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
        fileManager.deleteProjectFiles(id)
    }

    suspend fun renameProject(id: String, newName: String) {
        val proj = projectDao.getProjectById(id)
        if (proj != null) {
            projectDao.updateProject(proj.copy(name = newName.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun duplicateProject(id: String): ProjectEntity? {
        val orig = projectDao.getProjectById(id) ?: return null
        val source = fileManager.readProjectFile(id, "main.c")
        val newName = "${orig.name} (Copy)"
        return createProject(newName, orig.templateType, source)
    }

    suspend fun saveDraft(projectId: String, content: String) {
        fileManager.saveDraft(projectId, content)
    }

    suspend fun getDraft(): Pair<String, String>? {
        return fileManager.getDraft()
    }

    suspend fun clearDraft() {
        fileManager.clearDraft()
    }

    suspend fun exportProjectFile(projectId: String, name: String): File {
        return fileManager.exportProjectZip(projectId, name)
    }

    suspend fun addSnippet(title: String, prefix: String, description: String, code: String): Long {
        val snippet = SnippetEntity(
            title = title,
            prefix = prefix,
            description = description,
            code = code,
            isCustom = true
        )
        return snippetDao.insertSnippet(snippet)
    }

    suspend fun deleteSnippet(id: Long) {
        snippetDao.deleteSnippetById(id)
    }

    suspend fun getFileSize(projectId: String): Long {
        return fileManager.getProjectFileSize(projectId, "main.c")
    }
}
