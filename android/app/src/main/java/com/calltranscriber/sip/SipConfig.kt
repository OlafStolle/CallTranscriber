package com.calltranscriber.sip

data class SipConfig(
    val domain: String = "sip.zadarma.com",
    val port: Int = 5060,
    val transport: String = "TLS",
    val username: String = "",
    val password: String = "",
    val useSrtp: Boolean = true,
)
