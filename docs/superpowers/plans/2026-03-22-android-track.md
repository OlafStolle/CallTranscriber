# Android Track Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin Android app with Zadarma SIP calling, call recording, encrypted storage, WorkManager upload to FastAPI backend, and a Jetpack Compose UI showing call history with transcripts.

**Architecture:** Native Kotlin + Jetpack Compose. linphone-sdk for SIP/SRTP. Foreground Service for recording (linphone built-in, captures both sides). Room DB as local cache, Supabase Kotlin SDK for auth + cloud sync. WorkManager for reliable upload queue. Hilt for DI.

**Tech Stack:** Kotlin, Jetpack Compose, linphone-sdk 5.3.x, Supabase Kotlin SDK 3.x, Hilt, Room, WorkManager, AndroidX Security (EncryptedFile), Ktor HTTP client.

**Note:** Android builds require Android SDK. Steps that need `./gradlew assembleDebug` may not work in this environment. Focus on creating correct, complete files. Build verification happens on a machine with Android SDK.

---

## File Structure

```
android/
  build.gradle.kts                    # Root build file with plugin versions
  settings.gradle.kts                 # Project settings
  gradle.properties                   # JVM/Android properties
  app/
    build.gradle.kts                  # App module with all dependencies
    src/main/
      AndroidManifest.xml             # Permissions, services, activities
      java/com/calltranscriber/
        CallTranscriberApp.kt         # @HiltAndroidApp Application
        MainActivity.kt               # Single Activity host
        di/
          AppModule.kt                # Hilt DI bindings
        data/
          local/
            CallDatabase.kt           # Room database
            CallDao.kt                # Room DAO
            CallEntity.kt             # Room entity
          remote/
            SupabaseClient.kt         # Supabase SDK wrapper
            ApiClient.kt              # Ktor HTTP client for FastAPI
          repository/
            AuthRepository.kt         # Auth operations
            CallRepository.kt         # Single source of truth
        sip/
          SipConfig.kt                # SIP credentials data class
          SipManager.kt               # linphone-sdk wrapper
        service/
          CallRecordingService.kt     # Foreground Service for recording
          CallConnectionService.kt    # Telecom API ConnectionService
        upload/
          UploadWorker.kt             # WorkManager upload worker
        ui/
          auth/
            AuthViewModel.kt
            LoginScreen.kt
            RegisterScreen.kt
          calls/
            CallListViewModel.kt
            CallListScreen.kt
            CallDetailScreen.kt
          dialer/
            DialerViewModel.kt
            DialerScreen.kt
          navigation/
            Screen.kt
            NavGraph.kt
```

---

## Task 1: Gradle Project Setup

**Files:**
- Create: `android/build.gradle.kts`
- Create: `android/settings.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create root build.gradle.kts**

```kotlin
// android/build.gradle.kts
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
}
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
// android/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CallTranscriber"
include(":app")
```

- [ ] **Step 3: Create gradle.properties**

```properties
# android/gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create app/build.gradle.kts with all dependencies**

```kotlin
// android/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.calltranscriber"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.calltranscriber"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Supabase Kotlin SDK
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:3.0.2")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.53.1")
    kapt("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Ktor HTTP client
    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-android:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")

    // linphone SIP SDK
    implementation("org.linphone:linphone-sdk-android:5.3.74")
}
```

- [ ] **Step 5: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <application
        android:name=".CallTranscriberApp"
        android:label="Call Transcriber"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.CallConnectionService"
            android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.ConnectionService" />
            </intent-filter>
        </service>

        <service
            android:name=".service.CallRecordingService"
            android:foregroundServiceType="microphone"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): Gradle project setup with all dependencies"
```

---

## Task 2: Application + DI + Supabase Client

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/CallTranscriberApp.kt`
- Create: `android/app/src/main/java/com/calltranscriber/MainActivity.kt`
- Create: `android/app/src/main/java/com/calltranscriber/di/AppModule.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/remote/SupabaseClient.kt`

- [ ] **Step 1: Create Application class**

```kotlin
package com.calltranscriber

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CallTranscriberApp : Application()
```

- [ ] **Step 2: Create MainActivity**

