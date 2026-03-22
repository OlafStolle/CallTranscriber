// DialerViewModel.kt
package com.calltranscriber.ui.dialer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import com.calltranscriber.service.CallRecordingService
import com.calltranscriber.sip.CallState
import com.calltranscriber.sip.SipManager
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
class DialerViewModel @Inject constructor(private val sipManager: SipManager, private val callRepository: CallRepository, @ApplicationContext private val context: Context) : ViewModel() {
    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()
    val callState = sipManager.callState
    private var currentCallId: String? = null
    private var callStartTime: Instant? = null

    fun onNumberChanged(number: String) { _phoneNumber.value = number }

    fun makeCall() {
        val number = _phoneNumber.value; if (number.isBlank()) return
        currentCallId = UUID.randomUUID().toString(); callStartTime = Instant.now()
        sipManager.makeCall(number)
        context.startForegroundService(Intent(context, CallRecordingService::class.java).apply { action = CallRecordingService.ACTION_START; putExtra(CallRecordingService.EXTRA_CALL_ID, currentCallId) })
        viewModelScope.launch { callRepository.saveLocalCall(CallEntity(id = currentCallId!!, remoteNumber = number, direction = "outbound", startedAt = callStartTime!!.toEpochMilli(), status = "recording")) }
    }

    fun hangUp() {
        sipManager.hangUp()
        context.startService(Intent(context, CallRecordingService::class.java).apply { action = CallRecordingService.ACTION_STOP })
        val callId = currentCallId ?: return; val start = callStartTime ?: return; val end = Instant.now()
        val dur = (end.epochSecond - start.epochSecond).toInt()
        viewModelScope.launch {
            callRepository.saveLocalCall(CallEntity(id = callId, remoteNumber = _phoneNumber.value, direction = "outbound", startedAt = start.toEpochMilli(), endedAt = end.toEpochMilli(), durationSeconds = dur, status = "uploading"))
            UploadWorker.enqueue(context, callId, _phoneNumber.value, "outbound", start.toString(), end.toString(), dur)
        }
        currentCallId = null; callStartTime = null
    }
}
