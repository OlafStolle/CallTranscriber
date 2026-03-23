# Replace SIP with Microphone Recorder — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all SIP/linphone code and replace with a simple microphone recorder that captures audio during phone calls via AudioRecord API, with PhoneStateListener for automatic call detection.

**Architecture:** User makes/receives calls via normal phone app. Our app runs a Foreground Service that records via AudioRecord(VOICE_COMMUNICATION). PhoneStateListener detects call start/end to auto-trigger recording. Encrypted WAV uploaded via existing WorkManager pipeline. No SIP, no linphone, no VoIP.

**Tech Stack:** Kotlin, AudioRecord (VOICE_COMMUNICATION), TelephonyManager/PhoneStateListener, Foreground Service, EncryptedFile, WorkManager (existing).

---

## What Changes

| Action | Files |
|--------|-------|
| DELETE | `sip/SipManager.kt`, `sip/SipConfig.kt` |
| DELETE | `service/CallConnectionService.kt` |
| CREATE | `recording/CallState.kt` (moved enum) |
| CREATE | `recording/CallDetector.kt` (PhoneStateListener) |
| REWRITE | `service/CallRecordingService.kt` (AudioRecord instead of linphone) |
| MODIFY | `CallTranscriberApp.kt` (remove SIP init) |
| MODIFY | `ui/dialer/DialerViewModel.kt` (remove SIP, use CallDetector) |
| MODIFY | `ui/dialer/DialerScreen.kt` (import path change) |
| MODIFY | `app/build.gradle.kts` (remove linphone dep + SIP BuildConfig) |
| MODIFY | `AndroidManifest.xml` (remove CallConnectionService) |
| MODIFY | `CLAUDE.md` (correct project description) |

---

## Task 1: Remove SIP Code + linphone Dependency

**Files:**
- Delete: `android/app/src/main/java/com/calltranscriber/sip/SipManager.kt`
- Delete: `android/app/src/main/java/com/calltranscriber/sip/SipConfig.kt`
- Delete: `android/app/src/main/java/com/calltranscriber/service/CallConnectionService.kt`
- Modify: `android/app/build.gradle.kts` — remove linphone dep + SIP BuildConfig fields
- Modify: `android/app/src/main/AndroidManifest.xml` — remove CallConnectionService + MANAGE_OWN_CALLS
- Modify: `android/app/src/main/java/com/calltranscriber/CallTranscriberApp.kt` — remove SIP init

- [ ] **Step 1: Delete SIP files**
```bash
rm android/app/src/main/java/com/calltranscriber/sip/SipManager.kt
rm android/app/src/main/java/com/calltranscriber/sip/SipConfig.kt
rm android/app/src/main/java/com/calltranscriber/service/CallConnectionService.kt
rmdir android/app/src/main/java/com/calltranscriber/sip/
```

- [ ] **Step 2: Remove linphone from build.gradle.kts**
Remove these lines from `android/app/build.gradle.kts`:
```
implementation("org.linphone:linphone-sdk-android:5.3.74")
```
And remove the three SIP BuildConfig fields:
```
buildConfigField("String", "SIP_USERNAME", ...)
buildConfigField("String", "SIP_PASSWORD", ...)
buildConfigField("String", "SIP_DOMAIN", ...)
```

- [ ] **Step 3: Remove linphone Maven from settings.gradle.kts**
Remove:
```
maven { url = uri("https://linphone.org/maven_repository") }
```

- [ ] **Step 4: Clean AndroidManifest.xml**
Remove the `CallConnectionService` service block and `MANAGE_OWN_CALLS` permission. Keep `READ_PHONE_STATE` (needed for PhoneStateListener). Add `READ_CALL_LOG` for call number detection.

