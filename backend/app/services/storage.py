from supabase import create_client
from app.config import settings

_client = None


def get_supabase():
    global _client
    if _client is None:
        _client = create_client(settings.supabase_url, settings.supabase_service_key)
    return _client


async def upload_audio(user_id: str, call_id: str, audio_data: bytes, filename: str) -> str:
    client = get_supabase()
    path = f"{user_id}/{call_id}/{filename}"
    client.storage.from_("call-recordings").upload(path, audio_data, {"content-type": "audio/wav"})
    return path
