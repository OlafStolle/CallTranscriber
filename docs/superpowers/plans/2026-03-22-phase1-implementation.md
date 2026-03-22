# Phase 1: Call Transcriber — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a VoIP call recording app (Android) with cloud transcription and a personal account where all transcripts are visible on phone and desktop.

**Architecture:** Supabase (Auth, PostgreSQL, Storage, Realtime) as data layer. FastAPI on VPS as processing backend (upload, transcription). Android Kotlin app with Zadarma SIP, Foreground Service recording, encrypted local storage, WorkManager upload. Next.js web dashboard for desktop access.

**Tech Stack:** Kotlin + Jetpack Compose + linphone-sdk, FastAPI + supabase-py + openai, Supabase (Auth/DB/Storage/Realtime), Next.js + @supabase/ssr, PostgreSQL with RLS + full-text search.

---

## File Structure

### Supabase (Database + Config)
```
supabase/
  migrations/
    001_create_calls_table.sql
    002_create_transcripts_table.sql
    003_create_audio_files_table.sql
    004_enable_rls_policies.sql
    005_create_fulltext_index.sql
  config.toml
  seed.sql
```

### Cloud Backend (FastAPI)
```
backend/
  app/
    __init__.py
    main.py                  # FastAPI app, CORS, lifespan
    config.py                # Settings via pydantic-settings
    auth.py                  # JWT validation (Supabase tokens)
    routers/
      __init__.py
      upload.py              # POST /upload — receive audio, store, trigger transcription
      calls.py               # GET /calls, GET /calls/{id} — (optional, Supabase direct preferred)
      health.py              # GET /health
    services/
      __init__.py
      transcription.py       # OpenAI Whisper/GPT-4o-transcribe wrapper
      storage.py             # Supabase Storage upload
      db.py                  # Supabase DB writes (calls, transcripts)
    models/
      __init__.py
      schemas.py             # Pydantic models for API request/response
  tests/
    __init__.py
    conftest.py
    test_auth.py
    test_upload.py
    test_transcription.py
  requirements.txt
  Dockerfile
  docker-compose.yml
```

### Android App (Kotlin)
```
android/
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/calltranscriber/
        CallTranscriberApp.kt          # Application class, Supabase init
        di/
          AppModule.kt                 # Hilt DI module
        data/
          local/
            CallDatabase.kt            # Room DB
            CallDao.kt                 # Room DAO
            CallEntity.kt              # Room entity
          remote/
            SupabaseClient.kt          # Supabase Kotlin SDK wrapper
            ApiClient.kt               # Retrofit/Ktor for FastAPI upload
          repository/
            CallRepository.kt          # Single source of truth (Room + Supabase)
            AuthRepository.kt          # Auth operations
        sip/
          SipManager.kt               # linphone-sdk wrapper, SIP registration, call management
          SipConfig.kt                 # Zadarma SIP credentials, SRTP config
        service/
          CallRecordingService.kt      # Foreground Service, AudioRecord, encoding
          CallConnectionService.kt     # ConnectionService for Telecom API
        upload/
          UploadWorker.kt             # WorkManager worker for audio upload
        ui/
          theme/
            Theme.kt
            Color.kt
            Type.kt
          auth/
            LoginScreen.kt
            RegisterScreen.kt
            AuthViewModel.kt
          calls/
            CallListScreen.kt
            CallDetailScreen.kt
            CallListViewModel.kt
          dialer/
            DialerScreen.kt
            DialerViewModel.kt
          navigation/
            NavGraph.kt
            Screen.kt
      res/
        values/strings.xml
    src/test/
      java/com/calltranscriber/
        CallRepositoryTest.kt
        AuthRepositoryTest.kt
        UploadWorkerTest.kt
```

### Web Dashboard (Next.js)
```
web/
  package.json
  next.config.js
  tailwind.config.js
  tsconfig.json
  .env.local.example
  src/
    app/
      layout.tsx               # Root layout, Supabase provider
      page.tsx                 # Redirect to /calls or /login
      login/
        page.tsx               # Login page
      register/
        page.tsx               # Register page
      calls/
        page.tsx               # Call list with search/filter
        [id]/
          page.tsx             # Call detail with transcript
      api/
        auth/
          callback/
            route.ts           # Supabase auth callback
    lib/
      supabase/
        client.ts              # Browser Supabase client
        server.ts              # Server Supabase client
        middleware.ts           # Auth middleware
      types.ts                 # TypeScript types matching DB schema
    components/
      CallList.tsx
      CallCard.tsx
      TranscriptView.tsx
      SearchBar.tsx
      ExportButton.tsx
      AuthForm.tsx
    middleware.ts              # Next.js middleware for auth redirect
```

---

## Task 1: Project Scaffolding + Git Init

**Files:**
- Create: `.gitignore`, `README.md`, `CLAUDE.md` (update)
- Create: `backend/`, `android/`, `web/`, `supabase/` directories

- [ ] **Step 1: Initialize git repo**

```bash
cd /mnt/volume/Projects/call-transcriber
git init
```

- [ ] **Step 2: Create .gitignore**

```gitignore
# Python
__pycache__/
*.pyc
.venv/
.env

# Android
android/.gradle/
android/build/
android/app/build/
android/local.properties
*.apk

# Node
node_modules/
.next/
web/.env.local

# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
```

- [ ] **Step 3: Create directory structure**

```bash
mkdir -p backend/app/{routers,services,models} backend/tests
mkdir -p supabase/migrations
mkdir -p android/app/src/main/java/com/calltranscriber/{di,data/{local,remote,repository},sip,service,upload,ui/{theme,auth,calls,dialer,navigation}}
mkdir -p android/app/src/main/res/values
mkdir -p android/app/src/test/java/com/calltranscriber
mkdir -p web/src/{app/{login,register,calls,api/auth/callback},lib/supabase,components}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: scaffold project structure for Phase 1"
```

---

## Task 2: Supabase Schema + RLS

**Files:**
- Create: `supabase/migrations/001_create_calls_table.sql`
- Create: `supabase/migrations/002_create_transcripts_table.sql`
- Create: `supabase/migrations/003_create_audio_files_table.sql`
- Create: `supabase/migrations/004_enable_rls_policies.sql`
- Create: `supabase/migrations/005_create_fulltext_index.sql`
- Create: `supabase/seed.sql`

- [ ] **Step 1: Create calls table migration**

```sql
-- supabase/migrations/001_create_calls_table.sql
CREATE TABLE public.calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    remote_number TEXT NOT NULL,
    direction TEXT NOT NULL CHECK (direction IN ('inbound', 'outbound')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    status TEXT NOT NULL DEFAULT 'recording' CHECK (status IN ('recording', 'uploading', 'transcribing', 'completed', 'failed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_calls_user_id ON public.calls(user_id);
CREATE INDEX idx_calls_started_at ON public.calls(started_at DESC);
```

- [ ] **Step 2: Create transcripts table migration**

```sql
-- supabase/migrations/002_create_transcripts_table.sql
CREATE TABLE public.transcripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL REFERENCES public.calls(id) ON DELETE CASCADE,
    text TEXT NOT NULL DEFAULT '',
    language TEXT DEFAULT 'de',
    segments JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transcripts_call_id ON public.transcripts(call_id);
```

- [ ] **Step 3: Create audio_files table migration**

```sql
-- supabase/migrations/003_create_audio_files_table.sql
CREATE TABLE public.audio_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL REFERENCES public.calls(id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    format TEXT NOT NULL DEFAULT 'wav',
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audio_files_call_id ON public.audio_files(call_id);
```

- [ ] **Step 4: Enable RLS policies**

```sql
-- supabase/migrations/004_enable_rls_policies.sql
ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transcripts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audio_files ENABLE ROW LEVEL SECURITY;

-- Calls: user can only see/modify their own
CREATE POLICY "Users can view own calls"
    ON public.calls FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own calls"
    ON public.calls FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own calls"
    ON public.calls FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own calls"
    ON public.calls FOR DELETE
    USING (auth.uid() = user_id);

-- Transcripts: via call ownership
CREATE POLICY "Users can view own transcripts"
    ON public.transcripts FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM public.calls
        WHERE calls.id = transcripts.call_id
        AND calls.user_id = auth.uid()
    ));

CREATE POLICY "Service role can insert transcripts"
    ON public.transcripts FOR INSERT
    WITH CHECK (true);

-- Audio files: via call ownership
CREATE POLICY "Users can view own audio files"
    ON public.audio_files FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM public.calls
        WHERE calls.id = audio_files.call_id
        AND calls.user_id = auth.uid()
    ));

CREATE POLICY "Service role can insert audio files"
    ON public.audio_files FOR INSERT
    WITH CHECK (true);
```

- [ ] **Step 5: Create full-text search index**

