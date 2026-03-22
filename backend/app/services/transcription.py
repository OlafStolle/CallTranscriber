import openai
import tempfile
import os
from app.config import settings
from app.models.schemas import TranscriptionResult, TranscriptSegment

client = openai.OpenAI(api_key=settings.openai_api_key)


async def transcribe_audio(audio_data: bytes, filename: str = "audio.wav") -> TranscriptionResult:
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
            text=response.text, language=response.language or "de", segments=segments
        )
    finally:
        os.unlink(tmp_path)
