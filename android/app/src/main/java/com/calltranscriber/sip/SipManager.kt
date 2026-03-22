package com.calltranscriber.sip

import android.content.Context
import org.linphone.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SipRegistrationState { NONE, REGISTERING, REGISTERED, FAILED }
enum class CallState { IDLE, RINGING, CONNECTED, ENDED }

@Singleton
class SipManager @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {
    private lateinit var core: Core
    private val _registrationState = MutableStateFlow(SipRegistrationState.NONE)
    val registrationState = _registrationState.asStateFlow()
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()
    var currentCall: Call? = null; private set

    fun initialize(config: SipConfig) {
        val factory = Factory.instance()
        core = factory.createCore(null, null, context)
        if (config.useSrtp) { core.mediaEncryption = MediaEncryption.SRTP; core.isMediaEncryptionMandatory = true }
        val authInfo = factory.createAuthInfo(config.username, null, config.password, null, null, config.domain)
        core.addAuthInfo(authInfo)
        val accountParams = core.createAccountParams()
        accountParams.identityAddress = factory.createAddress("sip:${config.username}@${config.domain}")
        val serverAddress = factory.createAddress("sip:${config.domain}:${config.port}")
        serverAddress?.transport = when (config.transport) { "TLS" -> TransportType.Tls; "TCP" -> TransportType.Tcp; else -> TransportType.Udp }
        accountParams.serverAddress = serverAddress; accountParams.isRegisterEnabled = true
        val account = core.createAccount(accountParams); core.addAccount(account); core.defaultAccount = account
        core.addListener(object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState, message: String) {
                _registrationState.value = when (state) { RegistrationState.Progress -> SipRegistrationState.REGISTERING; RegistrationState.Ok -> SipRegistrationState.REGISTERED; RegistrationState.Failed -> SipRegistrationState.FAILED; else -> SipRegistrationState.NONE }
            }
            override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                currentCall = call
                _callState.value = when (state) { Call.State.IncomingReceived, Call.State.OutgoingRinging -> CallState.RINGING; Call.State.StreamsRunning -> CallState.CONNECTED; Call.State.End, Call.State.Released, Call.State.Error -> { currentCall = null; CallState.ENDED }; else -> _callState.value }
            }
        })
        core.start()
    }

    fun makeCall(number: String): Call? { val addr = core.interpretUrl("sip:$number@${core.defaultAccount?.params?.domain}") ?: return null; return core.inviteAddress(addr) }
    fun answerCall() { currentCall?.accept() }
    fun hangUp() { currentCall?.terminate() }
    fun getCore(): Core = core
    fun stop() { core.stop() }
}