```sql
-- supabase/migrations/005_create_fulltext_index.sql
ALTER TABLE public.transcripts ADD COLUMN IF NOT EXISTS fts tsvector
    GENERATED ALWAYS AS (to_tsvector('german', coalesce(text, ''))) STORED;

CREATE INDEX idx_transcripts_fts ON public.transcripts USING GIN(fts);

-- Search function
CREATE OR REPLACE FUNCTION search_transcripts(search_query TEXT, user_uuid UUID)
RETURNS TABLE (
    call_id UUID,
    remote_number TEXT,
    direction TEXT,
    started_at TIMESTAMPTZ,
    transcript_text TEXT,
    rank REAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.id AS call_id,
        c.remote_number,
        c.direction,
        c.started_at,
        t.text AS transcript_text,
        ts_rank(t.fts, websearch_to_tsquery('german', search_query)) AS rank
    FROM public.transcripts t
    JOIN public.calls c ON c.id = t.call_id
    WHERE c.user_id = user_uuid
    AND t.fts @@ websearch_to_tsquery('german', search_query)
    ORDER BY rank DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

- [ ] **Step 6: Create seed data**

```sql
-- supabase/seed.sql
-- Seed data is inserted via Supabase Auth + API, not direct SQL
-- This file documents the expected test data structure
-- Test user: created via Supabase Auth UI or API
-- Test calls: created via FastAPI upload endpoint
```

- [ ] **Step 7: Apply migrations to Supabase**

```bash
# If using Supabase CLI:
supabase db push
# Or apply manually via Supabase Dashboard SQL editor
```

- [ ] **Step 8: Commit**

```bash
git add supabase/
git commit -m "feat: add Supabase schema with RLS and full-text search"
```

---

## Task 3: FastAPI Backend — Project Setup + Auth

**Files:**
- Create: `backend/requirements.txt`
- Create: `backend/app/__init__.py`, `backend/app/main.py`, `backend/app/config.py`, `backend/app/auth.py`
- Create: `backend/app/routers/__init__.py`, `backend/app/routers/health.py`
- Create: `backend/tests/__init__.py`, `backend/tests/conftest.py`, `backend/tests/test_auth.py`

- [ ] **Step 1: Create requirements.txt**

```
fastapi==0.115.6
uvicorn[standard]==0.34.0
python-multipart==0.0.18
pydantic-settings==2.7.1
supabase==2.11.0
openai==1.58.1
python-jose[cryptography]==3.3.0
httpx==0.28.1
pytest==8.3.4
pytest-asyncio==0.25.0
```

- [ ] **Step 2: Write failing test for auth**

```python
# backend/tests/conftest.py
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def client():
    from app.main import app
    return TestClient(app)


@pytest.fixture
def valid_token():
    """A mock JWT that would be issued by Supabase Auth."""
    return "test-valid-token"


@pytest.fixture
def auth_headers(valid_token):
    return {"Authorization": f"Bearer {valid_token}"}
```

```python
# backend/tests/test_auth.py
import pytest
from fastapi import HTTPException
from app.auth import get_current_user


def test_missing_token_raises_401(client):
    """Request without Authorization header should return 401."""
    response = client.get("/health")
    # Health endpoint is public, so this should work
    assert response.status_code == 200


def test_get_current_user_with_invalid_token():
    """Invalid token should raise HTTPException 401."""
    from fastapi.security import HTTPAuthorizationCredentials
    mock_creds = HTTPAuthorizationCredentials(scheme="Bearer", credentials="invalid-token")
    with pytest.raises(HTTPException) as exc_info:
        import asyncio
        asyncio.run(get_current_user(mock_creds))
    assert exc_info.value.status_code == 401
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd /mnt/volume/Projects/call-transcriber/backend
python -m pytest tests/test_auth.py -v
```

Expected: FAIL (modules not found)

- [ ] **Step 4: Implement config**

```python
# backend/app/config.py
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    supabase_url: str
    supabase_service_key: str
    supabase_jwt_secret: str
    openai_api_key: str
    upload_max_size_mb: int = 100
    allowed_origins: list[str] = ["http://localhost:3000"]

    model_config = {"env_file": ".env"}


settings = Settings()
```

- [ ] **Step 5: Implement auth**

```python
# backend/app/auth.py
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from app.config import settings

security = HTTPBearer()


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> dict:
    """Validate Supabase JWT and return user payload."""
    token = credentials.credentials
    try:
        payload = jwt.decode(
            token,
            settings.supabase_jwt_secret,
            algorithms=["HS256"],
            audience="authenticated",
        )
        user_id = payload.get("sub")
        if user_id is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token: missing sub claim",
            )
        return {"user_id": user_id, "email": payload.get("email")}
    except JWTError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid token: {e}",
        )
```

- [ ] **Step 6: Implement main app + health router**

```python
# backend/app/__init__.py
```

```python
# backend/app/routers/__init__.py
```

```python
# backend/app/routers/health.py
from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
async def health_check():
    return {"status": "ok"}
```

```python
# backend/app/main.py
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import health


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield


app = FastAPI(title="Call Transcriber API", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, tags=["health"])
```

- [ ] **Step 7: Create .env for local dev**

```bash
# backend/.env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_KEY=your-service-key
SUPABASE_JWT_SECRET=your-jwt-secret
OPENAI_API_KEY=sk-your-key
```

- [ ] **Step 8: Run tests**

```bash
cd /mnt/volume/Projects/call-transcriber/backend
python -m pytest tests/ -v
```

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/
git commit -m "feat: FastAPI backend with auth and health endpoint"
```

---

## Task 4: FastAPI Backend — Upload + Transcription Service

**Files:**
- Create: `backend/app/models/schemas.py`
- Create: `backend/app/services/storage.py`, `backend/app/services/transcription.py`, `backend/app/services/db.py`
- Create: `backend/app/routers/upload.py`
- Create: `backend/tests/test_upload.py`, `backend/tests/test_transcription.py`

- [ ] **Step 1: Write Pydantic schemas**

```python
# backend/app/models/__init__.py
```

```python
# backend/app/models/schemas.py
from pydantic import BaseModel
from datetime import datetime


class CallCreate(BaseModel):
    remote_number: str
    direction: str  # "inbound" | "outbound"
    started_at: datetime
    ended_at: datetime
    duration_seconds: int


class TranscriptSegment(BaseModel):
    start: float
    end: float
    text: str


class TranscriptionResult(BaseModel):
    text: str
    language: str
    segments: list[TranscriptSegment]


class UploadResponse(BaseModel):
    call_id: str
    status: str
    message: str
```

- [ ] **Step 2: Write failing test for upload endpoint**

```python
# backend/tests/test_upload.py
import io
import pytest
from unittest.mock import patch, AsyncMock


def test_upload_requires_auth(client):
    """Upload without token should return 403."""
    response = client.post("/upload", files={"audio": ("test.wav", b"fake", "audio/wav")})
    assert response.status_code == 403


@patch("app.routers.upload.process_upload", new_callable=AsyncMock)
def test_upload_accepts_audio(mock_process, client):
    """Upload with valid auth should accept audio file."""
    mock_process.return_value = {"call_id": "test-id", "status": "transcribing", "message": "ok"}

    # Mock auth
    with patch("app.auth.get_current_user", return_value={"user_id": "test-user", "email": "test@test.de"}):
        audio_data = io.BytesIO(b"\x00" * 1024)
        response = client.post(
            "/upload",
            files={"audio": ("test.wav", audio_data, "audio/wav")},
            data={
                "remote_number": "+491234567890",
                "direction": "outbound",
                "started_at": "2026-03-22T10:00:00Z",
                "ended_at": "2026-03-22T10:05:00Z",
                "duration_seconds": "300",
            },
        )
    assert response.status_code == 200
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd /mnt/volume/Projects/call-transcriber/backend
python -m pytest tests/test_upload.py -v
```

Expected: FAIL

- [ ] **Step 4: Implement storage service**

```python
# backend/app/services/__init__.py
```

```python
# backend/app/services/storage.py
from supabase import create_client
from app.config import settings

_client = None


def get_supabase():
    global _client
    if _client is None:
        _client = create_client(settings.supabase_url, settings.supabase_service_key)
    return _client


async def upload_audio(user_id: str, call_id: str, audio_data: bytes, filename: str) -> str:
    """Upload audio to Supabase Storage. Returns storage path."""
    client = get_supabase()
    path = f"{user_id}/{call_id}/{filename}"
    client.storage.from_("call-recordings").upload(
        path,
        audio_data,
        {"content-type": "audio/wav"},
    )
    return path
```

- [ ] **Step 5: Implement transcription service**

```python
# backend/app/services/transcription.py
import openai
import tempfile
import os
from app.config import settings
from app.models.schemas import TranscriptionResult, TranscriptSegment

client = openai.OpenAI(api_key=settings.openai_api_key)


async def transcribe_audio(audio_data: bytes, filename: str = "audio.wav") -> TranscriptionResult:
    """Transcribe audio using OpenAI Whisper API. Returns text + segments."""
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp.write(audio_data)
        tmp_path = tmp.name

    try:
        with open(tmp_path, "rb") as audio_file:
            response = client.audio.transcriptions.create(
                model="whisper-1",
                file=audio_file,
                language="de",
                response_format="verbose_json",
                timestamp_granularities=["segment"],
            )

        segments = []
        if hasattr(response, "segments") and response.segments:
            segments = [
                TranscriptSegment(start=s["start"], end=s["end"], text=s["text"])
                for s in response.segments
            ]

        return TranscriptionResult(
            text=response.text,
            language=response.language or "de",
            segments=segments,
        )
    finally:
        os.unlink(tmp_path)
```

