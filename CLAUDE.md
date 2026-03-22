# Call Transcriber — Android VoIP + Cloud Transkription

## Zweck
Android-App die über eigene VoIP/SIP-Telefonie Gespräche aufnimmt, transkribiert und an CRM/Email sendet.

## Entscheidung
KEIN PSTN-Recording-Hack. Eigene VoIP-App mit Zadarma SIP.
Grund: Android gibt Dritt-Apps keinen sauberen Zugang zum Carrier-Call-Audio.

## Tech Stack
- **Android:** Kotlin, Jetpack Compose, ConnectionService/Telecom API
- **VoIP:** Zadarma SIP-Trunk, SRTP
- **Cloud:** FastAPI, OpenAI Whisper/GPT-4o-transcribe
- **CRM:** Supabase DB, n8n Webhooks, Email-Versand
- **Speicher:** App-specific Storage (verschlüsselt), WorkManager Upload

## Architektur
```
Android App (Kotlin)
  ├── SIP-Client (Zadarma)
  ├── Foreground Service (Recording)
  ├── Lokaler Chunker + VAD
  ├── EncryptedFile Storage
  └── WorkManager Upload Queue
        ↓
Cloud (FastAPI auf VPS)
  ├── Upload Endpoint (Auth Token)
  ├── OpenAI Transcription API
  ├── Zusammenfassung (GPT-4o)
  ├── CRM Integration (Supabase)
  ├── Email mit Transkript
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
- Phase 1: SIP-Client + Recording + Upload + Cloud-Transkript + Konto/Dashboard
- Phase 2: Zusammenfassung, Action Items, Kundenzuordnung, Sucharchiv
- Phase 3: Lokale Preview-ASR, Offline-Fallback

## Rechtlich
- KEIN heimliches Aufnehmen (§201 StGB)
- Ansage "Gespräch wird aufgezeichnet" pflicht
- DSGVO: Auftragsverarbeitung, Löschfristen, Informationspflicht
- "Kunde muss einstehen" reicht NICHT — wir sind Auftragsverarbeiter
