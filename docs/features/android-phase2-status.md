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
| Gradle Wrapper | ❌ Offen | Braucht `gradle wrapper --gradle-version 8.11` |
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

**Aktueller Stand:** Alle Code-Änderungen erledigt. Blocker: Gradle Wrapper + Android SDK für Build.

**Nächster Schritt:** Gradle Wrapper anlegen, dann `./gradlew assembleDebug`.
