# Android App — Status

**Stand:** 2026-03-23
**Commits:** 32+ (inkl. Mic-Recorder Migration)
**Architektur:** Mikrofon-Recorder (AudioRecord API) — KEIN SIP/VoIP
**Build:** Noch nicht verifiziert (braucht Android SDK + Android Studio)

---

## Was gebaut wurde

### Gradle-Projekt
- `android/build.gradle.kts` — Root mit Plugin-Versionen (AGP 8.7.3, Kotlin 2.1.0, Hilt 2.53.1)
- `android/settings.gradle.kts` — Repository-Config, Modul-Include
- `android/gradle.properties` — JVM-Args, AndroidX
- `android/app/build.gradle.kts` — compileSdk 35, minSdk 29, BuildConfig Credentials

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

### App-Architektur
- `CallTranscriberApp.kt` — @HiltAndroidApp Application (keine SIP-Initialisierung)
- `MainActivity.kt` — Single-Activity mit NavGraph
- `di/AppModule.kt` — Hilt-Modul: Room DB, CallDao

### Auth
- `data/repository/AuthRepository.kt` — Supabase Email/Password (signUp, signIn, signOut, getAccessToken)
- `ui/auth/AuthViewModel.kt` — StateFlow mit isLoading, isLoggedIn, error
- `ui/auth/LoginScreen.kt` — Email + Passwort, Fehleranzeige
- `ui/auth/RegisterScreen.kt` — Email + Passwort + Bestaetigung (min. 8 Zeichen)
- `ui/navigation/Screen.kt` — Sealed class: Login, Register, CallList, CallDetail, Dialer
- `ui/navigation/NavGraph.kt` — Navigation mit popUpTo nach Login/Register

### Lokale Datenbank + Cloud-Sync
- `data/local/CallEntity.kt` — Room Entity (id, remoteNumber, direction, startedAt, status, transcriptText, syncedToCloud)
- `data/local/CallDao.kt` — Flow-basierte Queries, Upsert, Suche via LIKE
- `data/local/CallDatabase.kt` — Room Database v1
- `data/repository/CallRepository.kt` — Single Source of Truth: Room lokal, Supabase Cloud-Sync

### Call Detection (PhoneStateListener)
- `recording/CallState.kt` — Enum: IDLE, RINGING, IN_CALL, ENDED
- `recording/CallDetector.kt` — TelephonyManager PhoneStateListener, StateFlow für CallState + Nummer

### Recording + Upload (Mikrofon-Recorder)
- `service/CallRecordingService.kt` — Foreground Service (MICROPHONE Type), AudioRecord(VOICE_COMMUNICATION, 16kHz, Mono), PCM→WAV, EncryptedFile (AES-256-GCM)
- `data/remote/ApiClient.kt` — Ktor HTTP Client: multipart Upload an FastAPI (BuildConfig.API_BASE_URL)
- `upload/UploadWorker.kt` — WorkManager: Entschluesselung → Upload → Retry (exponential backoff, 30s)

### UI Screens
- `ui/calls/CallListViewModel.kt` — Debounced Suche, Cloud-Sync beim Start
- `ui/calls/CallListScreen.kt` — Scaffold + LazyColumn, Suchfeld, FAB zum Dialer
- `ui/calls/CallDetailScreen.kt` — Transkript-Anzeige, Metadaten, Scroll
- `ui/dialer/DialerViewModel.kt` — CallDetector + Recording Control, Start/Stop über ForegroundService
- `ui/dialer/DialerScreen.kt` — Aufnahme-Steuerung, Telefon-Status Anzeige, manuelle Nummereingabe

### AndroidManifest.xml
```
Permissions: INTERNET, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE,
             POST_NOTIFICATIONS, READ_PHONE_STATE, READ_CALL_LOG
Services:    CallRecordingService (foregroundServiceType=microphone)
```

---

## Was fehlt (vor dem ersten Build)