```kotlin
package com.calltranscriber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.rememberNavController
import com.calltranscriber.ui.navigation.NavGraph
import com.calltranscriber.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    NavGraph(navController = navController, startDestination = Screen.Login.route)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create SupabaseClient wrapper**

```kotlin
package com.calltranscriber.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://your-project.supabase.co",
        supabaseKey = "your-anon-key",
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
```

- [ ] **Step 4: Create AppModule**

```kotlin
package com.calltranscriber.di

import android.content.Context
import androidx.room.Room
import com.calltranscriber.data.local.CallDatabase
import com.calltranscriber.data.local.CallDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCallDatabase(@ApplicationContext context: Context): CallDatabase {
        return Room.databaseBuilder(context, CallDatabase::class.java, "call_transcriber.db").build()
    }

    @Provides
    fun provideCallDao(db: CallDatabase): CallDao = db.callDao()
}
```

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat(android): Application, MainActivity, Hilt DI, Supabase client"
```

---

## Task 3: Auth Layer (Repository + ViewModel + UI)

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/data/repository/AuthRepository.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/auth/AuthViewModel.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/auth/LoginScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/auth/RegisterScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/navigation/Screen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Create AuthRepository**

```kotlin
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
```

- [ ] **Step 2: Create AuthViewModel**

```kotlin
package com.calltranscriber.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthState(isLoggedIn = authRepository.isLoggedIn))
    val state = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.signIn(email, password)
                _state.value = _state.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.signUp(email, password)
                _state.value = _state.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _state.value = AuthState(isLoggedIn = false)
        }
    }
}
```

- [ ] **Step 3: Create LoginScreen**

```kotlin
package com.calltranscriber.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onLoginSuccess() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Call Transcriber", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        Button(onClick = { viewModel.signIn(email, password) }, enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Anmelden")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNavigateToRegister) { Text("Noch kein Konto? Registrieren") }
    }
}
```

- [ ] **Step 4: Create RegisterScreen**

```kotlin
package com.calltranscriber.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onRegisterSuccess() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Konto erstellen", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Passwort bestaetigen") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        Button(onClick = { viewModel.signUp(email, password) }, enabled = !state.isLoading && email.isNotBlank() && password == confirmPassword && password.length >= 8, modifier = Modifier.fillMaxWidth()) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Registrieren")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNavigateToLogin) { Text("Bereits ein Konto? Anmelden") }
    }
}
```

- [ ] **Step 5: Create Navigation (Screen + NavGraph)**

```kotlin
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
```

```kotlin
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
```

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): auth layer with login, register, navigation"
```

---

## Task 4: Room Database + CallRepository

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/data/local/CallEntity.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/local/CallDao.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/local/CallDatabase.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/repository/CallRepository.kt`

- [ ] **Step 1: Create CallEntity**

```kotlin
package com.calltranscriber.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val id: String,
    val remoteNumber: String,
    val direction: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Int? = null,
    val status: String,
    val transcriptText: String? = null,
    val audioFilePath: String? = null,
    val syncedToCloud: Boolean = false,
)
```

- [ ] **Step 2: Create CallDao**

```kotlin
package com.calltranscriber.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startedAt DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): CallEntity?

    @Query("SELECT * FROM calls WHERE transcriptText LIKE '%' || :query || '%' ORDER BY startedAt DESC")
    fun searchCalls(query: String): Flow<List<CallEntity>>

    @Upsert
    suspend fun upsertCall(call: CallEntity)

    @Upsert
    suspend fun upsertCalls(calls: List<CallEntity>)

    @Query("UPDATE calls SET transcriptText = :text WHERE id = :callId")
    suspend fun updateTranscript(callId: String, text: String)

    @Query("UPDATE calls SET syncedToCloud = 1 WHERE id = :callId")
    suspend fun markSynced(callId: String)
}
```

- [ ] **Step 3: Create CallDatabase**

```kotlin
package com.calltranscriber.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CallEntity::class], version = 1)
abstract class CallDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
}
```

- [ ] **Step 4: Create CallRepository**

