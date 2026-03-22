# Android Phase 2: Integration, Verification & Release

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take the Phase 1 Android codebase from "files exist" to "working app on a real device" — connect to real services, verify all flows end-to-end, polish UI, build APK.

**Architecture:** No new architecture. Modify existing files to use BuildConfig for credentials, add runtime permission handling, improve error states, and prepare for release build.

**Tech Stack:** Same as Phase 1. No new dependencies except `accompanist-permissions` (optional) and `core-splashscreen`.

**Reihenfolge:** Task 1 (Build) → Task 2 (Credentials via BuildConfig) → Task 3 (SIP) → Task 4 (Cloud-Sync) → Task 5 (UI Polish) → Task 6 (Release APK)

**Hinweis:** Tasks 3-4 erfordern ein Android-Geraet/Emulator + echte Service-Credentials. Schritte mit `[MANUELL]` koennen nur auf einem Geraet verifiziert werden.

---

## Files to Modify

```
android/
  app/
    build.gradle.kts                  # BuildConfig fields, signingConfigs, proguard
    proguard-rules.pro                # CREATE: R8 rules for linphone, supabase, ktor
    src/main/
      AndroidManifest.xml             # Backup rules
      java/com/calltranscriber/
        MainActivity.kt               # Permission handling, splash, theme
        data/remote/
          SupabaseClient.kt           # BuildConfig.SUPABASE_URL/KEY
          ApiClient.kt                # BuildConfig.API_BASE_URL
        sip/SipConfig.kt              # BuildConfig.SIP_USER/PASS
        service/
          CallRecordingService.kt     # Recording announcement audio
        ui/
          theme/
            Theme.kt                  # CREATE: Custom Material3 theme
            Color.kt                  # CREATE: Color palette
          calls/
            CallListScreen.kt         # Pull-to-refresh, empty state, loading
            CallListViewModel.kt      # Refresh trigger
          dialer/
            DialerScreen.kt           # Permission check before call
      res/
        values/strings.xml            # CREATE: German string resources
        raw/recording_announcement.mp3 # CREATE: "Gespraech wird aufgezeichnet" audio
        mipmap-hdpi/ic_launcher.webp  # App icon (placeholder)
```

---

## Task 1: First Successful Build

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/gradlew` + `android/gradlew.bat`

- [ ] **Step 1: Add Gradle Wrapper**

```bash
cd /mnt/volume/Projects/call-transcriber/android
# If gradle is available:
gradle wrapper --gradle-version 8.11
# If not, create manually:
```

```properties
# android/gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Add linphone Maven repository**

Modify `android/settings.gradle.kts` — add linphone Maven to dependencyResolutionManagement:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://linphone.org/maven_repository") }
    }
}
```

- [ ] **Step 3: Run first build**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL or specific compile errors to fix.

- [ ] **Step 4: Fix any compile errors**

Common issues:
- linphone-sdk not found → check Maven URL
- Supabase API changes → check import paths
- Kotlin version mismatch → align plugin versions
- Missing `kotlinOptions` block → verify build.gradle.kts

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "build(android): Gradle wrapper + first successful build"
```

---

