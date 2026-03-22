import io
import pytest
from unittest.mock import patch, AsyncMock
from app.main import app
from app.auth import get_current_user


def test_upload_requires_auth(client):
    response = client.post("/upload", files={"audio": ("test.wav", b"fake", "audio/wav")})
    assert response.status_code == 403


@patch("app.routers.upload.process_upload", new_callable=AsyncMock)
def test_upload_accepts_audio(mock_process, client):
    mock_process.return_value = None

    async def override_get_current_user():
        return {"user_id": "test-user", "email": "test@test.de"}

    app.dependency_overrides[get_current_user] = override_get_current_user
    try:
        audio_data = io.BytesIO(b"\x00" * 1024)
        with patch("app.services.db.create_call", new_callable=AsyncMock, return_value={"id": "call-123"}):
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
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    data = response.json()
    assert data["call_id"] == "call-123"
    assert data["status"] == "uploading"
