from fastapi import APIRouter

from app.api.endpoints import auth
from app.api.endpoints import user
from app.api.endpoints import category

from app.api.endpoints import order
from app.api.endpoints import transaction

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(user.router)
api_router.include_router(category.router)
api_router.include_router(order.router, prefix="/orders")

api_router.include_router(transaction.router, prefix="/transactions", tags=["transactions"])

