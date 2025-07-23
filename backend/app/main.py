from fastapi import FastAPI
from app.core.config import settings

from app.api.v1.router import api_routerv1

app = FastAPI(
    title=settings.PROJECT_NAME
)

app.include_router(api_routerv1, prefix=settings.API_V1_STR)

