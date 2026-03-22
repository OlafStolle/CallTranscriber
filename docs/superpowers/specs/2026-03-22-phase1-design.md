# Phase 1 Design — Call Transcriber

## Ansatz
Monolith-First mit Supabase (Auth, DB, Storage, Realtime) + FastAPI Processing Backend.

## Kernfeature
User hat ein persoenliches Konto (Login). Alle Transkripte landen im Konto, sichtbar am Handy (Android App) und am Desktop (Web-Dashboard).

## Komponenten

### 1. Android App (Kotlin + Jetpack Compose)
- **Auth:** Supabase Auth (Email/Passwort, ggf. Magic Link)
- **SIP-Client:** Zadarma SIP-Trunk via SRTP (SRTP.SIP oder linphone-sdk)
- **Recording:** Foreground Service, AudioRecord API, Opus/WAV Encoding
- **Lokaler Speicher:** EncryptedFile (AndroidX Security), lokaler Cache fuer Offline
- **Upload:** WorkManager Queue → FastAPI Upload Endpoint
- **Gespraechsliste:** Jetpack Compose UI, Supabase Realtime fuer Live-Updates
- **Offline:** Room DB als lokaler Cache, Sync bei Connectivity

### 2. Cloud Backend (FastAPI auf VPS)
- **Upload Endpoint:** Auth Token validieren, Audio empfangen, in Supabase Storage ablegen
- **Transcription:** OpenAI Whisper/GPT-4o-transcribe API
- **DB Write:** Transkript + Metadaten in Supabase PostgreSQL
- **Email:** Optional, Transkript per Email (nicht Kernfeature)

### 3. Supabase
- **Auth:** User-Konten, JWT, Session Management
- **PostgreSQL:** Tabellen: users, calls, transcripts (mit Volltextsuche)
- **Storage:** Audio-Dateien mit RLS (nur eigener User)
- **Realtime:** Live-Updates fuer App + Dashboard

### 4. Web-Dashboard (Next.js)
- **Auth:** Supabase JS SDK (Login/Logout)
- **Gespraeche:** Liste mit Suche (Datum, Kontakt, Stichwort)
- **Transkript-Ansicht:** Volltext mit Zeitstempeln
- **Export:** CSV/PDF Download

## Android Permissions (14+)
- FOREGROUND_SERVICE_MICROPHONE
- RECORD_AUDIO
- POST_NOTIFICATIONS
- INTERNET
- ConnectionService/Telecom API Registrierung

## Datenmodell (Supabase PostgreSQL)
- **users:** via Supabase Auth (auth.users)
- **calls:** id, user_id, remote_number, direction, started_at, ended_at, duration, status
- **transcripts:** id, call_id, text, language, segments (JSONB mit Timestamps), created_at
- **audio_files:** id, call_id, storage_path, size_bytes, format

RLS: Alle Tabellen mit `auth.uid() = user_id` Policy.

## Sicherheit
- SRTP fuer SIP (verschluesselte Telefonie)
- EncryptedFile lokal auf Android
- Supabase RLS fuer Datenisolierung
- Auth Token fuer Upload-Endpoint
- DSGVO: Loeschfunktion im Dashboard, Retention Policy

## Nicht in Phase 1
- Zusammenfassung/Action Items (Phase 2)
- Kundenzuordnung CRM (Phase 2)
- Lokale ASR Preview (Phase 3)
