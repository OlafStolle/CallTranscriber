// NavGraph.kt
package com.calltranscriber.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.calltranscriber.ui.auth.LoginScreen
import com.calltranscriber.ui.auth.RegisterScreen
import com.calltranscriber.ui.calls.CallDetailScreen
import com.calltranscriber.ui.calls.CallListScreen
import com.calltranscriber.ui.dialer.DialerScreen

@Composable
fun NavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = { navController.navigate(Screen.CallList.route) { popUpTo(Screen.Login.route) { inclusive = true } } },
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = { navController.navigate(Screen.CallList.route) { popUpTo(Screen.Register.route) { inclusive = true } } },
            )
        }
        composable(Screen.CallList.route) {
            CallListScreen(
                onCallClick = { callId -> navController.navigate(Screen.CallDetail.createRoute(callId)) },
                onDialerClick = { navController.navigate(Screen.Dialer.route) },
            )
        }
        composable(Screen.CallDetail.route, arguments = listOf(navArgument("callId") { type = NavType.StringType })) {
            CallDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Dialer.route) {
            DialerScreen()
        }
    }
}