New manifest permissions:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
```

Remove the entire `CallConnectionService` `<service>` block.

- [ ] **Step 5: Simplify CallTranscriberApp.kt**
```kotlin
package com.calltranscriber

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CallTranscriberApp : Application()
```

- [ ] **Step 6: Commit**
```bash
git add -A
git commit -m "refactor(android): remove SIP/linphone code — preparing for mic recorder"
```

---

## Task 2: Create CallState Enum + CallDetector

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/recording/CallState.kt`
- Create: `android/app/src/main/java/com/calltranscriber/recording/CallDetector.kt`

- [ ] **Step 1: Create CallState enum (technology-agnostic)**
```kotlin
// android/app/src/main/java/com/calltranscriber/recording/CallState.kt
package com.calltranscriber.recording

enum class CallState { IDLE, RINGING, IN_CALL, ENDED }
```

- [ ] **Step 2: Create CallDetector (PhoneStateListener wrapper)**
```kotlin
// android/app/src/main/java/com/calltranscriber/recording/CallDetector.kt
package com.calltranscriber.recording

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallDetector @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    private val _phoneNumber = MutableStateFlow<String?>(null)
    val phoneNumber = _phoneNumber.asStateFlow()

    private var isListening = false

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            val newState = when (state) {
                TelephonyManager.CALL_STATE_IDLE -> CallState.IDLE
                TelephonyManager.CALL_STATE_RINGING -> {
                    if (!incomingNumber.isNullOrBlank()) _phoneNumber.value = incomingNumber
                    CallState.RINGING
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> CallState.IN_CALL
                else -> CallState.IDLE
            }
            Log.i("CallDetector", "Phone state: $state -> $newState (number: $incomingNumber)")
            _callState.value = newState
        }
    }

    @Suppress("DEPRECATION")
    fun startListening() {
        if (isListening) return
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        isListening = true
        Log.i("CallDetector", "Started listening for phone state changes")
    }

    @Suppress("DEPRECATION")
    fun stopListening() {
        if (!isListening) return
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        isListening = false
    }

    fun setOutgoingNumber(number: String) {
        _phoneNumber.value = number
    }
}
```

- [ ] **Step 3: Create recording directory**
```bash
mkdir -p android/app/src/main/java/com/calltranscriber/recording
```

- [ ] **Step 4: Commit**
```bash
git add android/
git commit -m "feat(android): CallState enum + CallDetector with PhoneStateListener"
```

---

## Task 3: Rewrite CallRecordingService with AudioRecord

**Files:**
- Rewrite: `android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt`

- [ ] **Step 1: Rewrite CallRecordingService**
```kotlin
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
```

- [ ] **Step 2: Commit**
```bash
git add android/
git commit -m "feat(android): AudioRecord-based CallRecordingService (replaces linphone)"
```

---

## Task 4: Update DialerViewModel + DialerScreen (Recording Control)

**Files:**
- Rewrite: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerViewModel.kt`
- Rewrite: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerScreen.kt`

The Dialer becomes a "Recording Control" screen. User makes calls via normal phone, our app detects and records.

