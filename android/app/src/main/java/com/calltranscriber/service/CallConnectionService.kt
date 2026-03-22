package com.calltranscriber.service

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class CallConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?): Connection {
        return CallConnection().apply { setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED); setActive() }
    }
    override fun onCreateIncomingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?): Connection {
        return CallConnection().apply { setAddress(request?.extras?.getParcelable("android.telecom.extra.INCOMING_CALL_ADDRESS") as? Uri, TelecomManager.PRESENTATION_ALLOWED); setRinging() }
    }
    inner class CallConnection : Connection() {
        init { connectionProperties = PROPERTY_SELF_MANAGED; audioModeIsVoip = true }
        override fun onAnswer() { setActive() }
        override fun onDisconnect() { setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); destroy() }
        override fun onAbort() { setDisconnected(DisconnectCause(DisconnectCause.CANCELED)); destroy() }
    }
}
