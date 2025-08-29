import time
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends

from app import crud
from app.schemas.message import MessageCreate
from app.schemas.user import UserPublic
from app.api.deps import SessionDep, CurrentUser, get_current_user_ws
from typing import Dict, Annotated
from sqlmodel import Session
import asyncio

from uuid import UUID
from fastapi import Depends
from app.schemas.message import Message

router = APIRouter(prefix="/chat", tags=["chat"])

active_connections: Dict[int, WebSocket] = {}

PING_INTERVAL = 30   
PING_TIMEOUT = 10   

async def ping_loop(websocket: WebSocket, user_id: str):
    try:
        while True:
            await websocket.send_json({"type": "ping", "ts": time.time()})
            
            try:
                msg = await asyncio.wait_for(websocket.receive_json(), timeout=PING_TIMEOUT)
                if msg.get("type") != "pong":
                    raise Exception("No pong received")
            except asyncio.TimeoutError:
                await websocket.close()
                break

            await asyncio.sleep(PING_INTERVAL)

    except WebSocketDisconnect:
        active_connections.pop(user_id, None)

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

@router.get("/history/{receiver_id}", response_model=list[Message])
def get_chat_history(
    receiver_id: UUID,
    current_user: CurrentUser,
    session: SessionDep
):
    return crud.get_chat_history(session, current_user.id, receiver_id)

# Endpoint lấy danh sách các user đã từng chat với user hiện tại
@router.get("/list")
def get_user_chats(current_user: CurrentUser, session: SessionDep):
    return crud.get_user_chats(session, current_user.id)

