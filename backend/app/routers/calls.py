# backend/app/routers/calls.py
from fastapi import APIRouter, Depends, HTTPException
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
        raise HTTPException(status_code=404, detail="Call not found")

    # Delete audio from storage
    audio = client.table("audio_files").select("storage_path").eq("call_id", call_id).execute()
    for f in audio.data:
        client.storage.from_("call-recordings").remove([f["storage_path"]])

    # Cascade delete handles transcripts and audio_files
    client.table("calls").delete().eq("id", call_id).execute()

    return {"status": "deleted"}
