from pydantic import field_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    supabase_url: str
    supabase_service_key: str
    supabase_jwt_secret: str
    openai_api_key: str
    upload_max_size_mb: int = 100
    allowed_origins: list[str] = ["http://localhost:3000"]
    model_config = {"env_file": ".env"}

    @field_validator("allowed_origins", mode="before")
    @classmethod
    def parse_origins(cls, v: object) -> object:
        if isinstance(v, str) and not v.startswith("["):
            return [o.strip() for o in v.split(",")]
        return v


settings = Settings()
