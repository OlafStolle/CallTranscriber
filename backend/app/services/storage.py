from supabase._async.client import create_client as acreate_client, AsyncClient
from app.config import settings

_client: AsyncClient | None = None


async def get_supabase() -> AsyncClient:
    global _client
    if _client is None:
        _client = await acreate_client(settings.supabase_url, settings.supabase_service_key)
    return _client


async def upload_audio(user_id: str, call_id: str, audio_data: bytes, filename: str) -> str:
    client = await get_supabase()
    path = f"{user_id}/{call_id}/{filename}"
    await client.storage.from_("call-recordings").upload(path, audio_data, {"content-type": "audio/wav"})
    return path
