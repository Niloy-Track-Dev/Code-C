package com.example.data.storage

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectFileManager(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    private val draftFile: File
        get() = File(context.filesDir, "draft_backup.c")

    private val draftMetaFile: File
        get() = File(context.filesDir, "draft_meta.txt")

    suspend fun saveProjectFile(projectId: String, fileName: String = "main.c", content: String) = withContext(Dispatchers.IO) {
        val projDir = File(projectsDir, projectId)
        if (!projDir.exists()) projDir.mkdirs()
        val file = File(projDir, fileName)
        file.writeText(content)
    }

    suspend fun readProjectFile(projectId: String, fileName: String = "main.c"): String = withContext(Dispatchers.IO) {
        val file = File(File(projectsDir, projectId), fileName)
        if (file.exists()) file.readText() else ""
    }

    suspend fun deleteProjectFiles(projectId: String) = withContext(Dispatchers.IO) {
        val projDir = File(projectsDir, projectId)
        if (projDir.exists()) {
            projDir.deleteRecursively()
        }
    }

    suspend fun saveDraft(projectId: String, content: String) = withContext(Dispatchers.IO) {
        draftFile.writeText(content)
        draftMetaFile.writeText("$projectId|${System.currentTimeMillis()}")
    }

    suspend fun clearDraft() = withContext(Dispatchers.IO) {
        if (draftFile.exists()) draftFile.delete()
        if (draftMetaFile.exists()) draftMetaFile.delete()
    }

    suspend fun getDraft(): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (draftFile.exists() && draftMetaFile.exists()) {
            val content = draftFile.readText()
            val meta = draftMetaFile.readText().split("|")
            val projId = if (meta.isNotEmpty()) meta[0] else ""
            if (content.isNotBlank() && projId.isNotBlank()) {
                return@withContext Pair(projId, content)
            }
        }
        null
    }

    suspend fun exportProjectZip(projectId: String, projectName: String): File = withContext(Dispatchers.IO) {
        val projDir = File(projectsDir, projectId)
        val exportFile = File(context.cacheDir, "${projectName.replace(" ", "_")}.c")
        val mainFile = File(projDir, "main.c")
        if (mainFile.exists()) {
            mainFile.copyTo(exportFile, overwrite = true)
        } else {
            exportFile.writeText("// C Project: $projectName\n")
        }
        exportFile
    }

    suspend fun getProjectFileSize(projectId: String, fileName: String = "main.c"): Long = withContext(Dispatchers.IO) {
        val file = File(File(projectsDir, projectId), fileName)
        if (file.exists()) file.length() else 0L
    }
}
