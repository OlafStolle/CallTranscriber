package com.calltranscriber.data.repository

import com.calltranscriber.data.remote.SupabaseClientProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    private val auth = SupabaseClientProvider.client.auth

    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    val isLoggedIn: Boolean
        get() = auth.currentUserOrNull() != null

    suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) { this.email = email; this.password = password }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) { this.email = email; this.password = password }
    }

    suspend fun signOut() { auth.signOut() }

    suspend fun getAccessToken(): String? = auth.currentAccessTokenOrNull()
}