- [ ] **Step 6: Implement DB service**

```python
# backend/app/services/db.py
from app.services.storage import get_supabase


async def create_call(user_id: str, call_data: dict) -> dict:
    """Insert a call record. Returns the created row."""
    client = get_supabase()
    result = client.table("calls").insert({
        "user_id": user_id,
        **call_data,
    }).execute()
    return result.data[0]


async def create_transcript(call_id: str, transcription: dict) -> dict:
    """Insert a transcript record."""
    client = get_supabase()
    result = client.table("transcripts").insert({
        "call_id": call_id,
        "text": transcription["text"],
        "language": transcription["language"],
        "segments": [s.model_dump() if hasattr(s, "model_dump") else s for s in transcription.get("segments", [])],
    }).execute()
    return result.data[0]


async def create_audio_file(call_id: str, storage_path: str, size_bytes: int, format: str = "wav") -> dict:
    """Insert an audio file record."""
    client = get_supabase()
    result = client.table("audio_files").insert({
        "call_id": call_id,
        "storage_path": storage_path,
        "size_bytes": size_bytes,
        "format": format,
    }).execute()
    return result.data[0]


async def update_call_status(call_id: str, status: str) -> None:
    """Update call status."""
    client = get_supabase()
    client.table("calls").update({"status": status}).eq("id", call_id).execute()
```

- [ ] **Step 7: Implement upload router**

```python
# backend/app/routers/upload.py
import logging
from fastapi import APIRouter, Depends, File, Form, UploadFile, HTTPException, BackgroundTasks
from datetime import datetime
from app.auth import get_current_user
from app.models.schemas import UploadResponse
from app.services import storage, transcription, db

logger = logging.getLogger(__name__)
router = APIRouter()


async def process_upload(
    user_id: str,
    call_id: str,
    audio_data: bytes,
    filename: str,
):
    """Background task: upload to storage, transcribe, save transcript."""
    try:
        # Upload audio to Supabase Storage
        storage_path = await storage.upload_audio(user_id, call_id, audio_data, filename)
        await db.create_audio_file(call_id, storage_path, len(audio_data))

        # Update status to transcribing
        await db.update_call_status(call_id, "transcribing")

        # Transcribe
        result = await transcription.transcribe_audio(audio_data, filename)
        await db.create_transcript(call_id, result.model_dump())

        # Done
        await db.update_call_status(call_id, "completed")
        logger.info(f"Call {call_id} transcribed successfully")

    except Exception as e:
        logger.error(f"Failed to process call {call_id}: {e}")
        await db.update_call_status(call_id, "failed")


@router.post("/upload", response_model=UploadResponse)
async def upload_audio(
    background_tasks: BackgroundTasks,
    audio: UploadFile = File(...),
    remote_number: str = Form(...),
    direction: str = Form(...),
    started_at: datetime = Form(...),
    ended_at: datetime = Form(...),
    duration_seconds: int = Form(...),
    user: dict = Depends(get_current_user),
):
    """Receive audio upload, create call record, trigger background transcription."""
    # Validate file type
    if not audio.content_type or not audio.content_type.startswith("audio/"):
        raise HTTPException(status_code=400, detail="File must be audio")

    audio_data = await audio.read()
    max_size = 100 * 1024 * 1024  # 100MB
    if len(audio_data) > max_size:
        raise HTTPException(status_code=413, detail="File too large (max 100MB)")

    # Create call record
    call = await db.create_call(user["user_id"], {
        "remote_number": remote_number,
        "direction": direction,
        "started_at": started_at.isoformat(),
        "ended_at": ended_at.isoformat(),
        "duration_seconds": duration_seconds,
        "status": "uploading",
    })

    # Trigger background processing
    background_tasks.add_task(
        process_upload,
        user["user_id"],
        call["id"],
        audio_data,
        audio.filename or "recording.wav",
    )

    return UploadResponse(
        call_id=call["id"],
        status="uploading",
        message="Audio received, transcription started",
    )
```

- [ ] **Step 8: Register upload router in main.py**

```python
# Update backend/app/main.py — add import and include_router
from app.routers import health, upload

# After health router:
app.include_router(upload.router, tags=["upload"])
```

- [ ] **Step 9: Write transcription test**

```python
# backend/tests/test_transcription.py
from unittest.mock import patch, MagicMock
import pytest


@pytest.mark.asyncio
async def test_transcribe_audio_returns_result():
    """Transcription service should return TranscriptionResult."""
    mock_response = MagicMock()
    mock_response.text = "Hallo, wie geht es Ihnen?"
    mock_response.language = "de"
    mock_response.segments = [
        {"start": 0.0, "end": 2.5, "text": "Hallo, wie geht es Ihnen?"}
    ]

    with patch("app.services.transcription.client") as mock_client:
        mock_client.audio.transcriptions.create.return_value = mock_response

        from app.services.transcription import transcribe_audio
        result = await transcribe_audio(b"\x00" * 1024)

    assert result.text == "Hallo, wie geht es Ihnen?"
    assert result.language == "de"
    assert len(result.segments) == 1
```

- [ ] **Step 10: Run all tests**

```bash
cd /mnt/volume/Projects/call-transcriber/backend
python -m pytest tests/ -v
```

Expected: ALL PASS

- [ ] **Step 11: Commit**

```bash
git add backend/
git commit -m "feat: upload endpoint with transcription and Supabase storage"
```

---

## Task 5: FastAPI Backend — Docker + Deployment

**Files:**
- Create: `backend/Dockerfile`
- Create: `docker-compose.yml` (project root)

- [ ] **Step 1: Create Dockerfile**

```dockerfile
# backend/Dockerfile
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ app/

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

- [ ] **Step 2: Create docker-compose.yml**

```yaml
# docker-compose.yml
services:
  backend:
    build: ./backend
    ports:
      - "8000:8000"
    env_file:
      - ./backend/.env
    restart: unless-stopped
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.call-transcriber.rule=Host(`transcriber-api.yourdomain.com`)"
      - "traefik.http.routers.call-transcriber.tls.certresolver=letsencrypt"
```

- [ ] **Step 3: Test build locally**

```bash
cd /mnt/volume/Projects/call-transcriber
docker compose build backend
docker compose up -d backend
curl http://localhost:8000/health
```

Expected: `{"status": "ok"}`

- [ ] **Step 4: Commit**

```bash
git add backend/Dockerfile docker-compose.yml
git commit -m "feat: Docker setup for FastAPI backend"
```

---

## Task 6: Android App — Project Setup + Supabase Auth

**Files:**
- Create: Android project via Android Studio template OR manual Gradle setup
- Create: `android/app/build.gradle.kts` with dependencies
- Create: `CallTranscriberApp.kt`, `AppModule.kt`
- Create: `SupabaseClient.kt`, `AuthRepository.kt`
- Create: `LoginScreen.kt`, `RegisterScreen.kt`, `AuthViewModel.kt`
- Create: `NavGraph.kt`, `Screen.kt`

- [ ] **Step 1: Initialize Android project with Gradle**

Create `android/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CallTranscriber"
include(":app")
```

- [ ] **Step 2: Configure app/build.gradle.kts with dependencies**

```kotlin
// android/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("kapt")
}

android {
    namespace = "com.calltranscriber"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.calltranscriber"
        minSdk = 29  // Android 10+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Supabase Kotlin SDK
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:3.0.2")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.53.1")
    kapt("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (local cache)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Security (encrypted files)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Ktor (HTTP client for FastAPI upload)
    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-android:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
}
```

- [ ] **Step 3: Implement SupabaseClient wrapper**

```kotlin
// android/app/src/main/java/com/calltranscriber/data/remote/SupabaseClient.kt
package com.calltranscriber.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://your-project.supabase.co",
        supabaseKey = "your-anon-key",
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}
```

- [ ] **Step 4: Implement AuthRepository**

```kotlin
// android/app/src/main/java/com/calltranscriber/data/repository/AuthRepository.kt
package com.calltranscriber.data.repository

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import com.calltranscriber.data.remote.SupabaseClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {

    private val auth = SupabaseClientProvider.client.auth

    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    val isLoggedIn: Boolean
        get() = auth.currentUserOrNull() != null

    suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun getAccessToken(): String? {
        return auth.currentAccessTokenOrNull()
    }
}
```

- [ ] **Step 5: Implement Auth UI (LoginScreen + RegisterScreen + ViewModel)**

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/auth/AuthViewModel.kt
package com.calltranscriber.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState(isLoggedIn = authRepository.isLoggedIn))
    val state = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.signIn(email, password)
                _state.value = _state.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.signUp(email, password)
                _state.value = _state.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _state.value = AuthState(isLoggedIn = false)
        }
    }
}
```

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/auth/LoginScreen.kt
package com.calltranscriber.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Call Transcriber", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.signIn(email, password) },
            enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp))
            else Text("Anmelden")
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("Noch kein Konto? Registrieren")
        }
    }
}
```

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/auth/RegisterScreen.kt
package com.calltranscriber.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onRegisterSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Konto erstellen", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Passwort bestaetigen") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.signUp(email, password) },
            enabled = !state.isLoading && email.isNotBlank() && password == confirmPassword && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp))
            else Text("Registrieren")
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Bereits ein Konto? Anmelden")
        }
    }
}
```

