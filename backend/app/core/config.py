
from pydantic_settings import SettingsConfigDict, BaseSettings

from pydantic import (
    EmailStr
)

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file="./.env",
        env_ignore_empty=True,
        extra="ignore"
    )

    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_USER: str = "user"
    POSTGRES_PASSWORD: str = "password"
    POSTGRES_DB: str = "database"
    POSTGRES_URL: str = "postgresql://user:password@localhost:5432/database"

    PROJECT_NAME: str = "My Project"
    API_STR: str = "/api"

    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60*24
    SECRET_KEY: str = "mysecretkey"

    SMTP_TLS: bool = True
    SMTP_SSL: bool = False
    SMTP_PORT: int = 587
    SMTP_HOST: str | None = None
    SMTP_USER: str | None = None
    SMTP_PASSWORD: str | None = None
    EMAILS_FROM_EMAIL: EmailStr | None = None
    EMAILS_FROM_NAME: EmailStr | None = None
    EMAIL_TOKEN_EXPIRE_HOURS: int = 15

    IMGBB_API_KEY: str = "imgbb_api_key"

    MAPBOX_ACCESS_TOKEN: str = "mapbox_access_token"

    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_DB: int = 0

    DEFAULT_AVATAR_URL: str = "https://i.ibb.co/5xt2NvW0/453178253-471506465671661-2781666950760530985-n.png"

    CLOUD_NAME: str = "cloud_name"
    CLOUD_API_KEY: str = "cloud_api_key"
    CLOUD_API_SECRET: str = "cloud_api_secret"
    
settings = Settings()