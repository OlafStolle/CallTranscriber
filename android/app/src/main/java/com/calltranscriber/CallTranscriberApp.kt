package com.calltranscriber

import android.app.Application
import com.calltranscriber.sip.SipConfig
import com.calltranscriber.sip.SipManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CallTranscriberApp : Application() {

    @Inject lateinit var sipManager: SipManager

    override fun onCreate() {
        super.onCreate()
        // Initialize SIP on app start
        val config = SipConfig()
        if (config.username.isNotBlank()) {
            sipManager.initialize(config)
            android.util.Log.i("CallTranscriberApp", "SIP initialized for ${config.username}@${config.domain}")
        } else {
            android.util.Log.w("CallTranscriberApp", "SIP credentials not configured — skipping initialization")
        }
    }
}