- [ ] **Step 6: Implement Navigation**

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/navigation/Screen.kt
package com.calltranscriber.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object CallList : Screen("calls")
    data object CallDetail : Screen("calls/{callId}") {
        fun createRoute(callId: String) = "calls/$callId"
    }
    data object Dialer : Screen("dialer")
}
```

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/navigation/NavGraph.kt
package com.calltranscriber.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.calltranscriber.ui.auth.LoginScreen
import com.calltranscriber.ui.auth.RegisterScreen

@Composable
fun NavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.CallList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.CallList.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.CallList.route) {
            // CallListScreen — implemented in Task 8
        }
    }
}
```

- [ ] **Step 7: Create Application class + Hilt module**

```kotlin
// android/app/src/main/java/com/calltranscriber/CallTranscriberApp.kt
package com.calltranscriber

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CallTranscriberApp : Application()
```

```kotlin
// android/app/src/main/java/com/calltranscriber/di/AppModule.kt
package com.calltranscriber.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule
```

- [ ] **Step 8: AndroidManifest.xml**

```xml
<!-- android/app/src/main/AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <application
        android:name=".CallTranscriberApp"
        android:label="Call Transcriber"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- ConnectionService for Telecom API -->
        <service
            android:name=".service.CallConnectionService"
            android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.ConnectionService" />
            </intent-filter>
        </service>

        <!-- Foreground Service for Recording -->
        <service
            android:name=".service.CallRecordingService"
            android:foregroundServiceType="microphone"
            android:exported="false" />

    </application>
</manifest>
```

- [ ] **Step 9: Build and verify compilation**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add android/
git commit -m "feat: Android app with Supabase auth, login/register UI, navigation"
```

---

## Task 7: Android App — SIP Client + ConnectionService

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/sip/SipConfig.kt`
- Create: `android/app/src/main/java/com/calltranscriber/sip/SipManager.kt`
- Create: `android/app/src/main/java/com/calltranscriber/service/CallConnectionService.kt`

**Note:** This task uses linphone-sdk for SIP. Add to build.gradle.kts:
```kotlin
implementation("org.linphone:linphone-sdk-android:5.3.74")
```

- [ ] **Step 1: Implement SipConfig**

```kotlin
// android/app/src/main/java/com/calltranscriber/sip/SipConfig.kt
package com.calltranscriber.sip

data class SipConfig(
    val domain: String = "sip.zadarma.com",
    val port: Int = 5060,
    val transport: String = "TLS",
    val username: String = "",  // Zadarma SIP login
    val password: String = "",  // Zadarma SIP password
    val useSrtp: Boolean = true,
)
```

- [ ] **Step 2: Implement SipManager**

```kotlin
// android/app/src/main/java/com/calltranscriber/sip/SipManager.kt
package com.calltranscriber.sip

import android.content.Context
import org.linphone.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SipRegistrationState { NONE, REGISTERING, REGISTERED, FAILED }
enum class CallState { IDLE, RINGING, CONNECTED, ENDED }

@Singleton
class SipManager @Inject constructor(
    private val context: Context,
) {
    private lateinit var core: Core

    private val _registrationState = MutableStateFlow(SipRegistrationState.NONE)
    val registrationState = _registrationState.asStateFlow()

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    var currentCall: Call? = null
        private set

    fun initialize(config: SipConfig) {
        val factory = Factory.instance()
        core = factory.createCore(null, null, context)

        // Enable SRTP
        if (config.useSrtp) {
            core.mediaEncryption = MediaEncryption.SRTP
            core.isMediaEncryptionMandatory = true
        }

        // Create auth info
        val authInfo = factory.createAuthInfo(
            config.username, null, config.password,
            null, null, config.domain,
        )
        core.addAuthInfo(authInfo)

        // Create SIP account
        val accountParams = core.createAccountParams()
        val identity = factory.createAddress("sip:${config.username}@${config.domain}")
        accountParams.identityAddress = identity

        val serverAddress = factory.createAddress("sip:${config.domain}:${config.port}")
        serverAddress?.transport = when (config.transport) {
            "TLS" -> TransportType.Tls
            "TCP" -> TransportType.Tcp
            else -> TransportType.Udp
        }
        accountParams.serverAddress = serverAddress
        accountParams.isRegisterEnabled = true

        val account = core.createAccount(accountParams)
        core.addAccount(account)
        core.defaultAccount = account

        // Listener
        core.addListener(object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(
                core: Core, account: Account,
                state: RegistrationState, message: String,
            ) {
                _registrationState.value = when (state) {
                    RegistrationState.Progress -> SipRegistrationState.REGISTERING
                    RegistrationState.Ok -> SipRegistrationState.REGISTERED
                    RegistrationState.Failed -> SipRegistrationState.FAILED
                    else -> SipRegistrationState.NONE
                }
            }

            override fun onCallStateChanged(
                core: Core, call: Call,
                state: Call.State, message: String,
            ) {
                currentCall = call
                _callState.value = when (state) {
                    Call.State.IncomingReceived, Call.State.OutgoingRinging -> CallState.RINGING
                    Call.State.StreamsRunning -> CallState.CONNECTED
                    Call.State.End, Call.State.Released, Call.State.Error -> {
                        currentCall = null
                        CallState.ENDED
                    }
                    else -> _callState.value
                }
            }
        })

        core.start()
    }

    fun makeCall(number: String): Call? {
        val address = core.interpretUrl("sip:$number@${core.defaultAccount?.params?.domain}")
            ?: return null
        return core.inviteAddress(address)
    }

    fun answerCall() {
        currentCall?.accept()
    }

    fun hangUp() {
        currentCall?.terminate()
    }

    fun stop() {
        core.stop()
    }

    /**
     * Get the audio playback device for recording remote audio.
     * linphone-sdk provides this via the core's audio device list.
     */
    fun getCore(): Core = core
}
```

- [ ] **Step 3: Implement CallConnectionService**

```kotlin
// android/app/src/main/java/com/calltranscriber/service/CallConnectionService.kt
package com.calltranscriber.service

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class CallConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = CallConnection()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setActive()
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = CallConnection()
        connection.setAddress(
            request?.extras?.getParcelable("android.telecom.extra.INCOMING_CALL_ADDRESS") as? Uri,
            TelecomManager.PRESENTATION_ALLOWED,
        )
        connection.setRinging()
        return connection
    }

    inner class CallConnection : Connection() {
        init {
            connectionProperties = PROPERTY_SELF_MANAGED
            audioModeIsVoip = true
        }

        override fun onAnswer() {
            setActive()
        }

        override fun onDisconnect() {
            setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
            destroy()
        }

        override fun onAbort() {
            setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.CANCELED))
            destroy()
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "feat: SIP client with linphone-sdk, SRTP, ConnectionService"
```

---

## Task 8: Android App — Foreground Service Recording + Encrypted Storage

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (already done in Task 6)

- [ ] **Step 1: Implement CallRecordingService**

**Important:** We use linphone-sdk's built-in call recording (`call.params.recordFile`) which captures BOTH sides of the conversation (local mic + remote audio mixed). This is critical — Android's AudioRecord alone would only capture the local microphone. linphone-sdk mixes both streams into a single WAV file.

```kotlin
// android/app/src/main/java/com/calltranscriber/service/CallRecordingService.kt
package com.calltranscriber.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.calltranscriber.sip.SipManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class CallRecordingService : Service() {

    @Inject lateinit var sipManager: SipManager

    companion object {
        const val CHANNEL_ID = "call_recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.calltranscriber.START_RECORDING"
        const val ACTION_STOP = "com.calltranscriber.STOP_RECORDING"
        const val EXTRA_CALL_ID = "call_id"
    }

    private var currentCallId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return START_NOT_STICKY
                currentCallId = callId
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording(callId)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(callId: String) {
        // linphone-sdk records BOTH sides (local + remote) into a single file
        val recordFile = File(filesDir, "recordings/$callId.wav")
        recordFile.parentFile?.mkdirs()

        val call = sipManager.currentCall ?: return
        val params = sipManager.getCore().createCallParams(call)
        params?.recordFile = recordFile.absolutePath
        call.update(params)
        call.startRecording()
    }

    private fun stopRecording() {
        sipManager.currentCall?.stopRecording()

        // Encrypt the recorded file
        val callId = currentCallId ?: return
        val plainFile = File(filesDir, "recordings/$callId.wav")
        if (plainFile.exists()) {
            encryptFile(plainFile, callId)
        }
        currentCallId = null
    }

    private fun encryptFile(sourceFile: File, callId: String) {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedOutputFile = File(filesDir, "recordings_encrypted/$callId.wav.enc")
        encryptedOutputFile.parentFile?.mkdirs()

        val encryptedFile = EncryptedFile.Builder(
            this,
            encryptedOutputFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

        encryptedFile.openFileOutput().use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        // Delete unencrypted file
        sourceFile.delete()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gespraech wird aufgezeichnet",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gespraech wird aufgezeichnet")
            .setContentText("Aufnahme laeuft...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: Commit**

```bash
git add android/
git commit -m "feat: foreground service recording with encrypted storage"
```

---

## Task 9: Android App — WorkManager Upload + Call Repository

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/upload/UploadWorker.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/remote/ApiClient.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/local/CallDatabase.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/local/CallDao.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/local/CallEntity.kt`
- Create: `android/app/src/main/java/com/calltranscriber/data/repository/CallRepository.kt`

