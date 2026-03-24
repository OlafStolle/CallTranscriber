from pydantic import computed_field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    supabase_url: str
    supabase_service_key: str
    supabase_jwt_secret: str
    openai_api_key: str
    upload_max_size_mb: int = 100
    allowed_origins_str: str = "http://localhost:3000"
    model_config = {"env_file": ".env", "env_prefix": ""}

    @computed_field
    @property
    def allowed_origins(self) -> list[str]:
        return [o.strip() for o in self.allowed_origins_str.split(",")]


settings = Settings()
