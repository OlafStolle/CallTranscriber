## 2026-03-23 01:00 — Phase 1+2 Call Transcriber komplett
- **Was:** Vollstaendige Implementierung Phase 1 (Backend + Web + Android) + Phase 2 Code-Tasks (BuildConfig, SIP, UI Polish, ProGuard)
- **Wo:** /mnt/volume/Projects/call-transcriber — 28 Commits auf main
- **Ergebnis:**
  - Backend: 6/6 Tests PASSED, async OpenAI + Supabase, DSGVO Delete, Docker
  - Web: Login/Register visuell getestet (Playwright Screenshots), Call-Liste, Transkript, Export
  - Android: 34 Kotlin-Dateien, BuildConfig Credentials, SIP Logging, Theme, Permissions, ProGuard
- **Details:**
  - Code Review (opus) fand 7 Issues → alle gefixt (async clients, input validation, RLS)
  - UploadWorker Bug gefixt: shared auth session statt neue Instanz
  - Web UI visuell verifiziert mit Playwright Screenshots

## 2026-03-24 00:00 — Mic-Recorder Migration + VPS Deploy
- **Was:** SIP/linphone komplett entfernt, Mikrofon-Recorder implementiert, Backend+Web auf VPS deployed
- **Wo:** VPS (72.60.181.127), Self-hosted Supabase
- **Ergebnis:**
  - Android: 5/5 Mic-Recorder Tasks abgeschlossen (CallDetector, AudioRecord Service, Recording UI)
  - Supabase: 7 Migrations angewandt (calls, transcripts, audio_files, RLS, fulltext, storage)
  - Backend: http://calltrans-api.72.60.181.127.sslip.io/health → {"status":"ok"}
  - Web: http://calltrans-web.72.60.181.127.sslip.io → Login-Seite rendert
  - Backend→Supabase Verbindung verifiziert: "Supabase connected! Calls: 0 rows"
- **Details:**
  - Coolify Traefik v3.6 als Reverse Proxy, sslip.io Domains
  - Architecture Decisions dokumentiert (ADR-1 bis ADR-7)
  - Deploy + Migration Scripts erstellt
  - OPENAI_API_KEY fehlt noch (Placeholder)
