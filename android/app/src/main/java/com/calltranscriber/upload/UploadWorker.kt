package com.calltranscriber.upload

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.work.*
import com.calltranscriber.data.remote.ApiClient
import com.calltranscriber.data.repository.AuthRepository
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val callId = inputData.getString("call_id") ?: return Result.failure()
        val remoteNumber = inputData.getString("remote_number") ?: return Result.failure()
        val direction = inputData.getString("direction") ?: return Result.failure()
        val startedAt = inputData.getString("started_at") ?: return Result.failure()
        val endedAt = inputData.getString("ended_at") ?: return Result.failure()
        val durationSeconds = inputData.getInt("duration_seconds", 0)

        val encryptedFilePath = File(applicationContext.filesDir, "recordings_encrypted/$callId.wav.enc")
        if (!encryptedFilePath.exists()) return Result.failure()

        val masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val ef = EncryptedFile.Builder(applicationContext, encryptedFilePath, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        val tempFile = File.createTempFile("upload_", ".wav", applicationContext.cacheDir)
        try {
            ef.openFileInput().use { input -> FileOutputStream(tempFile).use { output -> input.copyTo(output) } }
            val token = AuthRepository().getAccessToken() ?: return Result.retry()
            val response = ApiClient().uploadAudio(token, callId, tempFile, remoteNumber, direction, startedAt, endedAt, durationSeconds)
            return if (response.status.value in 200..299) Result.success() else Result.retry()
        } finally { tempFile.delete() }
    }

    companion object {
        fun enqueue(context: Context, callId: String, remoteNumber: String, direction: String, startedAt: String, endedAt: String, durationSeconds: Int) {
            val data = workDataOf("call_id" to callId, "remote_number" to remoteNumber, "direction" to direction, "started_at" to startedAt, "ended_at" to endedAt, "duration_seconds" to durationSeconds)
            val request = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(data).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("upload_$callId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
