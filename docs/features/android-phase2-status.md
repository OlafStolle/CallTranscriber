# Android Phase 2 — Status

**Stand:** 2026-03-24
**Architektur:** Mikrofon-Recorder (KEIN SIP) — siehe `docs/ARCHITECTURE-DECISIONS.md` ADR-1

---

## Status-Übersicht

| Feature | Status | Details |
|---------|--------|---------|
| BuildConfig Credentials | ✅ Done | Supabase URL + Key + API URL via gradle.properties |
| SIP entfernt → Mic Recorder | ✅ Done | AudioRecord(VOICE_COMMUNICATION), PhoneStateListener |
| Custom Theme | ✅ Done | Color.kt + Theme.kt (Material3) |
| Runtime Permissions | ✅ Done | RECORD_AUDIO, READ_PHONE_STATE, POST_NOTIFICATIONS |
| Pull-to-Refresh | ✅ Done | CallListScreen mit PullToRefreshBox |
| Empty State | ✅ Done | "Noch keine Gespraeche" Anzeige |
| ProGuard Rules | ✅ Done | Supabase, Ktor, Room, Serialization |
| Signing Config | ✅ Done | keystore.properties-basiert |
| §201 StGB Notification | ✅ Done | Prominenter Hinweis in Foreground Notification |
| Gradle Wrapper | ❌ BLOCKER | `gradle` CLI nicht auf Server verfügbar — braucht lokale Dev-Maschine mit Android SDK/Gradle |
| Erster Build | ❌ Offen | Braucht Android SDK (nicht auf diesem System) |
| Recording Announcement | ❌ Offen | Audio-Datei "Gespräch wird aufgezeichnet" |
| E2E Test auf Gerät | ❌ Offen | Braucht Android-Gerät/Emulator |

---

## Nächste konkrete Schritte

### 1. Gradle Wrapper (BLOCKER für Build)
```bash
cd /mnt/volume/Projects/call-transcriber/android
gradle wrapper --gradle-version 8.11
```
Braucht `gradle` CLI oder Android Studio.

**⚠ BLOCKER (2026-03-24):** `gradle` CLI ist auf dem VPS/Server nicht installiert.
Lösung: Auf lokaler Entwicklungsmaschine mit Android SDK ausführen, dann `gradlew`, `gradle/wrapper/` committen.

### 2. Erster Build
```bash
cd android && ./gradlew assembleDebug
```
Compile-Fehler fixen falls nötig (Supabase SDK Imports, Kotlin Version).

### 3. Recording Announcement Audio
TTS oder manuell aufgenommene WAV "Dieses Gespräch wird aufgezeichnet" → `res/raw/recording_announcement.wav`

### 4. E2E Test
1. App auf Gerät installieren
2. Registrieren/Einloggen
3. Anruf starten (normales Telefonat)
4. Aufnahme starten über App
5. Prüfen: Upload → Transkript im Web-Dashboard

---

## WARNUNG: Veralteter Plan

`docs/ANDROID-PHASE2-PLAN.md` ist VERALTET — referenziert SIP/Zadarma/linphone.
Aktueller Plan: `docs/superpowers/plans/2026-03-24-android-remaining.md`

---

## Recovery

Lies diese Datei + `CLAUDE.md` + `docs/ARCHITECTURE-DECISIONS.md`.

**Aktueller Stand:** Alle Code-Änderungen erledigt. Blocker: `gradle` CLI nicht auf Server verfügbar.

**Deployment:**
- GitHub: `https://github.com/OlafStolle/CallTranscriber.git` (main gepusht)
- Coolify Projekt: `ksk0gw04so08cssocwwcg40c` (CallTranscriber)
- Backend App: `gk40g08ocsokkw08oocsos4c` (Dockerfile, base_dir: /backend)
- Web App: `pk80c40o8gc80osw8cwcc0cs` (Dockerfile, base_dir: /web)
- Alter Service `p88w0k4404scgwg4cg0w0cgw` geloescht (kein Webhook-Support)

**URLs:**
- Web Dashboard: `https://calltrans.ai-crafters.io` (Cloudflare DNS ✅, Coolify ✅)
- Backend API: `https://calltrans-api.ai-crafters.io` (Cloudflare DNS ✅, Coolify ✅)

**Auto-Deploy:**
- GitHub Webhook ID: `602375813`
- Webhook URL: `https://coolify.ai-crafters.io/webhooks/source/github/events`
- Webhook Secret: `calltrans-deploy-2026` (auf beiden Apps + GitHub gesetzt)
- Trigger: Push auf `main` → Coolify baut beide Apps automatisch neu
- Status: Apps noch nicht gestartet (braucht .env auf VPS)

**Nächster Schritt:**
1. .env Dateien fuer backend + web in Coolify konfigurieren (Supabase Keys, OpenAI Key)
2. Apps deployen (push auf main oder manueller Deploy via Coolify)
3. Gradle Wrapper auf lokaler Dev-Maschine mit Android SDK generieren, committen, dann `./gradlew assembleDebug`
