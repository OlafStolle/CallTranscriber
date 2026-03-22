from unittest.mock import patch, MagicMock
import pytest


@pytest.mark.asyncio
async def test_transcribe_audio_returns_result():
    mock_response = MagicMock()
    mock_response.text = "Hallo, wie geht es Ihnen?"
    mock_response.language = "de"
    mock_response.segments = [{"start": 0.0, "end": 2.5, "text": "Hallo, wie geht es Ihnen?"}]
    with patch("app.services.transcription.client") as mock_client:
        mock_client.audio.transcriptions.create.return_value = mock_response
        from app.services.transcription import transcribe_audio
        result = await transcribe_audio(b"\x00" * 1024)
    assert result.text == "Hallo, wie geht es Ihnen?"
    assert result.language == "de"
    assert len(result.segments) == 1
