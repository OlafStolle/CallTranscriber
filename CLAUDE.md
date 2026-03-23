# Call Transcriber — Android Mikrofon-Recorder + Cloud Transkription

## Zweck
Android-App die während normaler Telefonate das Gespräch per Mikrofon aufnimmt, transkribiert und im persönlichen Konto speichert.

## Entscheidung
Mikrofon-Recorder (AudioRecord mit VOICE_COMMUNICATION). KEIN SIP/VoIP.
Grund: Simpelster Ansatz — User telefoniert normal, unsere App nimmt per Mikrofon auf.

## Tech Stack
- **Android:** Kotlin, Jetpack Compose, AudioRecord API, PhoneStateListener
- **Cloud:** FastAPI, OpenAI Whisper/GPT-4o-transcribe
- **DB:** Supabase (Auth + PostgreSQL + Storage)
- **Speicher:** App-specific Storage (EncryptedFile), WorkManager Upload

## Architektur
```
Android App (Kotlin)
  ├── PhoneStateListener (erkennt Anruf-Start/Ende)
  ├── AudioRecord (VOICE_COMMUNICATION) im Foreground Service
  ├── EncryptedFile Storage (AES-256-GCM)
  └── WorkManager Upload Queue
        ↓
Cloud (FastAPI auf VPS)
  ├── Upload Endpoint (Supabase Auth Token)
  ├── OpenAI Transcription API
  ├── Zusammenfassung (GPT-4o)
  ├── CRM Integration (Supabase)
  └── DSGVO: Retention/Deletion
```

## Kern-Feature: Persönliches Konto
- User hat ein KONTO (Web + App) wo alle Transkripte landen
- Am Phone: App zeigt alle bisherigen Gespräche mit Transkript
- Am Desktop: Web-Dashboard zum Durchsuchen, Filtern, Exportieren
- Konto = Identität (Login), NICHT nur Handynummer
- Gespräche durchsuchbar nach Datum, Kontakt, Stichwort
- Offline verfügbar auf dem Handy (lokaler Cache)

## Phasen
- Phase 1: Mikrofon-Recorder + Upload + Cloud-Transkript + Konto/Dashboard
- Phase 2: Zusammenfassung, Action Items, Kundenzuordnung, Sucharchiv
- Phase 3: Lokale Preview-ASR, Offline-Fallback

## Rechtlich
- KEIN heimliches Aufnehmen (§201 StGB)
- Ansage "Gespräch wird aufgezeichnet" pflicht
- DSGVO: Auftragsverarbeitung, Löschfristen, Informationspflicht
- "Kunde muss einstehen" reicht NICHT — wir sind Auftragsverarbeiter
