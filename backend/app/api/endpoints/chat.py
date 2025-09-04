import time
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends

from app import crud
from app.schemas.chat import MessageCreate
from app.schemas.user import UserPublic
from app.api.deps import SessionDep, CurrentUser, get_current_user_ws
from typing import Dict, Annotated
from sqlmodel import Session
import asyncio

from uuid import UUID
from fastapi import Depends
from app.schemas.chat import Message

router = APIRouter(prefix="/chat", tags=["chat"])

active_connections: Dict[int, WebSocket] = {}

@router.websocket("/ws/chat")
async def chat_websocket(
    websocket: WebSocket, 
    current_user: Annotated[UserPublic, Depends(get_current_user_ws)], 
    session: SessionDep
    ):
    await websocket.accept()
    active_connections[current_user.id] = websocket

    # asyncio.create_task(ping_loop(websocket, current_user.id))

    try:
        while True:
            data = await websocket.receive_json()
            
            if data.get("type") == "ping":
                await websocket.send_json({"type": "pong"})
                continue

            receiver_id = data["receiver_id"]
            content = data["content"]

            message_in = MessageCreate(sender_id=current_user.id, receiver_id=receiver_id, content=content)
            crud.create_message(session, message_in)
            
            if receiver_id in active_connections:
                await active_connections[receiver_id].send_json({"sender_id": current_user.id, "content": content})
    except WebSocketDisconnect:
        del active_connections[current_user.id]


