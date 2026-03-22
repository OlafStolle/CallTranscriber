package com.calltranscriber.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.calltranscriber.sip.SipManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class CallRecordingService : Service() {
    @Inject lateinit var sipManager: SipManager
    companion object { const val CHANNEL_ID = "call_recording"; const val NOTIFICATION_ID = 1; const val ACTION_START = "START_RECORDING"; const val ACTION_STOP = "STOP_RECORDING"; const val EXTRA_CALL_ID = "call_id" }
    private var currentCallId: String? = null

    override fun onCreate() { super.onCreate(); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> { val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return START_NOT_STICKY; currentCallId = callId; startForeground(NOTIFICATION_ID, createNotification()); startRecording(callId) }
            ACTION_STOP -> { stopRecording(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(callId: String) {
        val recordFile = File(filesDir, "recordings/$callId.wav"); recordFile.parentFile?.mkdirs()
        val call = sipManager.currentCall ?: return
        val params = sipManager.getCore().createCallParams(call)
        params?.recordFile = recordFile.absolutePath; call.update(params); call.startRecording()
    }

    private fun stopRecording() {
        sipManager.currentCall?.stopRecording()
        val callId = currentCallId ?: return
        val plainFile = File(filesDir, "recordings/$callId.wav")
        if (plainFile.exists()) encryptFile(plainFile, callId)
        currentCallId = null
    }

    private fun encryptFile(sourceFile: File, callId: String) {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encFile = File(filesDir, "recordings_encrypted/$callId.wav.enc"); encFile.parentFile?.mkdirs()
        val ef = EncryptedFile.Builder(this, encFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        ef.openFileOutput().use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
        sourceFile.delete()
    }

    private fun createNotificationChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Gespraech wird aufgezeichnet", NotificationManager.IMPORTANCE_LOW)) }
    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Gespraech wird aufgezeichnet").setContentText("Aufnahme laeuft...").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    override fun onBind(intent: Intent?): IBinder? = null
}
