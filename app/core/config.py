
from pydantic_settings import SettingsConfigDict, BaseSettings

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file="./.env",
        env_ignore_empty=True,
        extra="ignore"
    )

    POSTGRES_HOST: str
    POSTGRES_PORT: int
    POSTGRES_USER: str
    POSTGRES_PASSWORD: str
    POSTGRES_DB: str
    POSTGRES_URL: str

    PROJECT_NAME: str
    API_V1_STR: str = "/api/v1"

    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60*24
    SECRET_KEY: str 

settings = Settings()