- [ ] **Step 1: Implement Room entities + DAO**

```kotlin
// android/app/src/main/java/com/calltranscriber/data/local/CallEntity.kt
package com.calltranscriber.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val id: String,
    val remoteNumber: String,
    val direction: String,
    val startedAt: Long,  // epoch millis
    val endedAt: Long?,
    val durationSeconds: Int?,
    val status: String,
    val transcriptText: String? = null,
    val audioFilePath: String? = null,  // local encrypted path
    val syncedToCloud: Boolean = false,
)
```

```kotlin
// android/app/src/main/java/com/calltranscriber/data/local/CallDao.kt
package com.calltranscriber.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startedAt DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): CallEntity?

    @Query("SELECT * FROM calls WHERE transcriptText LIKE '%' || :query || '%' ORDER BY startedAt DESC")
    fun searchCalls(query: String): Flow<List<CallEntity>>

    @Upsert
    suspend fun upsertCall(call: CallEntity)

    @Upsert
    suspend fun upsertCalls(calls: List<CallEntity>)

    @Query("SELECT * FROM calls WHERE syncedToCloud = 0")
    suspend fun getUnsyncedCalls(): List<CallEntity>

    @Query("UPDATE calls SET syncedToCloud = 1 WHERE id = :callId")
    suspend fun markSynced(callId: String)

    @Query("UPDATE calls SET transcriptText = :text WHERE id = :callId")
    suspend fun updateTranscript(callId: String, text: String)
}
```

```kotlin
// android/app/src/main/java/com/calltranscriber/data/local/CallDatabase.kt
package com.calltranscriber.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CallEntity::class], version = 1)
abstract class CallDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
}
```

- [ ] **Step 2: Implement ApiClient for FastAPI upload**

```kotlin
// android/app/src/main/java/com/calltranscriber/data/remote/ApiClient.kt
package com.calltranscriber.data.remote

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

class ApiClient(
    private val baseUrl: String = "https://transcriber-api.yourdomain.com",
) {
    private val client = HttpClient(Android)

    suspend fun uploadAudio(
        token: String,
        callId: String,
        audioFile: File,
        remoteNumber: String,
        direction: String,
        startedAt: String,
        endedAt: String,
        durationSeconds: Int,
    ): HttpResponse {
        return client.submitFormWithBinaryData(
            url = "$baseUrl/upload",
            formData = formData {
                append("remote_number", remoteNumber)
                append("direction", direction)
                append("started_at", startedAt)
                append("ended_at", endedAt)
                append("duration_seconds", durationSeconds.toString())
                append("audio", audioFile.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"$callId.wav\"")
                })
            },
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
```

- [ ] **Step 3: Implement UploadWorker**

```kotlin
// android/app/src/main/java/com/calltranscriber/upload/UploadWorker.kt
package com.calltranscriber.upload

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.work.*
import com.calltranscriber.data.remote.ApiClient
import com.calltranscriber.data.repository.AuthRepository
import java.io.File
import java.io.FileOutputStream

class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val callId = inputData.getString("call_id") ?: return Result.failure()
        val remoteNumber = inputData.getString("remote_number") ?: return Result.failure()
        val direction = inputData.getString("direction") ?: return Result.failure()
        val startedAt = inputData.getString("started_at") ?: return Result.failure()
        val endedAt = inputData.getString("ended_at") ?: return Result.failure()
        val durationSeconds = inputData.getInt("duration_seconds", 0)

        // Decrypt the file for upload
        val encryptedFilePath = File(applicationContext.filesDir, "recordings_encrypted/$callId.wav.enc")
        if (!encryptedFilePath.exists()) return Result.failure()

        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedFile = EncryptedFile.Builder(
            applicationContext,
            encryptedFilePath,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

        // Read decrypted content to temp file
        val tempFile = File.createTempFile("upload_", ".wav", applicationContext.cacheDir)
        try {
            encryptedFile.openFileInput().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Get auth token
            val authRepo = AuthRepository()
            val token = authRepo.getAccessToken() ?: return Result.retry()

            // Upload
            val apiClient = ApiClient()
            val response = apiClient.uploadAudio(
                token = token,
                callId = callId,
                audioFile = tempFile,
                remoteNumber = remoteNumber,
                direction = direction,
                startedAt = startedAt,
                endedAt = endedAt,
                durationSeconds = durationSeconds,
            )

            return if (response.status.value in 200..299) {
                Result.success()
            } else {
                Result.retry()
            }
        } finally {
            tempFile.delete()
        }
    }

    companion object {
        fun enqueue(context: Context, callId: String, remoteNumber: String, direction: String, startedAt: String, endedAt: String, durationSeconds: Int) {
            val data = workDataOf(
                "call_id" to callId,
                "remote_number" to remoteNumber,
                "direction" to direction,
                "started_at" to startedAt,
                "ended_at" to endedAt,
                "duration_seconds" to durationSeconds,
            )

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("upload_$callId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
```

- [ ] **Step 4: Implement CallRepository (single source of truth)**

```kotlin
// android/app/src/main/java/com/calltranscriber/data/repository/CallRepository.kt
package com.calltranscriber.data.repository

import com.calltranscriber.data.local.CallDao
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CallWithTranscript(
    val id: String,
    val user_id: String,
    val remote_number: String,
    val direction: String,
    val started_at: String,
    val ended_at: String?,
    val duration_seconds: Int?,
    val status: String,
)

@Serializable
data class TranscriptRow(
    val id: String,
    val call_id: String,
    val text: String,
    val language: String,
)

@Singleton
class CallRepository @Inject constructor(
    private val callDao: CallDao,
) {
    private val supabase = SupabaseClientProvider.client

    /** Local-first: returns Flow from Room DB */
    fun getAllCalls(): Flow<List<CallEntity>> = callDao.getAllCalls()

    fun searchCalls(query: String): Flow<List<CallEntity>> = callDao.searchCalls(query)

    suspend fun getCallById(callId: String): CallEntity? = callDao.getCallById(callId)

    /** Sync from Supabase to local Room DB */
    suspend fun syncFromCloud() {
        val calls = supabase.postgrest["calls"]
            .select()
            .decodeList<CallWithTranscript>()

        val entities = calls.map { call ->
            // Fetch transcript if exists
            val transcripts = supabase.postgrest["transcripts"]
                .select { filter { eq("call_id", call.id) } }
                .decodeList<TranscriptRow>()

            CallEntity(
                id = call.id,
                remoteNumber = call.remote_number,
                direction = call.direction,
                startedAt = parseTimestamp(call.started_at),
                endedAt = call.ended_at?.let { parseTimestamp(it) },
                durationSeconds = call.duration_seconds,
                status = call.status,
                transcriptText = transcripts.firstOrNull()?.text,
                syncedToCloud = true,
            )
        }
        callDao.upsertCalls(entities)
    }

    /** Save a new local call (before upload) */
    suspend fun saveLocalCall(call: CallEntity) {
        callDao.upsertCall(call)
    }

    private fun parseTimestamp(ts: String): Long {
        return try {
            java.time.Instant.parse(ts).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add android/
git commit -m "feat: WorkManager upload, Room DB cache, CallRepository"
```

---

## Task 10: Android App — Call List UI + Dialer

**Files:**
- Create: `android/app/src/main/java/com/calltranscriber/ui/calls/CallListScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/calls/CallDetailScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/calls/CallListViewModel.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerScreen.kt`
- Create: `android/app/src/main/java/com/calltranscriber/ui/dialer/DialerViewModel.kt`