## Task 2: BuildConfig Credentials (keine Hardcoded Strings)

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/com/calltranscriber/data/remote/SupabaseClient.kt`
- Modify: `android/app/src/main/java/com/calltranscriber/data/remote/ApiClient.kt`
- Modify: `android/app/src/main/java/com/calltranscriber/sip/SipConfig.kt`
- Create: `android/local.properties.example`

- [ ] **Step 1: Add BuildConfig fields to build.gradle.kts**

Add inside `android { defaultConfig { ... } }`:

```kotlin
android {
    // ... existing config ...
    defaultConfig {
        // ... existing config ...

        // Supabase
        buildConfigField("String", "SUPABASE_URL", "\"${project.findProperty("SUPABASE_URL") ?: "https://your-project.supabase.co"}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY") ?: "your-anon-key"}\"")

        // FastAPI Backend
        buildConfigField("String", "API_BASE_URL", "\"${project.findProperty("API_BASE_URL") ?: "https://transcriber-api.yourdomain.com"}\"")

        // Zadarma SIP
        buildConfigField("String", "SIP_USERNAME", "\"${project.findProperty("SIP_USERNAME") ?: ""}\"")
        buildConfigField("String", "SIP_PASSWORD", "\"${project.findProperty("SIP_PASSWORD") ?: ""}\"")
        buildConfigField("String", "SIP_DOMAIN", "\"${project.findProperty("SIP_DOMAIN") ?: "sip.zadarma.com"}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

- [ ] **Step 2: Update SupabaseClient.kt**

```kotlin
package com.calltranscriber.data.remote

import com.calltranscriber.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
```

- [ ] **Step 3: Update ApiClient.kt**

```kotlin
package com.calltranscriber.data.remote

import com.calltranscriber.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

class ApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    private val client = HttpClient(Android)

    suspend fun uploadAudio(token: String, callId: String, audioFile: File, remoteNumber: String, direction: String, startedAt: String, endedAt: String, durationSeconds: Int): HttpResponse {
        return client.submitFormWithBinaryData(url = "$baseUrl/upload", formData = formData {
            append("remote_number", remoteNumber); append("direction", direction); append("started_at", startedAt); append("ended_at", endedAt); append("duration_seconds", durationSeconds.toString())
            append("audio", audioFile.readBytes(), Headers.build { append(HttpHeaders.ContentType, "audio/wav"); append(HttpHeaders.ContentDisposition, "filename=\"$callId.wav\"") })
        }) { header(HttpHeaders.Authorization, "Bearer $token") }
    }
}
```

- [ ] **Step 4: Update SipConfig.kt**

```kotlin
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
```

- [ ] **Step 5: Create local.properties.example**

```properties
# android/local.properties.example
# Copy to local.properties and fill in real values
# DO NOT commit local.properties!

sdk.dir=/path/to/android/sdk

# Supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJ...your-anon-key

# FastAPI Backend
API_BASE_URL=https://transcriber-api.yourdomain.com

# Zadarma SIP (from Zadarma Dashboard → My PBX → SIP Accounts)
SIP_USERNAME=100001
SIP_PASSWORD=your-sip-password
SIP_DOMAIN=sip.zadarma.com
```

- [ ] **Step 6: Verify .gitignore excludes local.properties**

Check `android/` is in root `.gitignore` with `android/local.properties`.

- [ ] **Step 7: Build to verify BuildConfig**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleDebug
```

- [ ] **Step 8: Commit**

```bash
git add android/
git commit -m "feat(android): BuildConfig for all credentials, no more hardcoded strings"
```

---

## Task 3: SIP Integration Test (Zadarma)

**Files:**
- Modify: `android/app/src/main/java/com/calltranscriber/sip/SipManager.kt` (add logging)
- Modify: `android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt` (add announcement)
- Create: `android/app/src/main/res/raw/recording_announcement.mp3`
- Create: `android/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add SIP registration logging to SipManager**

Add after `core.start()` in `initialize()`:

```kotlin
android.util.Log.i("SipManager", "SIP core started, registering to ${config.domain}")
```

And in the registration listener:

```kotlin
android.util.Log.i("SipManager", "SIP registration: $state - $message")
```

- [ ] **Step 2: Create strings.xml**

```xml
<!-- android/app/src/main/res/values/strings.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Call Transcriber</string>
    <string name="recording_notice">Dieses Gespraech wird aufgezeichnet</string>
    <string name="recording_active">Aufnahme laeuft</string>
    <string name="no_calls">Noch keine Gespraeche</string>
    <string name="search_hint">Transkripte durchsuchen...</string>
    <string name="login_title">Call Transcriber</string>
    <string name="register_title">Konto erstellen</string>
</resources>
```

- [ ] **Step 3: Add recording announcement to CallRecordingService**

Modify `startRecording()` — play announcement before starting linphone recording:

```kotlin
private fun startRecording(callId: String) {
    // Play recording announcement (§201 StGB)
    val call = sipManager.currentCall ?: return
    val core = sipManager.getCore()

    // Play announcement file if it exists
    val announcementPath = "${filesDir}/announcement.wav"
    val rawRes = resources.openRawResource(R.raw.recording_announcement)
    File(announcementPath).outputStream().use { rawRes.copyTo(it) }
    core.playLocal(announcementPath)

    // Wait 2 seconds for announcement, then start recording
    android.os.Handler(mainLooper).postDelayed({
        val recordFile = File(filesDir, "recordings/$callId.wav")
        recordFile.parentFile?.mkdirs()
        val params = core.createCallParams(call)
        params?.recordFile = recordFile.absolutePath
        call.update(params)
        call.startRecording()
    }, 2000)
}
```

- [ ] **Step 4: Create placeholder announcement audio**

`[MANUELL]` Record a short WAV/MP3 saying "Dieses Gespraech wird aufgezeichnet" and place at `android/app/src/main/res/raw/recording_announcement.mp3`. For now create an empty placeholder.

- [ ] **Step 5: [MANUELL] Test SIP Registration**

On device with real Zadarma credentials in `local.properties`:
1. Launch app → Login → Dialer
2. Check Logcat for: `SIP registration: Ok`
3. If FAILED: check credentials, domain, port, network

- [ ] **Step 6: [MANUELL] Test Outgoing Call**

1. Enter a real phone number in Dialer
2. Press "Anrufen"
3. Verify: call connects, audio works both ways
4. Hang up, check `filesDir/recordings_encrypted/` for encrypted WAV

- [ ] **Step 7: [MANUELL] Verify Recording Quality**

1. Decrypt recording (adb pull + decrypt)
2. Play WAV — both sides audible?
3. If only one side: check linphone `call.startRecording()` config

- [ ] **Step 8: Commit**

```bash
git add android/
git commit -m "feat(android): SIP logging, recording announcement, strings"
```

---

## Task 4: Supabase Cloud-Sync End-to-End

**Files:**
- No new files. Testing existing upload + sync flow.

- [ ] **Step 1: [MANUELL] Verify Supabase Project Setup**

1. Supabase Dashboard: create project (if not done)
2. SQL Editor: run all migrations 001-007
3. Storage: verify `call-recordings` bucket exists
4. Settings → API: copy URL + anon key to `local.properties`

- [ ] **Step 2: [MANUELL] Verify FastAPI Backend Running**

```bash
curl https://transcriber-api.yourdomain.com/health
# Expected: {"status": "ok"}
```

- [ ] **Step 3: [MANUELL] Test Auth Flow**

1. App: Register with email/password
2. Supabase Dashboard → Authentication: verify user created
3. App: Logout → Login → verify access

- [ ] **Step 4: [MANUELL] Test Upload Flow**

1. Make a test call (Task 3)
2. Hang up → Watch Logcat for WorkManager
3. Check Supabase Dashboard:
   - `calls` table: new row with status progression (uploading → transcribing → completed)
   - `transcripts` table: text + segments
   - `audio_files` table: storage_path
   - Storage bucket: audio file present

- [ ] **Step 5: [MANUELL] Test Cloud Sync to App**

1. Kill and relaunch app
2. Call list should show the call from step 4
3. Tap call → transcript visible
4. Search → find call by transcript text

- [ ] **Step 6: [MANUELL] Test Web Dashboard**

1. Open https://transcriber.yourdomain.com
2. Login with same email/password
3. Same call + transcript visible
4. Export works (TXT/CSV)

- [ ] **Step 7: [MANUELL] Test Offline Resilience**

1. Enable airplane mode
2. Make note of pending calls in Room DB
3. Disable airplane mode
4. WorkManager should retry upload automatically
5. Verify call appears in Supabase after reconnect

- [ ] **Step 8: Document results**

```bash
# Add test results to docs
echo "## E2E Test Results $(date +%Y-%m-%d)" >> docs/ANDROID-PHASE2-PLAN.md
```

---

## Task 5: UI Polish

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/ui/theme/Color.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/theme/Theme.kt`
- Modify: `android/app/src/main/java/com/calltranscriber/MainActivity.kt`
- Modify: `android/app/src/main/java/com/calltranscriber/ui/calls/CallListScreen.kt`
- Modify: `android/app/src/main/java/com/calltranscriber/ui/calls/CallListViewModel.kt`
- Modify: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerScreen.kt`

- [ ] **Step 1: Create Color.kt**

```kotlin
package com.calltranscriber.ui.theme

import androidx.compose.ui.graphics.Color

val Blue600 = Color(0xFF1E88E5)
val Blue700 = Color(0xFF1565C0)
val Blue100 = Color(0xFFBBDEFB)
val Green600 = Color(0xFF43A047)
val Red600 = Color(0xFFE53935)
val Gray50 = Color(0xFFFAFAFA)
val Gray200 = Color(0xFFEEEEEE)
val Gray600 = Color(0xFF757575)
```

- [ ] **Step 2: Create Theme.kt**

```kotlin
package com.calltranscriber.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue100,
    secondary = Green600,
    error = Red600,
    background = Gray50,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = Gray200,
    onSurfaceVariant = Gray600,
)

@Composable
fun CallTranscriberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else LightColors,
        content = content,
    )
}
```

- [ ] **Step 3: Update MainActivity with theme + permissions**

```kotlin
package com.calltranscriber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.calltranscriber.ui.navigation.NavGraph
import com.calltranscriber.ui.navigation.Screen
import com.calltranscriber.ui.theme.CallTranscriberTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        setContent {
            CallTranscriberTheme {
                Surface {
                    val navController = rememberNavController()
                    NavGraph(navController = navController, startDestination = Screen.Login.route)
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
```

- [ ] **Step 4: Add pull-to-refresh + loading state to CallListScreen**

Add to CallListViewModel:

```kotlin
private val _isRefreshing = MutableStateFlow(false)
val isRefreshing = _isRefreshing.asStateFlow()

fun refresh() {
    viewModelScope.launch {
        _isRefreshing.value = true
        try { callRepository.syncFromCloud() } catch (_: Exception) {}
        _isRefreshing.value = false
    }
}
```

Update CallListScreen — wrap LazyColumn with pull-to-refresh:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallListScreen(onCallClick: (String) -> Unit, onDialerClick: () -> Unit, viewModel: CallListViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gespraeche") }) },
        floatingActionButton = { FloatingActionButton(onClick = onDialerClick) { Icon(Icons.Default.Phone, "Anrufen") } },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.padding(padding),
        ) {
            Column {
                OutlinedTextField(value = searchQuery, onValueChange = viewModel::onSearchQueryChanged, label = { Text("Suchen...") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true)
                if (calls.isEmpty() && !isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Noch keine Gespraeche", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn { items(calls, key = { it.id }) { call -> CallCard(call) { onCallClick(call.id) } } }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Add permission check to DialerScreen before calling**

In DialerViewModel.makeCall(), add at the beginning:

```kotlin
// Caller must check RECORD_AUDIO permission before calling makeCall()
```

In DialerScreen, wrap the Anrufen button:

```kotlin
val context = LocalContext.current
val hasRecordPermission = remember {
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

// In the button:
Button(
    onClick = { if (hasRecordPermission) viewModel.makeCall() },
    enabled = phoneNumber.isNotBlank() && hasRecordPermission,
    modifier = Modifier.fillMaxWidth(),
) { Text(if (hasRecordPermission) "Anrufen" else "Mikrofon-Berechtigung noetig") }
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew assembleDebug
```

- [ ] **Step 7: Commit**

```bash
git add android/
git commit -m "feat(android): custom theme, permissions, pull-to-refresh, empty state"
```

---

## Task 6: Release APK

**Files:**
- Modify: `android/app/build.gradle.kts` (signingConfigs, buildTypes, proguard)
- Create: `android/app/proguard-rules.pro`

- [ ] **Step 1: Create proguard-rules.pro**

```pro
# android/app/proguard-rules.pro

# linphone-sdk
-keep class org.linphone.** { *; }
-dontwarn org.linphone.**

# Supabase / Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.calltranscriber.**$$serializer { *; }
-keepclassmembers class com.calltranscriber.** { *** Companion; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
```

- [ ] **Step 2: Add signingConfigs + release buildType**

Add to `android/app/build.gradle.kts`:

```kotlin
android {
    // ... existing ...

    signingConfigs {
        create("release") {
            val props = project.rootProject.file("keystore.properties")
            if (props.exists()) {
                val keystoreProps = java.util.Properties().apply { load(props.inputStream()) }
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

- [ ] **Step 3: Create keystore (one-time, manual)**

```bash
keytool -genkey -v -keystore calltranscriber-release.keystore \
  -alias calltranscriber -keyalg RSA -keysize 2048 -validity 10000
```

Create `android/keystore.properties` (gitignored):
```properties
storeFile=../calltranscriber-release.keystore
storePassword=your-keystore-password
keyAlias=calltranscriber
keyPassword=your-key-password
```

- [ ] **Step 4: Build release APK**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleRelease
```

Expected: `app/build/outputs/apk/release/app-release.apk`

- [ ] **Step 5: [MANUELL] Install and test on device**

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Full E2E: Login → Dialer → Call → Hang up → Transcript in list → Web dashboard.

- [ ] **Step 6: Commit**

```bash
git add android/app/build.gradle.kts android/app/proguard-rules.pro
git commit -m "feat(android): release build with ProGuard, signing config"
```

---

## Dependencies

```
Task 1 (Build) → required by all
Task 2 (BuildConfig) → required by 3, 4
Task 3 (SIP) → requires Zadarma credentials + device
Task 4 (Cloud-Sync) → requires Supabase + Backend running + device
Task 5 (UI Polish) → can run after Task 1
Task 6 (Release) → requires Task 1-5 complete
```

**Parallel moeglich:** Task 5 (UI Polish) kann parallel zu Task 3+4 laufen.

---

## Erfolgskriterien

- [ ] `./gradlew assembleDebug` — BUILD SUCCESSFUL
- [ ] SIP-Registration: Logcat zeigt "Ok"
- [ ] Testanruf: Audio beidseitig, Recording funktioniert
- [ ] Upload: Call erscheint in Supabase `calls` Tabelle
- [ ] Transcription: Transcript in `transcripts` Tabelle
- [ ] App: Call-Liste zeigt Gespraech mit Transkript
- [ ] Web: Dashboard zeigt dasselbe Gespraech
- [ ] `./gradlew assembleRelease` — signierte APK
