from pydantic_settings import BaseSettings
from functools import lru_cache

class Settings(BaseSettings):
    PROJECT_NAME: str = "DeutschStart Content Server"
    VERSION: str = "0.1.0"
    API_V1_STR: str = "/api/v1"

    POSTGRES_USER: str = "postgres"
    POSTGRES_PASSWORD: str = "postgres"
    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_DB: str = "deutschstart"
    DATABASE_URL: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/deutschstart"

    REDIS_URL: str = "redis://localhost:6379/0"
    
    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = "minioadmin"
    MINIO_SECRET_KEY: str = "minioadmin"
    
    OPENAI_API_KEY: str = "sk-placeholder"

    class Config:
        env_file = ".env"
        env_file_encoding = 'utf-8'
        extra = 'ignore'

@lru_cache()
def get_settings():
    return Settings()

settings = get_settings()
