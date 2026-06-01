"""Application configuration via environment variables."""

from pathlib import Path

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Model service configuration."""

    host: str = "0.0.0.0"
    port: int = 8501
    data_dir: Path = Path("data")
    max_model_size_mb: int = 500
    model_cache_ttl: int = 3600  # seconds, 0 = no expiry
    log_level: str = "info"

    model_config = {"env_prefix": "MODEL_SERVICE_", "env_file": ".env"}


settings = Settings()
