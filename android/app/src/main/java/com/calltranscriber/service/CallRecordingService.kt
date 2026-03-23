package com.calltranscriber.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CallRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "call_recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        const val EXTRA_CALL_ID = "call_id"
        private const val TAG = "CallRecordingService"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var currentCallId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return START_NOT_STICKY
                currentCallId = callId
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording(callId)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(callId: String) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "No RECORD_AUDIO permission", e)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        audioRecord?.startRecording()
        Log.i(TAG, "Recording started for call $callId")

        scope.launch {
            val rawFile = File(filesDir, "recordings/$callId.pcm")
            rawFile.parentFile?.mkdirs()

            FileOutputStream(rawFile).use { fos ->
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (bytesRead > 0) fos.write(buffer, 0, bytesRead)
                }
            }

            // Convert PCM to WAV
            val wavFile = File(filesDir, "recordings/$callId.wav")
            pcmToWav(rawFile, wavFile)
            rawFile.delete()

            // Encrypt
            encryptFile(wavFile, callId)
            Log.i(TAG, "Recording saved and encrypted for call $callId")
        }
    }

    private fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        Log.i(TAG, "Recording stopped for call ${currentCallId}")
        currentCallId = null
    }

    private fun pcmToWav(pcmFile: File, wavFile: File) {
        val pcmData = pcmFile.readBytes()
        val totalDataLen = pcmData.size + 36
        FileOutputStream(wavFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(totalDataLen); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1) // PCM
                putShort(1) // mono
                putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2) // byte rate
                putShort(2) // block align
                putShort(16) // bits per sample
                put("data".toByteArray()); putInt(pcmData.size)
            }
            fos.write(header.array())
            fos.write(pcmData)
        }
    }

    private fun encryptFile(sourceFile: File, callId: String) {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encFile = File(filesDir, "recordings_encrypted/$callId.wav.enc")
        encFile.parentFile?.mkdirs()
        val ef = EncryptedFile.Builder(this, encFile, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        ef.openFileOutput().use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
        sourceFile.delete()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gespraech wird aufgezeichnet",
                NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gespraech wird aufgezeichnet")
            .setContentText("Aufnahme laeuft...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording()
        scope.cancel()
        super.onDestroy()
    }
}
