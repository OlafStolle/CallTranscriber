import logging
from fastapi import APIRouter, Depends, File, Form, UploadFile, HTTPException, BackgroundTasks
from datetime import datetime
from app.auth import get_current_user
from app.models.schemas import UploadResponse
from app.services import storage, transcription, db

logger = logging.getLogger(__name__)
router = APIRouter()


async def process_upload(user_id: str, call_id: str, audio_data: bytes, filename: str):
    try:
        storage_path = await storage.upload_audio(user_id, call_id, audio_data, filename)
        await db.create_audio_file(call_id, storage_path, len(audio_data))
        await db.update_call_status(call_id, "transcribing")
        result = await transcription.transcribe_audio(audio_data, filename)
        await db.create_transcript(call_id, result.model_dump())
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
    if not audio.content_type or not audio.content_type.startswith("audio/"):
        raise HTTPException(status_code=400, detail="File must be audio")
    audio_data = await audio.read()
    if len(audio_data) > 100 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="File too large (max 100MB)")
    call = await db.create_call(user["user_id"], {
        "remote_number": remote_number,
        "direction": direction,
        "started_at": started_at.isoformat(),
        "ended_at": ended_at.isoformat(),
        "duration_seconds": duration_seconds,
        "status": "uploading",
    })
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
