package com.calltranscriber.data.repository

import com.calltranscriber.data.local.CallDao
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CallRow(val id: String, val user_id: String, val remote_number: String, val direction: String, val started_at: String, val ended_at: String? = null, val duration_seconds: Int? = null, val status: String)

@Serializable
data class TranscriptRow(val id: String, val call_id: String, val text: String, val language: String)

@Singleton
class CallRepository @Inject constructor(private val callDao: CallDao) {
    private val supabase = SupabaseClientProvider.client

    fun getAllCalls(): Flow<List<CallEntity>> = callDao.getAllCalls()
    fun searchCalls(query: String): Flow<List<CallEntity>> = callDao.searchCalls(query)
    suspend fun getCallById(callId: String): CallEntity? = callDao.getCallById(callId)

    suspend fun syncFromCloud() {
        val calls = supabase.postgrest["calls"].select().decodeList<CallRow>()
        val entities = calls.map { call ->
            val transcripts = supabase.postgrest["transcripts"].select { filter { eq("call_id", call.id) } }.decodeList<TranscriptRow>()
            CallEntity(id = call.id, remoteNumber = call.remote_number, direction = call.direction, startedAt = parseTimestamp(call.started_at), endedAt = call.ended_at?.let { parseTimestamp(it) }, durationSeconds = call.duration_seconds, status = call.status, transcriptText = transcripts.firstOrNull()?.text, syncedToCloud = true)
        }
        callDao.upsertCalls(entities)
    }

    suspend fun saveLocalCall(call: CallEntity) { callDao.upsertCall(call) }

    private fun parseTimestamp(ts: String): Long = try { java.time.Instant.parse(ts).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
}
