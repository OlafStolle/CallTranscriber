package com.calltranscriber.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val id: String,
    val remoteNumber: String,
    val direction: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Int? = null,
    val status: String,
    val transcriptText: String? = null,
    val audioFilePath: String? = null,
    val syncedToCloud: Boolean = false,
)
