# Android Remaining Tasks — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android App build-fähig machen, fehlende Permissions + Recording-Ansage ergänzen, Gradle Wrapper anlegen.

**Architecture:** Keine neuen Architektur-Änderungen. Nur Lücken schließen in der bestehenden Mic-Recorder App.

**Tech Stack:** Kotlin, Jetpack Compose, AudioRecord, Hilt, Room, WorkManager (alles vorhanden)

---

## File Structure

| Action | Files |
|--------|-------|
| CREATE | `android/gradle/wrapper/gradle-wrapper.properties` |
| CREATE | `android/gradlew` + `android/gradlew.bat` |
| MODIFY | `android/app/src/main/java/com/calltranscriber/MainActivity.kt:38-44` — READ_PHONE_STATE Permission |
| MODIFY | `android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt` — Announcement vor Recording |
| CREATE | `android/app/src/main/res/raw/` Verzeichnis (Announcement Audio Placeholder) |

---

### Task 1: Gradle Wrapper anlegen

**Files:**
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/gradlew`, `android/gradlew.bat`

- [ ] **Step 1: Gradle Wrapper generieren**

```bash
cd /mnt/volume/Projects/call-transcriber/android
# Option A: Wenn gradle CLI verfügbar
gradle wrapper --gradle-version 8.11
# Option B: Manuell herunterladen
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
```

Dann gradlew-Skripte von einem bekannten Gradle-Projekt kopieren oder `gradle wrapper` ausführen.

- [ ] **Step 2: Verifiziere Wrapper**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew --version
```

Expected: `Gradle 8.11`

- [ ] **Step 3: Commit**

```bash
git add android/gradle/ android/gradlew android/gradlew.bat
git commit -m "build(android): add Gradle 8.11 wrapper"
```

---

### Task 2: READ_PHONE_STATE Permission bei App-Start anfordern

**Files:**
- Modify: `android/app/src/main/java/com/calltranscriber/MainActivity.kt:38-44`

- [ ] **Step 1: Permission-Liste erweitern**

In `MainActivity.kt`, Zeile 39 — `mutableListOf(Manifest.permission.RECORD_AUDIO)` erweitern:

```kotlin
private fun requestPermissions() {
    val perms = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/calltranscriber/MainActivity.kt
git commit -m "feat(android): request READ_PHONE_STATE permission at startup"
```

---

### Task 3: Recording-Ansage (§201 StGB Compliance)

**Files:**
- Modify: `android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt`
- Create: `android/app/src/main/res/raw/` (Verzeichnis)

- [ ] **Step 1: Notification-Text als Ansage-Hinweis**

Da kein TTS/Audio-File vorhanden: Zunächst Notification-Text als Compliance-Hinweis.
Der User sieht "Gespräch wird aufgezeichnet" in der Notification.

Später: TTS-Ansage über Lautsprecher wenn AudioRecord startet.

In `CallRecordingService.kt`, `startRecording()` — vor `audioRecord?.startRecording()` einen Log-Eintrag:

```kotlin
Log.w(TAG, "§201 StGB: Recording started — user must inform call partner!")
```

Und in der Notification den Text prominenter machen:

```kotlin
private fun createNotification(): Notification =
    NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("⚠ Aufnahme aktiv")
        .setContentText("Gesprächspartner muss informiert werden (§201 StGB)")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
```

- [ ] **Step 2: raw-Verzeichnis anlegen (für spätere Audio-Ansage)**

```bash
mkdir -p android/app/src/main/res/raw
touch android/app/src/main/res/raw/.gitkeep
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt
git add android/app/src/main/res/raw/
git commit -m "feat(android): §201 StGB compliance notification + raw dir for future announcement"
```

---

### Task 4: Erster Build (Android SDK erforderlich)

**Files:** Keine neuen Dateien.

- [ ] **Step 1: Build ausführen**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleDebug 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL` oder spezifische Compile-Fehler.

- [ ] **Step 2: Fehler fixen falls nötig**

Häufige Probleme:
- `SDK not found` → `local.properties` mit `sdk.dir=/path/to/android/sdk`
- Supabase API Inkompatibilität → Import-Pfade prüfen
- Kotlin/Compose Version Mismatch → Plugin-Versionen in root `build.gradle.kts` abgleichen
- Missing `kotlinx.serialization` plugin → In plugins Block hinzufügen

- [ ] **Step 3: Commit wenn erfolgreich**

```bash
git add -A
git commit -m "build(android): first successful assembleDebug"
```

---

## Dependencies

```
Task 1 (Gradle Wrapper) → required by Task 4
Task 2 (Permissions) → independent
Task 3 (§201 Ansage) → independent
Task 4 (Build) → depends on Task 1
```

Parallelisierbar: Task 2 + Task 3 parallel, dann Task 1, dann Task 4.

---

## Hinweis: Veralteter Plan

`docs/ANDROID-PHASE2-PLAN.md` ist VERALTET — referenziert SIP/linphone/Zadarma.
Folgende Tasks daraus sind bereits erledigt:
- Task 1 (Build): Teilweise (Wrapper fehlt)
- Task 2 (BuildConfig): ✅ Komplett
- Task 3 (SIP): ❌ OBSOLET (SIP entfernt, Mic-Recorder stattdessen)
- Task 4 (Cloud-Sync): ✅ Backend deployed, Migrations angewandt
- Task 5 (UI Polish): ✅ Theme, Permissions, Pull-to-Refresh
- Task 6 (Release APK): ✅ signingConfigs + ProGuard
