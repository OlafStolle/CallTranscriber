# Android App — Status

**Stand:** 2026-03-22
**Commits:** 7 (c125cef → 2000942)
**Dateien:** 30 (25 Kotlin, 2 Gradle KTS, 1 XML, 1 Properties, 1 root Gradle)
**Build:** Noch nicht verifiziert (braucht Android SDK + Android Studio)

---

## Was gebaut wurde

### Gradle-Projekt (Task 1)
- `android/build.gradle.kts` — Root mit Plugin-Versionen (AGP 8.7.3, Kotlin 2.1.0, Hilt 2.53.1)
- `android/settings.gradle.kts` — Repository-Config, Modul-Include
- `android/gradle.properties` — JVM-Args, AndroidX
- `android/app/build.gradle.kts` — compileSdk 35, minSdk 29, alle Dependencies

### Dependencies
| Bereich | Library | Version |
|---------|---------|---------|
| UI | Jetpack Compose (BOM) | 2024.12.01 |
| UI | Material3 | via BOM |
| Navigation | navigation-compose | 2.8.5 |
| Auth/DB | Supabase Kotlin SDK (BOM) | 3.0.3 |
| HTTP | Ktor Client Android | 3.0.2 |
| DI | Hilt | 2.53.1 |
| Cache | Room | 2.6.1 |
| Upload | WorkManager | 2.10.0 |
| Security | EncryptedFile | 1.1.0-alpha06 |
| SIP | linphone-sdk-android | 5.3.74 |

### App-Architektur (Task 2)
- `CallTranscriberApp.kt` — @HiltAndroidApp Application
- `MainActivity.kt` — Single-Activity mit NavGraph
- `di/AppModule.kt` — Hilt-Modul: Room DB, CallDao

### Auth (Task 3)
- `data/repository/AuthRepository.kt` — Supabase Email/Password (signUp, signIn, signOut, getAccessToken)
- `ui/auth/AuthViewModel.kt` — StateFlow mit isLoading, isLoggedIn, error
- `ui/auth/LoginScreen.kt` — Email + Passwort, Fehleranzeige
- `ui/auth/RegisterScreen.kt` — Email + Passwort + Bestaetigung (min. 8 Zeichen)
- `ui/navigation/Screen.kt` — Sealed class: Login, Register, CallList, CallDetail, Dialer
- `ui/navigation/NavGraph.kt` — Navigation mit popUpTo nach Login/Register

### Lokale Datenbank + Cloud-Sync (Task 4)
- `data/local/CallEntity.kt` — Room Entity (id, remoteNumber, direction, startedAt, status, transcriptText, syncedToCloud)
- `data/local/CallDao.kt` — Flow-basierte Queries, Upsert, Suche via LIKE
- `data/local/CallDatabase.kt` — Room Database v1
- `data/repository/CallRepository.kt` — Single Source of Truth: Room lokal, Supabase Cloud-Sync

### SIP-Client (Task 5)
- `sip/SipConfig.kt` — Data Class: domain (sip.zadarma.com), port, transport (TLS), SRTP
- `sip/SipManager.kt` — linphone Core Wrapper: Registration, Call-Management, SRTP
- `service/CallConnectionService.kt` — Android Telecom API: PROPERTY_SELF_MANAGED, VoIP-Audio

### Recording + Upload (Task 6)
- `service/CallRecordingService.kt` — Foreground Service (MICROPHONE Type), linphone `call.startRecording()` (beide Seiten), EncryptedFile
- `data/remote/ApiClient.kt` — Ktor HTTP Client: multipart Upload an FastAPI
- `upload/UploadWorker.kt` — WorkManager: Entschluesselung → Upload → Retry (exponential backoff, 30s)

### UI Screens (Task 7)
- `ui/calls/CallListViewModel.kt` — Debounced Suche, Cloud-Sync beim Start
- `ui/calls/CallListScreen.kt` — Scaffold + LazyColumn, Suchfeld, FAB zum Dialer
- `ui/calls/CallDetailScreen.kt` — Transkript-Anzeige, Metadaten, Scroll
- `ui/dialer/DialerViewModel.kt` — makeCall() startet SIP + Recording + lokalen DB-Eintrag
- `ui/dialer/DialerScreen.kt` — Nummereingabe, Anrufen/Auflegen je nach CallState

### AndroidManifest.xml
```
Permissions: INTERNET, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE,
             POST_NOTIFICATIONS, MANAGE_OWN_CALLS, READ_PHONE_STATE
Services:    CallConnectionService (BIND_TELECOM_CONNECTION_SERVICE, exported)
             CallRecordingService (foregroundServiceType=microphone)
```

---

## Was fehlt (vor dem ersten Build)

### PFLICHT — Credentials eintragen
| Datei | Was eintragen | Wo herbekommen |
|-------|---------------|----------------|
| `data/remote/SupabaseClient.kt` | `supabaseUrl` + `supabaseKey` | Supabase Dashboard → Settings → API |
| `data/remote/ApiClient.kt` | `baseUrl` | FastAPI Backend URL (z.B. `https://transcriber-api.domain.com`) |
| `sip/SipConfig.kt` | `username` + `password` | Zadarma Dashboard → My PBX → SIP Accounts |

### PFLICHT — Gradle Wrapper
```bash
cd android/
gradle wrapper --gradle-version 8.11
```
Oder: Projekt in Android Studio oeffnen → Gradle Sync.

