package com.calltranscriber.sip

import com.calltranscriber.BuildConfig

data class SipConfig(
    val domain: String = BuildConfig.SIP_DOMAIN,
    val port: Int = 5060,
    val transport: String = "TLS",
    val username: String = BuildConfig.SIP_USERNAME,
    val password: String = BuildConfig.SIP_PASSWORD,
    val useSrtp: Boolean = true,
)
