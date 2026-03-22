// Screen.kt
package com.calltranscriber.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object CallList : Screen("calls")
    data object CallDetail : Screen("calls/{callId}") {
        fun createRoute(callId: String) = "calls/$callId"
    }
    data object Dialer : Screen("dialer")
}