- [ ] **Step 1: Implement CallListViewModel**

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/calls/CallListViewModel.kt
package com.calltranscriber.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallListViewModel @Inject constructor(
    private val callRepository: CallRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val calls: StateFlow<List<CallEntity>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) callRepository.getAllCalls()
            else callRepository.searchCalls(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Sync from cloud on start
        viewModelScope.launch {
            try { callRepository.syncFromCloud() } catch (_: Exception) {}
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
```

- [ ] **Step 2: Implement CallListScreen**

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/calls/CallListScreen.kt
package com.calltranscriber.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.data.local.CallEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallListScreen(
    onCallClick: (String) -> Unit,
    onDialerClick: () -> Unit,
    viewModel: CallListViewModel = hiltViewModel(),
) {
    val calls by viewModel.calls.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Gespraeche") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onDialerClick) {
                Icon(Icons.Default.Phone, contentDescription = "Anrufen")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                label = { Text("Suchen...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )

            if (calls.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Gespraeche", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(calls, key = { it.id }) { call ->
                        CallCard(call = call, onClick = { onCallClick(call.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun CallCard(call: CallEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    call.remoteNumber,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (call.direction == "inbound") "Eingehend" else "Ausgehend",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                dateFormat.format(Date(call.startedAt)),
                style = MaterialTheme.typography.bodySmall,
            )
            call.durationSeconds?.let { dur ->
                Text(
                    "${dur / 60}:${String.format("%02d", dur % 60)} Min",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            // Preview of transcript
            call.transcriptText?.let { text ->
                Text(
                    text.take(120) + if (text.length > 120) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            } ?: Text(
                call.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
```

- [ ] **Step 3: Implement CallDetailScreen**

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/calls/CallDetailScreen.kt
package com.calltranscriber.ui.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val callRepository: CallRepository,
) : ViewModel() {

    private val callId: String = savedStateHandle["callId"] ?: ""

    private val _call = MutableStateFlow<CallEntity?>(null)
    val call = _call.asStateFlow()

    init {
        viewModelScope.launch {
            _call.value = callRepository.getCallById(callId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    onBack: () -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel(),
) {
    val call by viewModel.call.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(call?.remoteNumber ?: "Gespraech") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck")
                    }
                },
            )
        },
    ) { padding ->
        call?.let { c ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Meta info
                Text("Nummer: ${c.remoteNumber}", style = MaterialTheme.typography.titleMedium)
                Text("Richtung: ${if (c.direction == "inbound") "Eingehend" else "Ausgehend"}")
                Text("Datum: ${dateFormat.format(Date(c.startedAt))}")
                c.durationSeconds?.let { Text("Dauer: ${it / 60}:${String.format("%02d", it % 60)} Min") }
                Text("Status: ${c.status}")

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Transcript
                Text("Transkript", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                c.transcriptText?.let { text ->
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                } ?: Text(
                    "Transkript wird erstellt...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Implement DialerScreen + ViewModel**

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/dialer/DialerViewModel.kt
package com.calltranscriber.ui.dialer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calltranscriber.data.local.CallEntity
import com.calltranscriber.data.repository.CallRepository
import com.calltranscriber.sip.CallState
import com.calltranscriber.sip.SipManager
import com.calltranscriber.service.CallRecordingService
import com.calltranscriber.upload.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val sipManager: SipManager,
    private val callRepository: CallRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    val callState = sipManager.callState

    private var currentCallId: String? = null
    private var callStartTime: Instant? = null

    fun onNumberChanged(number: String) {
        _phoneNumber.value = number
    }

    fun makeCall() {
        val number = _phoneNumber.value
        if (number.isBlank()) return

        currentCallId = UUID.randomUUID().toString()
        callStartTime = Instant.now()

        sipManager.makeCall(number)

        // Start recording via foreground service
        val intent = android.content.Intent(context, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_START
            putExtra(CallRecordingService.EXTRA_CALL_ID, currentCallId)
        }
        context.startForegroundService(intent)

        // Save local call record
        viewModelScope.launch {
            callRepository.saveLocalCall(
                CallEntity(
                    id = currentCallId!!,
                    remoteNumber = number,
                    direction = "outbound",
                    startedAt = callStartTime!!.toEpochMilli(),
                    endedAt = null,
                    durationSeconds = null,
                    status = "recording",
                ),
            )
        }
    }

    fun hangUp() {
        sipManager.hangUp()

        // Stop recording
        val intent = android.content.Intent(context, CallRecordingService::class.java).apply {
            action = CallRecordingService.ACTION_STOP
        }
        context.startService(intent)

        // Update call record and enqueue upload
        val callId = currentCallId ?: return
        val startTime = callStartTime ?: return
        val endTime = Instant.now()
        val duration = (endTime.epochSecond - startTime.epochSecond).toInt()

        viewModelScope.launch {
            callRepository.saveLocalCall(
                CallEntity(
                    id = callId,
                    remoteNumber = _phoneNumber.value,
                    direction = "outbound",
                    startedAt = startTime.toEpochMilli(),
                    endedAt = endTime.toEpochMilli(),
                    durationSeconds = duration,
                    status = "uploading",
                ),
            )

            UploadWorker.enqueue(
                context = context,
                callId = callId,
                remoteNumber = _phoneNumber.value,
                direction = "outbound",
                startedAt = startTime.toString(),
                endedAt = endTime.toString(),
                durationSeconds = duration,
            )
        }

        currentCallId = null
        callStartTime = null
    }
}
```

```kotlin
// android/app/src/main/java/com/calltranscriber/ui/dialer/DialerScreen.kt
package com.calltranscriber.ui.dialer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calltranscriber.sip.CallState

@Composable
fun DialerScreen(
    viewModel: DialerViewModel = hiltViewModel(),
) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val callState by viewModel.callState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Anrufen", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = viewModel::onNumberChanged,
            label = { Text("Telefonnummer") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        when (callState) {
            CallState.IDLE, CallState.ENDED -> {
                Button(
                    onClick = { viewModel.makeCall() },
                    enabled = phoneNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Anrufen")
                }
            }
            CallState.RINGING -> {
                Text("Verbindung wird aufgebaut...", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.hangUp() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Auflegen")
                }
            }
            CallState.CONNECTED -> {
                Text(
                    "Gespraech laeuft — wird aufgezeichnet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.hangUp() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Auflegen")
                }
            }
        }
    }
}
```

- [ ] **Step 5: Update NavGraph with all screens**

Update `NavGraph.kt` to include CallList, CallDetail, and Dialer composables from the new screens (add the composable routes referencing the new screens).

- [ ] **Step 6: Build and verify**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add android/
git commit -m "feat: call list, detail, dialer UI with search and offline cache"
```

---

## Task 11: Web Dashboard — Next.js + Supabase Auth

**Files:**
- Create: `web/package.json`, `web/next.config.js`, `web/tailwind.config.js`, `web/tsconfig.json`
- Create: `web/.env.local.example`
- Create: `web/src/lib/supabase/client.ts`, `web/src/lib/supabase/server.ts`, `web/src/lib/supabase/middleware.ts`
- Create: `web/src/lib/types.ts`
- Create: `web/src/middleware.ts`
- Create: `web/src/app/layout.tsx`, `web/src/app/page.tsx`
- Create: `web/src/app/login/page.tsx`, `web/src/app/register/page.tsx`
- Create: `web/src/app/api/auth/callback/route.ts`

- [ ] **Step 1: Initialize Next.js project**

```bash
cd /mnt/volume/Projects/call-transcriber/web
npx create-next-app@latest . --typescript --tailwind --eslint --app --src-dir --no-import-alias
```

- [ ] **Step 2: Install Supabase dependencies**

```bash
cd /mnt/volume/Projects/call-transcriber/web
npm install @supabase/supabase-js @supabase/ssr
```

- [ ] **Step 3: Create .env.local.example**

```
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
```

- [ ] **Step 4: Implement Supabase clients**

```typescript
// web/src/lib/supabase/client.ts
import { createBrowserClient } from "@supabase/ssr";

export function createClient() {
  return createBrowserClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
  );
}
```

```typescript
// web/src/lib/supabase/server.ts
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";

export async function createServerSupabaseClient() {
  const cookieStore = await cookies();

  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll(cookiesToSet) {
          try {
            cookiesToSet.forEach(({ name, value, options }) =>
              cookieStore.set(name, value, options),
            );
          } catch {
            // Server Component — ignore
          }
        },
      },
    },
  );
}
```

```typescript
// web/src/lib/supabase/middleware.ts
import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

export async function updateSession(request: NextRequest) {
  let supabaseResponse = NextResponse.next({ request });

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value, options }) =>
            request.cookies.set(name, value),
          );
          supabaseResponse = NextResponse.next({ request });
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options),
          );
        },
      },
    },
  );

  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user && !request.nextUrl.pathname.startsWith("/login") && !request.nextUrl.pathname.startsWith("/register") && !request.nextUrl.pathname.startsWith("/api/auth")) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    return NextResponse.redirect(url);
  }

  return supabaseResponse;
}
```

```typescript
// web/src/middleware.ts
import { updateSession } from "@/lib/supabase/middleware";
import type { NextRequest } from "next/server";

export async function middleware(request: NextRequest) {
  return await updateSession(request);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"],
};
```

- [ ] **Step 5: Implement TypeScript types**

```typescript
// web/src/lib/types.ts
export interface Call {
  id: string;
  user_id: string;
  remote_number: string;
  direction: "inbound" | "outbound";
  started_at: string;
  ended_at: string | null;
  duration_seconds: number | null;
  status: "recording" | "uploading" | "transcribing" | "completed" | "failed";
  created_at: string;
}

export interface Transcript {
  id: string;
  call_id: string;
  text: string;
  language: string;
  segments: TranscriptSegment[];
  created_at: string;
}

export interface TranscriptSegment {
  start: number;
  end: number;
  text: string;
}

export interface AudioFile {
  id: string;
  call_id: string;
  storage_path: string;
  size_bytes: number;
  format: string;
}
```

- [ ] **Step 6: Implement auth callback route**

```typescript
// web/src/app/api/auth/callback/route.ts
import { createServerSupabaseClient } from "@/lib/supabase/server";
import { NextResponse } from "next/server";

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get("code");

  if (code) {
    const supabase = await createServerSupabaseClient();
    await supabase.auth.exchangeCodeForSession(code);
  }

  return NextResponse.redirect(`${origin}/calls`);
}
```

- [ ] **Step 7: Implement Login page**

```typescript
// web/src/app/login/page.tsx
"use client";

import { createClient } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const supabase = createClient();

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);

    const { error } = await supabase.auth.signInWithPassword({ email, password });

    if (error) {
      setError(error.message);
      setLoading(false);
    } else {
      router.push("/calls");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg shadow">
        <h2 className="text-3xl font-bold text-center">Call Transcriber</h2>
        <form onSubmit={handleLogin} className="space-y-4">
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 border rounded-md"
            required
          />
          <input
            type="password"
            placeholder="Passwort"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border rounded-md"
            required
          />
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? "Wird angemeldet..." : "Anmelden"}
          </button>
        </form>
        <p className="text-center text-sm">
          Noch kein Konto?{" "}
          <a href="/register" className="text-blue-600 hover:underline">Registrieren</a>
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 8: Implement Register page**

```typescript
// web/src/app/register/page.tsx
"use client";

import { createClient } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const supabase = createClient();

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirmPassword) {
      setError("Passwoerter stimmen nicht ueberein");
      return;
    }
    setLoading(true);
    setError(null);

    const { error } = await supabase.auth.signUp({ email, password });

    if (error) {
      setError(error.message);
      setLoading(false);
    } else {
      router.push("/calls");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg shadow">
        <h2 className="text-3xl font-bold text-center">Konto erstellen</h2>
        <form onSubmit={handleRegister} className="space-y-4">
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 border rounded-md"
            required
          />
          <input
            type="password"
            placeholder="Passwort (min. 8 Zeichen)"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border rounded-md"
            required
            minLength={8}
          />
          <input
            type="password"
            placeholder="Passwort bestaetigen"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="w-full px-3 py-2 border rounded-md"
            required
          />
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? "Wird registriert..." : "Registrieren"}
          </button>
        </form>
        <p className="text-center text-sm">
          Bereits ein Konto?{" "}
          <a href="/login" className="text-blue-600 hover:underline">Anmelden</a>
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 9: Implement signout route**