### PFLICHT — Erster Build
```bash
cd android/
./gradlew assembleDebug
```
Erwartbar: Compile-Fehler bei linphone-SDK Imports wenn SDK nicht korrekt aufgeloest wird. Fix: Maven-Repository fuer linphone pruefen.

### EMPFOHLEN — Vor Release
- [ ] `local.properties` mit `sdk.dir` (Android SDK Pfad)
- [ ] ProGuard/R8 Rules fuer linphone-sdk, Supabase, Ktor
- [ ] App-Icon und Theme anpassen
- [ ] Runtime-Permission-Flow testen (RECORD_AUDIO, POST_NOTIFICATIONS)
- [ ] Aufnahme-Ansage implementieren (§201 StGB: "Gespraech wird aufgezeichnet")
- [ ] Signing-Config fuer Release-Build
- [ ] Unit-Tests fuer ViewModels und Repositories

### BEKANNTE RISIKEN
1. **linphone-sdk Maven**: Das Package `org.linphone:linphone-sdk-android:5.3.74` muss von linphone.org Maven verfuegbar sein. Falls nicht: manuell als AAR einbinden.
2. **Supabase Kotlin SDK BOM 3.0.3**: Relativ neu, API kann sich aendern. Package-Namen pruefen.
3. **Android 14+ Permissions**: `FOREGROUND_SERVICE_MICROPHONE` muss zur Laufzeit erklaert werden.
4. **EncryptedFile Alpha**: `security-crypto:1.1.0-alpha06` ist noch Alpha. Stabil aber API koennte sich aendern.

---

## Dateistruktur

```
android/
  build.gradle.kts                    # AGP 8.7.3, Kotlin 2.1.0, Hilt 2.53.1
  settings.gradle.kts                 # google(), mavenCentral()
  gradle.properties                   # JVM args, AndroidX
  app/
    build.gradle.kts                  # compileSdk 35, minSdk 29, 15 Dependencies
    src/main/
      AndroidManifest.xml             # 7 Permissions, 2 Services
      java/com/calltranscriber/
        CallTranscriberApp.kt         # @HiltAndroidApp
        MainActivity.kt               # Single Activity + NavGraph
        di/AppModule.kt               # Room DB Provider
        data/
          local/
            CallEntity.kt             # Room Entity
            CallDao.kt                # Room DAO (Flow-basiert)
            CallDatabase.kt           # Room DB v1
          remote/
            SupabaseClient.kt         # Supabase SDK Init [CREDENTIALS NOETIG]
            ApiClient.kt              # Ktor Upload Client [URL NOETIG]
          repository/
            AuthRepository.kt         # Supabase Email Auth
            CallRepository.kt         # Room + Supabase Sync
        sip/
          SipConfig.kt                # Zadarma Config [CREDENTIALS NOETIG]
          SipManager.kt               # linphone Core Wrapper
        service/
          CallConnectionService.kt    # Telecom API (Self-Managed)
          CallRecordingService.kt     # Foreground + linphone Recording
        upload/
          UploadWorker.kt             # WorkManager Upload Queue
        ui/
          auth/
            AuthViewModel.kt          # Login/Register State
            LoginScreen.kt            # Login UI
            RegisterScreen.kt         # Register UI
          calls/
            CallListViewModel.kt      # Suche + Sync
            CallListScreen.kt         # Gespraechsliste
            CallDetailScreen.kt       # Transkript-Ansicht
          dialer/
            DialerViewModel.kt        # SIP Call + Recording
            DialerScreen.kt           # Nummereingabe + Call-Status
          navigation/
            Screen.kt                 # Route Definitionen
            NavGraph.kt               # Navigation Host
```

---

## Recovery

Lies diese Datei + `docs/phase1-summary.md` + `CLAUDE.md` + letzte 3 Git-Commits.

**Aktueller Stand:** Phase 1 komplett. 21 Commits auf main. Alle 3 Tracks implementiert (Backend, Web, Android). Backend-Tests 6/6 PASSED. Android braucht Android SDK fuer Build-Verifikation.

**Letzter Commit:** `fab4535` — docs: Android app status

**Branch:** `main` (+ `feature/phase1-complete` Tag)

**Naechster Schritt:**
1. Supabase-Projekt anlegen, Migrations 001-007 anwenden
2. `.env` Dateien befuellen (Backend: SUPABASE_URL, SERVICE_KEY, JWT_SECRET, OPENAI_API_KEY)
3. `docker compose up -d` auf VPS
4. Android Studio: Projekt oeffnen, Gradle Sync, Credentials in SupabaseClient.kt + SipConfig.kt + ApiClient.kt eintragen
5. `./gradlew assembleDebug` — erster Build
6. End-to-End Test: Anruf → Aufnahme → Upload → Transkript im Web-Dashboard

**Wichtige Dateien fuer Kontext:**
- `docs/superpowers/plans/2026-03-22-phase1-implementation.md` — Gesamtplan (Backend + Web)
- `docs/superpowers/plans/2026-03-22-android-track.md` — Android-Plan (7 Tasks)
- `docs/superpowers/specs/2026-03-22-phase1-design.md` — Architektur-Spec
- `docs/phase1-summary.md` — Zusammenfassung aller Tracks
