from pydantic import BaseModel
from datetime import datetime


class CallCreate(BaseModel):
    remote_number: str
    direction: str
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