### PFLICHT — Credentials eintragen
| Datei | Was eintragen | Wo herbekommen |
|-------|---------------|----------------|
| `gradle.properties` | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `API_BASE_URL` | Supabase Dashboard + VPS URL |

### PFLICHT — Gradle Wrapper
```bash
cd android/
gradle wrapper --gradle-version 8.11
```

### PFLICHT — Erster Build
```bash
cd android/
./gradlew assembleDebug
```

### EMPFOHLEN — Vor Release
- [ ] `local.properties` mit `sdk.dir` (Android SDK Pfad)
- [ ] ProGuard/R8 Rules pruefen (Supabase, Ktor, Room)
- [ ] App-Icon und Theme anpassen
- [ ] Runtime-Permission-Flow testen (RECORD_AUDIO, READ_PHONE_STATE, POST_NOTIFICATIONS)
- [ ] Aufnahme-Ansage implementieren (§201 StGB: "Gespraech wird aufgezeichnet")
- [ ] Signing-Config fuer Release-Build
- [ ] Unit-Tests fuer ViewModels und Repositories

### BEKANNTE RISIKEN
1. **Aufnahmequalitaet:** VOICE_COMMUNICATION nimmt nur eigene Stimme + Lautsprecher-Wiedergabe auf, kein Line-Level Recording
2. **Supabase Kotlin SDK BOM 3.0.3**: Relativ neu, API kann sich aendern. Package-Namen pruefen
3. **Android 14+ Permissions**: FOREGROUND_SERVICE_MICROPHONE muss zur Laufzeit erklaert werden
4. **EncryptedFile Alpha**: security-crypto:1.1.0-alpha06 ist noch Alpha
5. **PhoneStateListener deprecated**: Funktioniert aber bis Android 15+, Migration zu TelephonyCallback wenn minSdk 31+

---

## Dateistruktur

```
android/
  build.gradle.kts                    # AGP 8.7.3, Kotlin 2.1.0, Hilt 2.53.1
  settings.gradle.kts                 # google(), mavenCentral()
  gradle.properties                   # JVM args, AndroidX, BuildConfig Credentials
  app/
    build.gradle.kts                  # compileSdk 35, minSdk 29, BuildConfig fields
    proguard-rules.pro                # Supabase, Ktor, Room rules
    src/main/
      AndroidManifest.xml             # 7 Permissions, 1 Service
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
            SupabaseClient.kt         # Supabase SDK Init (BuildConfig)
            ApiClient.kt              # Ktor Upload Client (BuildConfig)
          repository/
            AuthRepository.kt         # Supabase Email Auth
            CallRepository.kt         # Room + Supabase Sync
        recording/
          CallState.kt               # Enum: IDLE, RINGING, IN_CALL, ENDED
          CallDetector.kt             # PhoneStateListener Wrapper
        service/
          CallRecordingService.kt     # AudioRecord Foreground Service
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
            DialerViewModel.kt        # CallDetector + Recording Control
            DialerScreen.kt           # Aufnahme-Steuerung + Status
          navigation/
            Screen.kt                 # Route Definitionen
            NavGraph.kt               # Navigation Host
```

---

## Recovery

Lies diese Datei + `CLAUDE.md` + `docs/ARCHITECTURE-DECISIONS.md` + letzte 3 Git-Commits.

**Aktueller Stand:** Mic-Recorder Migration komplett. SIP/linphone entfernt. Backend 6/6 Tests PASS. Deploy-Plan erstellt.

**Naechster Schritt:**
1. Supabase-Projekt anlegen, Migrations 001-007 anwenden
2. `.env` Dateien befuellen (Backend + Web)
3. `./scripts/deploy.sh` auf VPS
4. Android: `gradle.properties` mit Credentials, `./gradlew assembleDebug`
5. End-to-End Test: Anruf → Aufnahme → Upload → Transkript
