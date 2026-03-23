# Architecture Decisions — Call Transcriber

Stand: 2026-03-23

---

## ADR-1: Audio-Aufnahme — Mikrofon-Recorder (nicht SIP)

**Entscheidung:** AudioRecord API mit `VOICE_COMMUNICATION` Source im Foreground Service.

**Alternativen verworfen:**
- SIP/VoIP (Zadarma): Zu komplex, anderer Use-Case (CRM), linphone SDK 50MB+
- AccessibilityService: Google lehnt ab, Play Store Risiko
- Telecom API (ConnectionService): Nur für Self-Managed VoIP Calls, nicht für Carrier-Calls

**Konsequenzen:**
- Nur eigene Stimme + Lautsprecher-Audio (kein Line-Level Recording)
- User muss Mikrofon-Berechtigung geben
- Aufnahmequalität abhängig von Lautsprecher-Lautstärke
- Funktioniert auf allen Android 10+ Geräten ohne Root

---

## ADR-2: Call Detection — PhoneStateListener

**Entscheidung:** `TelephonyManager.PhoneStateListener` (deprecated aber funktioniert bis Android 15+).

**Ablauf:**
1. IDLE → RINGING (eingehend) oder IDLE → OFFHOOK (ausgehend)
2. OFFHOOK = Gespräch aktiv → Recording starten (manuell oder automatisch)
3. IDLE = Gespräch beendet → Recording stoppen

**Berechtigungen:** `READ_PHONE_STATE`, `READ_CALL_LOG` (für Nummer bei eingehenden Anrufen)

**Zukunft:** Migration zu `TelephonyCallback` wenn minSdk auf 31+ steigt.

---

## ADR-3: Recording Service — Foreground Service + WorkManager

**Entscheidung:** Zwei getrennte Mechanismen:

| Aufgabe | Mechanismus | Grund |
|---------|------------|-------|
| Aufnahme | ForegroundService (MICROPHONE type) | Muss während Call aktiv bleiben, Android killt sonst den Prozess |
| Upload | WorkManager | Retry bei Netzwerk-Verlust, exponential backoff, überlebt App-Kill |

**Audio-Pipeline:**
```
AudioRecord (16kHz, Mono, PCM16) → PCM-Datei → WAV-Konvertierung → EncryptedFile (AES-256-GCM) → WorkManager entschlüsselt → Multipart Upload → Backend
```

**Warum nicht alles im ForegroundService?**
Upload kann Minuten dauern, Service blockiert Ressourcen. WorkManager handled Connectivity-Constraints nativ.

---

## ADR-4: Supabase-Schema

**Entscheidung:** 3 Tabellen + Storage Bucket, alle mit Row Level Security.

```
calls (UUID PK)
  ├── user_id → auth.users(id)
  ├── remote_number, direction, started_at, ended_at, duration_seconds
  ├── status: recording → uploading → transcribing → completed | failed
  └── RLS: Users sehen nur eigene Calls

transcripts (UUID PK)
  ├── call_id → calls(id) CASCADE
  ├── text, language, segments (JSONB)
  ├── fts: tsvector GENERATED (German fulltext)
  └── RLS: Users sehen nur Transcripts eigener Calls

audio_files (UUID PK)
  ├── call_id → calls(id) CASCADE
  ├── storage_path, size_bytes, format
  └── RLS: Users sehen nur eigene Audio Files

Storage Bucket: call-recordings (private)
  ├── Pfad: {user_id}/{call_id}.wav
  └── RLS: Users lesen nur eigenen Ordner
```

**Fulltext-Suche:** `websearch_to_tsquery('german', ...)` mit GIN-Index auf tsvector.

---

## ADR-5: Backend — FastAPI mit Background Tasks

**Entscheidung:** FastAPI mit `BackgroundTasks` für Transkription.

**Upload-Flow:**
1. Android schickt WAV + Metadaten als Multipart
2. Backend validiert (Auth, Größe ≤100MB, Audio-MIME, Telefonnummer-Format)
3. Speichert in Supabase Storage + DB-Eintrag
4. BackgroundTask: OpenAI Whisper → Transcript in DB → Status "completed"

**Warum nicht Celery/Redis?** YAGNI. Bei <100 gleichzeitigen Users reicht BackgroundTasks. Wenn Skalierung nötig: Celery nachrüsten.

**Auth:** Supabase JWT (HS256, audience="authenticated"). Backend validiert mit `python-jose`.

---

## ADR-6: VPS Deploy — Docker Compose + Traefik

**Entscheidung:** Zwei Container (backend, web) hinter bestehendem Traefik.

```yaml
backend: python:3.12-slim, uvicorn, Port 8000
web: node:20-alpine, Next.js standalone, Port 3000
```

**Domains:**
- API: `transcriber-api.aicraftors.com` (Traefik → backend:8000)
- Web: `transcriber.aicraftors.com` (Traefik → web:3000)

**Kein eigener Supabase-Server.** Supabase Cloud (Free Tier reicht für MVP).

**Env-Variablen Backend (.env):**
- SUPABASE_URL, SUPABASE_SERVICE_KEY, SUPABASE_JWT_SECRET, OPENAI_API_KEY

**Env-Variablen Web:**
- NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_ANON_KEY

---

## ADR-7: Android Auth — Supabase Kotlin SDK

**Entscheidung:** `io.github.jan.supabase:gotrue-kt` für Email/Password Auth.

**Token-Handling:**
- Login → Supabase SDK speichert Session (Access + Refresh Token)
- UploadWorker liest Token via `SupabaseClientProvider.client.auth.currentAccessTokenOrNull()`
- Backend validiert JWT mit service_key Secret

**Offline:** Room DB als lokaler Cache. Sync bei Netzwerk-Verfügbarkeit.

---

## Offene Punkte (Phase 2+)

- [ ] Transkriptionsmodell wählbar (whisper-1, gpt-4o-transcribe, etc.)
- [ ] Automatische Aufnahme bei Call-Start (statt manueller Start)
- [ ] Zusammenfassung via GPT-4o nach Transkription
- [ ] Push-Notification wenn Transkript fertig
- [ ] Kontakt-Zuordnung aus Telefonbuch