```kotlin
package com.calltranscriber.data.repository

import com.calltranscriber.data.local.CallDao
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CallRow(val id: String, val user_id: String, val remote_number: String, val direction: String, val started_at: String, val ended_at: String? = null, val duration_seconds: Int? = null, val status: String)

@Serializable
data class TranscriptRow(val id: String, val call_id: String, val text: String, val language: String)

@Singleton
class CallRepository @Inject constructor(private val callDao: CallDao) {
    private val supabase = SupabaseClientProvider.client

    fun getAllCalls(): Flow<List<CallEntity>> = callDao.getAllCalls()
    fun searchCalls(query: String): Flow<List<CallEntity>> = callDao.searchCalls(query)
    suspend fun getCallById(callId: String): CallEntity? = callDao.getCallById(callId)

    suspend fun syncFromCloud() {
        val calls = supabase.postgrest["calls"].select().decodeList<CallRow>()
        val entities = calls.map { call ->
            val transcripts = supabase.postgrest["transcripts"].select { filter { eq("call_id", call.id) } }.decodeList<TranscriptRow>()
            CallEntity(id = call.id, remoteNumber = call.remote_number, direction = call.direction, startedAt = parseTimestamp(call.started_at), endedAt = call.ended_at?.let { parseTimestamp(it) }, durationSeconds = call.duration_seconds, status = call.status, transcriptText = transcripts.firstOrNull()?.text, syncedToCloud = true)
        }
        callDao.upsertCalls(entities)
    }

    suspend fun saveLocalCall(call: CallEntity) { callDao.upsertCall(call) }

    private fun parseTimestamp(ts: String): Long = try { java.time.Instant.parse(ts).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
}
```

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat(android): Room DB, CallDao, CallRepository with cloud sync"
```

---

## Task 5: SIP Client + ConnectionService

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/sip/SipConfig.kt`
- Create: `android/app/src/main/java/com/calltranscriber/sip/SipManager.kt`
- Create: `android/app/src/main/java/com/calltranscriber/service/CallConnectionService.kt`

- [ ] **Step 1: Create SipConfig**

```kotlin
package com.calltranscriber.sip

data class SipConfig(
    val domain: String = "sip.zadarma.com",
    val port: Int = 5060,
    val transport: String = "TLS",
    val username: String = "",
    val password: String = "",
    val useSrtp: Boolean = true,
)
```

- [ ] **Step 2: Create SipManager**

```kotlin
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
```

- [ ] **Step 3: Create CallConnectionService**

```kotlin
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
```

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "feat(android): SIP client with linphone-sdk, SRTP, ConnectionService"
```

---

## Task 6: Recording Service + Encrypted Storage + Upload Worker

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/remote/ApiClient.kt`
- Create: `android/app/src/main/java/com/calltranscriber/upload/UploadWorker.kt`

- [ ] **Step 1: Create CallRecordingService (linphone built-in recording — both sides)**

```kotlin
package com.calltranscriber.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.calltranscriber.sip.SipManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class CallRecordingService : Service() {
    @Inject lateinit var sipManager: SipManager
    companion object { const val CHANNEL_ID = "call_recording"; const val NOTIFICATION_ID = 1; const val ACTION_START = "START_RECORDING"; const val ACTION_STOP = "STOP_RECORDING"; const val EXTRA_CALL_ID = "call_id" }
    private var currentCallId: String? = null

    override fun onCreate() { super.onCreate(); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> { val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return START_NOT_STICKY; currentCallId = callId; startForeground(NOTIFICATION_ID, createNotification()); startRecording(callId) }
            ACTION_STOP -> { stopRecording(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(callId: String) {
        val recordFile = File(filesDir, "recordings/$callId.wav"); recordFile.parentFile?.mkdirs()
        val call = sipManager.currentCall ?: return
        val params = sipManager.getCore().createCallParams(call)
        params?.recordFile = recordFile.absolutePath; call.update(params); call.startRecording()
    }

    private fun stopRecording() {
        sipManager.currentCall?.stopRecording()
        val callId = currentCallId ?: return
        val plainFile = File(filesDir, "recordings/$callId.wav")
        if (plainFile.exists()) encryptFile(plainFile, callId)
        currentCallId = null
    }

    private fun encryptFile(sourceFile: File, callId: String) {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encFile = File(filesDir, "recordings_encrypted/$callId.wav.enc"); encFile.parentFile?.mkdirs()
        val ef = EncryptedFile.Builder(this, encFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        ef.openFileOutput().use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
        sourceFile.delete()
    }

    private fun createNotificationChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Gespraech wird aufgezeichnet", NotificationManager.IMPORTANCE_LOW)) }
    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Gespraech wird aufgezeichnet").setContentText("Aufnahme laeuft...").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: Create ApiClient**

```kotlin
package com.calltranscriber.data.remote

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

