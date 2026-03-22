import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from jose import jwt
from app.auth import get_current_user


def test_health_endpoint_is_public(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_get_current_user_with_invalid_token():
    mock_creds = HTTPAuthorizationCredentials(
        scheme="Bearer", credentials="invalid-token"
    )
    with pytest.raises(HTTPException) as exc_info:
        import asyncio
        asyncio.run(get_current_user(mock_creds))
    assert exc_info.value.status_code == 401


def test_get_current_user_with_valid_token():
    import os
    secret = os.environ["SUPABASE_JWT_SECRET"]
    token = jwt.encode(
        {"sub": "test-user-id", "email": "test@test.de", "aud": "authenticated"},
        secret,
        algorithm="HS256",
    )
    mock_creds = HTTPAuthorizationCredentials(
        scheme="Bearer", credentials=token
    )
    import asyncio
    result = asyncio.run(get_current_user(mock_creds))
    assert result["user_id"] == "test-user-id"
    assert result["email"] == "test@test.de"
