from fastapi import APIRouter

from app.api.endpoints import auth
from app.api.endpoints import user
from app.api.endpoints import category

from app.api.endpoints import order
from app.api.endpoints import transaction
from app.api.endpoints import tracking
from app.api.endpoints import notification

from app.api.endpoints import chat
from app.api.endpoints import otp

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(user.router)
api_router.include_router(category.router)
api_router.include_router(order.router, prefix="/orders")
api_router.include_router(transaction.router, prefix="/transactions", tags=["transactions"])
api_router.include_router(tracking.router, prefix="/ws", tags=["websocket-tracking"])
api_router.include_router(notification.router)

api_router.include_router(chat.router)
api_router.include_router(otp.router)

