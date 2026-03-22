package com.calltranscriber.data.remote

import com.calltranscriber.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

class ApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    private val client = HttpClient(Android)

    suspend fun uploadAudio(token: String, callId: String, audioFile: File, remoteNumber: String, direction: String, startedAt: String, endedAt: String, durationSeconds: Int): HttpResponse {
        return client.submitFormWithBinaryData(url = "$baseUrl/upload", formData = formData {
            append("remote_number", remoteNumber); append("direction", direction); append("started_at", startedAt); append("ended_at", endedAt); append("duration_seconds", durationSeconds.toString())
            append("audio", audioFile.readBytes(), Headers.build { append(HttpHeaders.ContentType, "audio/wav"); append(HttpHeaders.ContentDisposition, "filename=\"$callId.wav\"") })
        }) { header(HttpHeaders.Authorization, "Bearer $token") }
    }
}
