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
