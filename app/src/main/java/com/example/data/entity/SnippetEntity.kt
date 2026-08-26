package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val prefix: String,
    val description: String,
    val code: String,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
