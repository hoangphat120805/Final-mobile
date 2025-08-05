from fastapi import APIRouter

from app.api.endpoints import auth
from app.api.endpoints import user
from app.api.endpoints import category
from app.api.endpoints import order

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(user.router)
api_router.include_router(category.router)
api_router.include_router(order.router)

