from fastapi import APIRouter

from app.api.v1.endpoints import auth

api_routerv1 = APIRouter()
api_routerv1.include_router(auth.router)