class ApiClient(private val baseUrl: String = "https://transcriber-api.yourdomain.com") {
    private val client = HttpClient(Android)

    suspend fun uploadAudio(token: String, callId: String, audioFile: File, remoteNumber: String, direction: String, startedAt: String, endedAt: String, durationSeconds: Int): HttpResponse {
        return client.submitFormWithBinaryData(url = "$baseUrl/upload", formData = formData {
            append("remote_number", remoteNumber); append("direction", direction); append("started_at", startedAt); append("ended_at", endedAt); append("duration_seconds", durationSeconds.toString())
            append("audio", audioFile.readBytes(), Headers.build { append(HttpHeaders.ContentType, "audio/wav"); append(HttpHeaders.ContentDisposition, "filename=\"$callId.wav\"") })
        }) { header(HttpHeaders.Authorization, "Bearer $token") }
    }
}
```

- [ ] **Step 3: Create UploadWorker**

```kotlin
package com.calltranscriber.upload

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.work.*
import com.calltranscriber.data.remote.ApiClient
import com.calltranscriber.data.repository.AuthRepository
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val callId = inputData.getString("call_id") ?: return Result.failure()
        val remoteNumber = inputData.getString("remote_number") ?: return Result.failure()
        val direction = inputData.getString("direction") ?: return Result.failure()
        val startedAt = inputData.getString("started_at") ?: return Result.failure()
        val endedAt = inputData.getString("ended_at") ?: return Result.failure()
        val durationSeconds = inputData.getInt("duration_seconds", 0)

        val encryptedFilePath = File(applicationContext.filesDir, "recordings_encrypted/$callId.wav.enc")
        if (!encryptedFilePath.exists()) return Result.failure()

        val masterKey = MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val ef = EncryptedFile.Builder(applicationContext, encryptedFilePath, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        val tempFile = File.createTempFile("upload_", ".wav", applicationContext.cacheDir)
        try {
            ef.openFileInput().use { input -> FileOutputStream(tempFile).use { output -> input.copyTo(output) } }
            val token = AuthRepository().getAccessToken() ?: return Result.retry()
            val response = ApiClient().uploadAudio(token, callId, tempFile, remoteNumber, direction, startedAt, endedAt, durationSeconds)
            return if (response.status.value in 200..299) Result.success() else Result.retry()
        } finally { tempFile.delete() }
    }

    companion object {
        fun enqueue(context: Context, callId: String, remoteNumber: String, direction: String, startedAt: String, endedAt: String, durationSeconds: Int) {
            val data = workDataOf("call_id" to callId, "remote_number" to remoteNumber, "direction" to direction, "started_at" to startedAt, "ended_at" to endedAt, "duration_seconds" to durationSeconds)
            val request = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(data).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("upload_$callId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "feat(android): recording service, encrypted storage, upload worker"
```

---

## Task 7: Call List + Detail + Dialer UI

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/ui/calls/CallListViewModel.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/calls/CallListScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/calls/CallDetailScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerViewModel.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerScreen.kt`

- [ ] **Step 1: Create CallListViewModel**

```kotlin
package com.calltranscriber.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallListViewModel @Inject constructor(private val callRepository: CallRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    val calls: StateFlow<List<CallEntity>> = _searchQuery.debounce(300).flatMapLatest { q -> if (q.isBlank()) callRepository.getAllCalls() else callRepository.searchCalls(q) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { try { callRepository.syncFromCloud() } catch (_: Exception) {} } }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
}
```

- [ ] **Step 2: Create CallListScreen**

```kotlin
package com.calltranscriber.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.data.local.CallEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallListScreen(onCallClick: (String) -> Unit, onDialerClick: () -> Unit, viewModel: CallListViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Gespraeche") }) }, floatingActionButton = { FloatingActionButton(onClick = onDialerClick) { Icon(Icons.Default.Phone, "Anrufen") } }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(value = searchQuery, onValueChange = viewModel::onSearchQueryChanged, label = { Text("Suchen...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true)
            if (calls.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Gespraeche") } }
            else { LazyColumn { items(calls, key = { it.id }) { call -> CallCard(call) { onCallClick(call.id) } } } }
        }
    }
}

@Composable
fun CallCard(call: CallEntity, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(call.remoteNumber, style = MaterialTheme.typography.titleMedium); Text(if (call.direction == "inbound") "Eingehend" else "Ausgehend", style = MaterialTheme.typography.labelMedium) }
            Text(fmt.format(Date(call.startedAt)), style = MaterialTheme.typography.bodySmall)
            call.durationSeconds?.let { Text("${it / 60}:${"%02d".format(it % 60)} Min", style = MaterialTheme.typography.bodySmall) }
            call.transcriptText?.let { Text(it.take(120) + if (it.length > 120) "..." else "", style = MaterialTheme.typography.bodySmall, maxLines = 2) } ?: Text(call.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
```

- [ ] **Step 3: Create CallDetailScreen**

```kotlin
package com.calltranscriber.ui.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CallDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val callRepository: CallRepository) : ViewModel() {
    private val callId: String = savedStateHandle["callId"] ?: ""
    private val _call = MutableStateFlow<CallEntity?>(null)
    val call = _call.asStateFlow()
    init { viewModelScope.launch { _call.value = callRepository.getCallById(callId) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(onBack: () -> Unit, viewModel: CallDetailViewModel = hiltViewModel()) {
    val call by viewModel.call.collectAsState()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Scaffold(topBar = { TopAppBar(title = { Text(call?.remoteNumber ?: "Gespraech") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck") } }) }) { padding ->
        call?.let { c ->
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Nummer: ${c.remoteNumber}", style = MaterialTheme.typography.titleMedium)
                Text("Richtung: ${if (c.direction == "inbound") "Eingehend" else "Ausgehend"}")
                Text("Datum: ${fmt.format(Date(c.startedAt))}")
                c.durationSeconds?.let { Text("Dauer: ${it / 60}:${"%02d".format(it % 60)} Min") }
                Text("Status: ${c.status}")
                Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))
                Text("Transkript", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp))
                c.transcriptText?.let { Text(it) } ?: Text("Transkript wird erstellt...", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
```

- [ ] **Step 4: Create DialerViewModel + DialerScreen**

```kotlin
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
```

```kotlin
// DialerScreen.kt
package com.calltranscriber.ui.dialer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.sip.CallState

@Composable
fun DialerScreen(viewModel: DialerViewModel = hiltViewModel()) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val callState by viewModel.callState.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Anrufen", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = phoneNumber, onValueChange = viewModel::onNumberChanged, label = { Text("Telefonnummer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        when (callState) {
            CallState.IDLE, CallState.ENDED -> Button(onClick = { viewModel.makeCall() }, enabled = phoneNumber.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Anrufen") }
            CallState.RINGING -> { Text("Verbindung wird aufgebaut..."); Spacer(Modifier.height(8.dp)); Button(onClick = { viewModel.hangUp() }, modifier = Modifier.fillMaxWidth()) { Text("Auflegen") } }
            CallState.CONNECTED -> { Text("Gespraech laeuft — wird aufgezeichnet", color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Button(onClick = { viewModel.hangUp() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Auflegen") } }
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat(android): call list, detail, dialer UI with search"
```

---

## Dependencies

```
Task 1 (Gradle) → required by all
Task 2 (App + DI) → required by 3, 4, 5, 6, 7
Task 3 (Auth) → required by 7 (NavGraph references all screens)
Task 4 (Room + Repository) → required by 6, 7
Task 5 (SIP) → required by 6, 7
Task 6 (Recording + Upload) → required by 7
Task 7 (UI) → depends on all
```

**Sequential execution required:** Tasks 1 → 2 → 3 → 4 → 5 → 6 → 7
