package com.calltranscriber.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startedAt DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): CallEntity?

    @Query("SELECT * FROM calls WHERE transcriptText LIKE '%' || :query || '%' ORDER BY startedAt DESC")
    fun searchCalls(query: String): Flow<List<CallEntity>>

    @Upsert
    suspend fun upsertCall(call: CallEntity)

    @Upsert
    suspend fun upsertCalls(calls: List<CallEntity>)

    @Query("UPDATE calls SET transcriptText = :text WHERE id = :callId")
    suspend fun updateTranscript(callId: String, text: String)

    @Query("UPDATE calls SET syncedToCloud = 1 WHERE id = :callId")
    suspend fun markSynced(callId: String)
}
