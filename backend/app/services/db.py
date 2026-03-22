from app.services.storage import get_supabase


async def create_call(user_id: str, call_data: dict) -> dict:
    client = await get_supabase()
    result = await client.table("calls").insert({"user_id": user_id, **call_data}).execute()
    return result.data[0]


async def create_transcript(call_id: str, transcription: dict) -> dict:
    client = await get_supabase()
    result = await client.table("transcripts").insert({
        "call_id": call_id,
        "text": transcription["text"],
        "language": transcription["language"],
        "segments": [
            s.model_dump() if hasattr(s, "model_dump") else s
            for s in transcription.get("segments", [])
        ],
    }).execute()
    return result.data[0]


async def create_audio_file(call_id: str, storage_path: str, size_bytes: int, format: str = "wav") -> dict:
    client = await get_supabase()
    result = await client.table("audio_files").insert({
        "call_id": call_id,
        "storage_path": storage_path,
        "size_bytes": size_bytes,
        "format": format,
    }).execute()
    return result.data[0]


async def update_call_status(call_id: str, status: str) -> None:
    client = await get_supabase()
    await client.table("calls").update({"status": status}).eq("id", call_id).execute()
