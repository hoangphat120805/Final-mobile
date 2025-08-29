from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends

from app import crud
from app.schemas.message import MessageCreate, Message
from app.api.deps import SessionDep
from typing import Dict

router = APIRouter()

active_connections: Dict[int, WebSocket] = {}

@router.websocket("/ws/chat/{user_id}")
async def chat_websocket(websocket: WebSocket, user_id: int, session: SessionDep):
    await websocket.accept()
    active_connections[user_id] = websocket
    try:
        while True:
            data = await websocket.receive_json()
            receiver_id = data["receiver_id"]
            content = data["content"]

            message_in = MessageCreate(sender_id=user_id, receiver_id=receiver_id, content=content)
            crud.create_message(session, message_in)
            
            if receiver_id in active_connections:
                await active_connections[receiver_id].send_json({"sender_id": user_id, "content": content})
    except WebSocketDisconnect:
        del active_connections[user_id]