```typescript
// web/src/app/api/auth/signout/route.ts
import { createServerSupabaseClient } from "@/lib/supabase/server";
import { NextResponse } from "next/server";

export async function POST(request: Request) {
  const supabase = await createServerSupabaseClient();
  await supabase.auth.signOut();
  const { origin } = new URL(request.url);
  return NextResponse.redirect(`${origin}/login`, { status: 302 });
}
```

- [ ] **Step 10: Commit**

```bash
git add web/
git commit -m "feat: Next.js web dashboard with Supabase auth"
```

---

## Task 12: Web Dashboard — Call List + Transcript View + Search + Export

**Files:**
- Create: `web/src/app/calls/page.tsx`
- Create: `web/src/app/calls/[id]/page.tsx`
- Create: `web/src/components/CallList.tsx`
- Create: `web/src/components/CallCard.tsx`
- Create: `web/src/components/TranscriptView.tsx`
- Create: `web/src/components/SearchBar.tsx`
- Create: `web/src/components/ExportButton.tsx`
- Create: `web/src/app/layout.tsx` (update)

- [ ] **Step 1: Implement CallList component**

```typescript
// web/src/components/CallCard.tsx
import type { Call } from "@/lib/types";
import Link from "next/link";

export function CallCard({ call }: { call: Call & { transcript_text?: string } }) {
  const date = new Date(call.started_at).toLocaleString("de-DE");
  const duration = call.duration_seconds
    ? `${Math.floor(call.duration_seconds / 60)}:${String(call.duration_seconds % 60).padStart(2, "0")}`
    : "-";

  return (
    <Link href={`/calls/${call.id}`}>
      <div className="p-4 border rounded-lg hover:bg-gray-50 cursor-pointer">
        <div className="flex justify-between items-center">
          <span className="font-medium">{call.remote_number}</span>
          <span className="text-sm text-gray-500">
            {call.direction === "inbound" ? "Eingehend" : "Ausgehend"}
          </span>
        </div>
        <div className="text-sm text-gray-500 mt-1">
          {date} — {duration} Min
        </div>
        {call.transcript_text && (
          <p className="text-sm text-gray-600 mt-2 line-clamp-2">
            {call.transcript_text}
          </p>
        )}
        <span className={`text-xs mt-1 inline-block px-2 py-0.5 rounded ${
          call.status === "completed" ? "bg-green-100 text-green-800"
            : call.status === "failed" ? "bg-red-100 text-red-800"
            : "bg-yellow-100 text-yellow-800"
        }`}>
          {call.status}
        </span>
      </div>
    </Link>
  );
}
```

```typescript
// web/src/components/SearchBar.tsx
"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

export function SearchBar() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [query, setQuery] = useState(searchParams.get("q") || "");

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    const params = new URLSearchParams();
    if (query) params.set("q", query);
    router.push(`/calls?${params.toString()}`);
  }

  return (
    <form onSubmit={handleSearch} className="flex gap-2">
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Transkripte durchsuchen..."
        className="flex-1 px-3 py-2 border rounded-md"
      />
      <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">
        Suchen
      </button>
    </form>
  );
}
```

- [ ] **Step 2: Implement Calls list page (server component)**

```typescript
// web/src/app/calls/page.tsx
import { createServerSupabaseClient } from "@/lib/supabase/server";
import { CallCard } from "@/components/CallCard";
import { SearchBar } from "@/components/SearchBar";
import type { Call, Transcript } from "@/lib/types";

export default async function CallsPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q } = await searchParams;
  const supabase = await createServerSupabaseClient();

  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return null;

  let calls: (Call & { transcript_text?: string })[];

  if (q) {
    // Full-text search via RPC
    const { data } = await supabase.rpc("search_transcripts", {
      search_query: q,
      user_uuid: user.id,
    });
    calls = (data || []).map((row: any) => ({
      id: row.call_id,
      user_id: user.id,
      remote_number: row.remote_number,
      direction: row.direction,
      started_at: row.started_at,
      ended_at: null,
      duration_seconds: null,
      status: "completed" as const,
      created_at: row.started_at,
      transcript_text: row.transcript_text,
    }));
  } else {
    // All calls with transcript preview
    const { data: callData } = await supabase
      .from("calls")
      .select("*")
      .order("started_at", { ascending: false })
      .limit(50);

    const callIds = (callData || []).map((c: Call) => c.id);
    const { data: transcripts } = await supabase
      .from("transcripts")
      .select("call_id, text")
      .in_("call_id", callIds);

    const transcriptMap = new Map(
      (transcripts || []).map((t: any) => [t.call_id, t.text]),
    );

    calls = (callData || []).map((c: Call) => ({
      ...c,
      transcript_text: transcriptMap.get(c.id),
    }));
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Gespraeche</h1>
        <form action="/api/auth/signout" method="POST">
          <button className="text-sm text-gray-500 hover:text-gray-700">Abmelden</button>
        </form>
      </div>

      <div className="mb-6">
        <SearchBar />
      </div>

      <div className="space-y-3">
        {calls.length === 0 ? (
          <p className="text-center text-gray-500 py-12">Keine Gespraeche gefunden</p>
        ) : (
          calls.map((call) => <CallCard key={call.id} call={call} />)
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Implement Call Detail page with Transcript**

```typescript
// web/src/app/calls/[id]/page.tsx
import { createServerSupabaseClient } from "@/lib/supabase/server";
import { TranscriptView } from "@/components/TranscriptView";
import { ExportButton } from "@/components/ExportButton";
import Link from "next/link";
import type { Call, Transcript } from "@/lib/types";

