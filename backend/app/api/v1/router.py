from fastapi import APIRouter

from app.api.v1.endpoints import auth
from app.api.v1.endpoints import user
from app.api.v1.endpoints import category

api_routerv1 = APIRouter()
api_routerv1.include_router(auth.router)
api_routerv1.include_router(user.router)
api_routerv1.include_router(category.router)

