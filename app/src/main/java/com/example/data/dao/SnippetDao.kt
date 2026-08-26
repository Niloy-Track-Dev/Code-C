package com.example.data.dao

import androidx.room.*
import com.example.data.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {

    @Query("SELECT * FROM snippets ORDER BY isCustom DESC, title ASC")
    fun getAllSnippets(): Flow<List<SnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: SnippetEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(snippets: List<SnippetEntity>)

    @Update
    suspend fun updateSnippet(snippet: SnippetEntity)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteSnippetById(id: Long)

    @Query("SELECT COUNT(*) FROM snippets")
    suspend fun getSnippetCount(): Int
}
