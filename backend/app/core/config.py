
from pydantic_settings import SettingsConfigDict, BaseSettings

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

settings = Settings()