- [ ] **Step 1: Rewrite DialerViewModel**
```kotlin
package com.calltranscriber.ui.dialer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import com.calltranscriber.recording.CallDetector
import com.calltranscriber.recording.CallState
import com.calltranscriber.service.CallRecordingService
import com.calltranscriber.upload.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val callDetector: CallDetector,
    private val callRepository: CallRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val callState = callDetector.callState
    val detectedNumber = callDetector.phoneNumber

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var currentCallId: String? = null
    private var callStartTime: Instant? = null

    init {
        callDetector.startListening()
    }

    fun startRecording(phoneNumber: String) {
        if (_isRecording.value) return
        currentCallId = UUID.randomUUID().toString()
        callStartTime = Instant.now()
        _isRecording.value = true

        context.startForegroundService(Intent(context, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_START
            putExtra(CallRecordingService.EXTRA_CALL_ID, currentCallId)
        })

        viewModelScope.launch {
            callRepository.saveLocalCall(CallEntity(
                id = currentCallId!!,
                remoteNumber = phoneNumber.ifBlank { "Unbekannt" },
                direction = "outbound",
                startedAt = callStartTime!!.toEpochMilli(),
                status = "recording",
            ))
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false

        context.startService(Intent(context, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_STOP
        })

        val callId = currentCallId ?: return
        val start = callStartTime ?: return
        val end = Instant.now()
        val dur = (end.epochSecond - start.epochSecond).toInt()

        viewModelScope.launch {
            callRepository.saveLocalCall(CallEntity(
                id = callId,
                remoteNumber = detectedNumber.value ?: "Unbekannt",
                direction = "outbound",
                startedAt = start.toEpochMilli(),
                endedAt = end.toEpochMilli(),
                durationSeconds = dur,
                status = "uploading",
            ))
            UploadWorker.enqueue(context, callId, detectedNumber.value ?: "Unbekannt",
                "outbound", start.toString(), end.toString(), dur)
        }
        currentCallId = null
        callStartTime = null
    }

    override fun onCleared() {
        callDetector.stopListening()
        super.onCleared()
    }
}
```

- [ ] **Step 2: Rewrite DialerScreen as Recording Control**
```kotlin
package com.calltranscriber.ui.dialer

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.recording.CallState

@Composable
fun DialerScreen(viewModel: DialerViewModel = hiltViewModel()) {
    val callState by viewModel.callState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val detectedNumber by viewModel.detectedNumber.collectAsState()
    val context = LocalContext.current
    val hasMicPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var manualNumber by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aufnahme", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Telefoniere normal ueber deine Telefon-App.\nDiese App nimmt das Gespraech auf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        // Phone state indicator
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Telefon-Status", style = MaterialTheme.typography.labelMedium)
                Text(
                    when (callState) {
                        CallState.IDLE -> "Kein Anruf"
                        CallState.RINGING -> "Eingehender Anruf..."
                        CallState.IN_CALL -> "Anruf aktiv"
                        CallState.ENDED -> "Anruf beendet"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (callState == CallState.IN_CALL) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                detectedNumber?.let {
                    Text("Nummer: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Manual number input (optional)
        OutlinedTextField(
            value = manualNumber,
            onValueChange = { manualNumber = it },
            label = { Text("Telefonnummer (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        if (!hasMicPermission) {
            Text("Mikrofon-Berechtigung noetig", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        // Recording controls
        if (!isRecording) {
            Button(
                onClick = { viewModel.startRecording(manualNumber.ifBlank { detectedNumber ?: "" }) },
                enabled = hasMicPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("Aufnahme starten") }
        } else {
            Text(
                "Aufnahme laeuft...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.stopRecording() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Aufnahme stoppen") }
        }
    }
}
```

- [ ] **Step 3: Commit**
```bash
git add android/
git commit -m "feat(android): recording control UI with PhoneStateListener auto-detect"
```

---

## Task 5: Update CLAUDE.md + Navigation Label

**Files:**
- Modify: `CLAUDE.md`
- Modify: `android/app/src/main/java/com/calltranscriber/ui/navigation/Screen.kt` (rename Dialer → Recorder)

- [ ] **Step 1: Update CLAUDE.md**
Replace SIP references with mic recorder approach.

- [ ] **Step 2: Update Screen.kt route name**
Change `Dialer` to `Recorder` in the sealed class (optional, cosmetic).

- [ ] **Step 3: Commit**
```bash
git add -A
git commit -m "docs: update CLAUDE.md — mic recorder instead of SIP"
```

---

## Dependencies
```
Task 1 (Remove SIP) → required by all
Task 2 (CallState + CallDetector) → required by Task 3, 4
Task 3 (Recording Service) → required by Task 4
Task 4 (ViewModel + Screen) → depends on 2, 3
Task 5 (Docs) → independent
```

Sequential: 1 → 2 → 3 → 4 → 5
