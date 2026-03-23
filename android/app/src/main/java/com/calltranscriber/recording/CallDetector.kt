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