export default async function CallDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const supabase = await createServerSupabaseClient();

  const { data: call } = await supabase
    .from("calls")
    .select("*")
    .eq("id", id)
    .single<Call>();

  const { data: transcript } = await supabase
    .from("transcripts")
    .select("*")
    .eq("call_id", id)
    .single<Transcript>();

  if (!call) return <p>Gespraech nicht gefunden</p>;

  const date = new Date(call.started_at).toLocaleString("de-DE");
  const duration = call.duration_seconds
    ? `${Math.floor(call.duration_seconds / 60)}:${String(call.duration_seconds % 60).padStart(2, "0")}`
    : "-";

  return (
    <div className="max-w-4xl mx-auto p-6">
      <Link href="/calls" className="text-blue-600 hover:underline text-sm">
        Zurueck zur Liste
      </Link>

      <div className="mt-4 mb-6">
        <h1 className="text-2xl font-bold">{call.remote_number}</h1>
        <div className="text-gray-500 mt-1">
          {call.direction === "inbound" ? "Eingehend" : "Ausgehend"} — {date} — {duration} Min
        </div>
      </div>

      <div className="flex gap-2 mb-6">
        {transcript && (
          <ExportButton callId={call.id} remoteNumber={call.remote_number} date={call.started_at} text={transcript.text} />
        )}
      </div>

      <hr className="mb-6" />

      <h2 className="text-xl font-semibold mb-4">Transkript</h2>
      {transcript ? (
        <TranscriptView transcript={transcript} />
      ) : (
        <p className="text-gray-500">Transkript wird erstellt...</p>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Implement TranscriptView + ExportButton**

```typescript
// web/src/components/TranscriptView.tsx
import type { Transcript } from "@/lib/types";

function formatTime(seconds: number): string {
  const min = Math.floor(seconds / 60);
  const sec = Math.floor(seconds % 60);
  return `${min}:${String(sec).padStart(2, "0")}`;
}

export function TranscriptView({ transcript }: { transcript: Transcript }) {
  if (transcript.segments.length > 0) {
    return (
      <div className="space-y-3">
        {transcript.segments.map((segment, i) => (
          <div key={i} className="flex gap-3">
            <span className="text-xs text-gray-400 font-mono min-w-[50px] pt-0.5">
              {formatTime(segment.start)}
            </span>
            <p className="text-gray-800">{segment.text}</p>
          </div>
        ))}
      </div>
    );
  }

  return <p className="text-gray-800 whitespace-pre-wrap">{transcript.text}</p>;
}
```

```typescript
// web/src/components/ExportButton.tsx
"use client";

export function ExportButton({
  callId,
  remoteNumber,
  date,
  text,
}: {
  callId: string;
  remoteNumber: string;
  date: string;
  text: string;
}) {
  function exportAsText() {
    const content = `Gespraech: ${remoteNumber}\nDatum: ${new Date(date).toLocaleString("de-DE")}\n\n${text}`;
    const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `transkript-${remoteNumber}-${date.slice(0, 10)}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  function exportAsCsv() {
    const rows = [
      ["Nummer", "Datum", "Transkript"],
      [remoteNumber, new Date(date).toLocaleString("de-DE"), `"${text.replace(/"/g, '""')}"`],
    ];
    const csv = rows.map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `transkript-${remoteNumber}-${date.slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="flex gap-2">
      <button
        onClick={exportAsText}
        className="px-3 py-1 text-sm border rounded hover:bg-gray-50"
      >
        TXT Export
      </button>
      <button
        onClick={exportAsCsv}
        className="px-3 py-1 text-sm border rounded hover:bg-gray-50"
      >
        CSV Export
      </button>
    </div>
  );
}
```

- [ ] **Step 5: Update root layout**

```typescript
// web/src/app/layout.tsx
import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Call Transcriber",
  description: "Persoenliches Konto fuer Gespraeche und Transkripte",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="de">
      <body>{children}</body>
    </html>
  );
}
```

```typescript
// web/src/app/page.tsx
import { redirect } from "next/navigation";

export default function Home() {
  redirect("/calls");
}
```

- [ ] **Step 6: Build and verify**

```bash
cd /mnt/volume/Projects/call-transcriber/web
npm run build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add web/
git commit -m "feat: web dashboard with call list, transcript view, search, export"
```

---

## Task 13: Web Dashboard — Docker + Deployment

**Files:**
- Create: `web/Dockerfile`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Create Dockerfile for web**

```dockerfile
# web/Dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

- [ ] **Step 2: Update docker-compose.yml**

```yaml
# Add to docker-compose.yml
  web:
    build: ./web
    ports:
      - "3000:3000"
    environment:
      - NEXT_PUBLIC_SUPABASE_URL=${SUPABASE_URL}
      - NEXT_PUBLIC_SUPABASE_ANON_KEY=${SUPABASE_ANON_KEY}
    restart: unless-stopped
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.call-transcriber-web.rule=Host(`transcriber.yourdomain.com`)"
      - "traefik.http.routers.call-transcriber-web.tls.certresolver=letsencrypt"
```

- [ ] **Step 3: Test build**

```bash
cd /mnt/volume/Projects/call-transcriber
docker compose build web
```

- [ ] **Step 4: Commit**

```bash
git add web/Dockerfile docker-compose.yml
git commit -m "feat: Docker setup for web dashboard"
```

---

## Task 14: Supabase Storage Bucket + DSGVO Basics

**Files:**
- Create: `supabase/migrations/006_create_storage_bucket.sql`

- [ ] **Step 1: Create Storage bucket via Supabase Dashboard or SQL**

```sql
-- supabase/migrations/006_create_storage_bucket.sql
INSERT INTO storage.buckets (id, name, public)
VALUES ('call-recordings', 'call-recordings', false);

-- RLS for storage: users can only access their own folder
CREATE POLICY "Users can read own recordings"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'call-recordings' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Service role can upload recordings"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'call-recordings');
```

- [ ] **Step 2: Add DSGVO deletion endpoint to FastAPI**

```python
# Add to backend/app/routers/calls.py
from fastapi import APIRouter, Depends
from app.auth import get_current_user
from app.services.storage import get_supabase

router = APIRouter()


@router.delete("/calls/{call_id}")
async def delete_call(call_id: str, user: dict = Depends(get_current_user)):
    """DSGVO: Delete a call and all associated data."""
    client = get_supabase()

    # Verify ownership
    call = client.table("calls").select("*").eq("id", call_id).eq("user_id", user["user_id"]).execute()
    if not call.data:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Call not found")

    # Delete audio from storage
    audio = client.table("audio_files").select("storage_path").eq("call_id", call_id).execute()
    for f in audio.data:
        client.storage.from_("call-recordings").remove([f["storage_path"]])

    # Cascade delete handles transcripts and audio_files
    client.table("calls").delete().eq("id", call_id).execute()

    return {"status": "deleted"}
```

- [ ] **Step 3: Register calls router in main.py**

```python
# Update backend/app/main.py
from app.routers import health, upload, calls

app.include_router(calls.router, tags=["calls"])
```

- [ ] **Step 4: Commit**

```bash
git add supabase/ backend/
git commit -m "feat: storage bucket with RLS, DSGVO delete endpoint"
```

---

## Task 15: Integration Test + End-to-End Verification

**Files:**
- Create: `backend/tests/test_integration.py`
- Modify: existing test files as needed

- [ ] **Step 1: Write integration test for full flow**

```python
# backend/tests/test_integration.py
"""
Integration test for the full upload → transcribe → store flow.
Requires: .env with valid Supabase and OpenAI credentials.
Run with: pytest tests/test_integration.py -v -m integration
"""
import pytest
import io
from unittest.mock import patch, MagicMock

pytestmark = pytest.mark.integration


def test_full_upload_flow(client):
    """Test: upload audio → call created → transcription triggered."""
    mock_user = {"user_id": "test-uuid", "email": "test@example.com"}

    with patch("app.auth.get_current_user", return_value=mock_user), \
         patch("app.services.db.create_call", return_value={"id": "call-123"}) as mock_create_call, \
         patch("app.routers.upload.process_upload") as mock_process:

        audio = io.BytesIO(b"\x00" * 2048)
        response = client.post(
            "/upload",
            files={"audio": ("test.wav", audio, "audio/wav")},
            data={
                "remote_number": "+491234567890",
                "direction": "outbound",
                "started_at": "2026-03-22T10:00:00Z",
                "ended_at": "2026-03-22T10:05:00Z",
                "duration_seconds": "300",
            },
        )

        assert response.status_code == 200
        data = response.json()
        assert data["call_id"] == "call-123"
        assert data["status"] == "uploading"
        mock_create_call.assert_called_once()
```

- [ ] **Step 2: Run all backend tests**

```bash
cd /mnt/volume/Projects/call-transcriber/backend
python -m pytest tests/ -v
```

Expected: ALL PASS

- [ ] **Step 3: Verify Android builds**

```bash
cd /mnt/volume/Projects/call-transcriber/android
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Verify web builds**

```bash
cd /mnt/volume/Projects/call-transcriber/web
npm run build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Verify Docker builds**

```bash
cd /mnt/volume/Projects/call-transcriber
docker compose build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "feat: integration tests and build verification for Phase 1"
```

---

## Summary of Dependencies

```
Task 1 (Scaffolding) → required by all
Task 2 (Supabase Schema) → required by Task 3, 4, 11, 12
Task 3 (FastAPI Auth) → required by Task 4
Task 4 (Upload + Transcription) → required by Task 5, 14
Task 5 (Backend Docker) → independent after Task 4
Task 6 (Android Setup + Auth) → required by Task 7, 8, 9, 10
Task 7 (SIP Client) → required by Task 10
Task 8 (Recording Service) → required by Task 9
Task 9 (Upload Worker + Repository) → required by Task 10
Task 10 (Android UI) → depends on 6, 7, 8, 9
Task 11 (Web Auth) → required by Task 12
Task 12 (Web Dashboard UI) → depends on 11
Task 13 (Web Docker) → depends on 12
Task 14 (Storage + DSGVO) → depends on 2, 4
Task 15 (Integration Tests) → depends on all
```

**Parallel tracks possible:**
- Track A: Tasks 2 → 3 → 4 → 5 → 14 (Backend)
- Track B: Tasks 6 → 7 → 8 → 9 → 10 (Android)
- Track C: Tasks 11 → 12 → 13 (Web)
- Track D: Task 15 (after A, B, C)
