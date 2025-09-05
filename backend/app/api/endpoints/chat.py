import time
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends

from app import crud
from app.schemas.chat import ConversationCreate, MessageCreate, ConversationPublic, MessagePublic
from app.schemas.user import UserPublic
from app.api.deps import SessionDep, CurrentUser, get_current_user_ws
from typing import Dict, Annotated

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

    try:
        while True:
            msg = await websocket.receive_json()
            type = msg.get("type")
            match type:
                case "message":
                    message_in = MessageCreate(**msg.get("data"))
                    message = crud.create_message(session=session, message_create=message_in, sender_id=current_user.id)

                    members = crud.get_conversation_members(session, message_in.conversation_id)
                    for member in members:
                        if member.user_id in active_connections and member.user_id != current_user.id:
                            await active_connections[member.user_id].send_json({
                                "type": "message",
                                "data": {
                                    "id": str(message.id),
                                    "conversation_id": str(message.conversation_id),
                                    "sender_id": str(message.sender_id),
                                    "content": message.content,
                                    "is_read": message.is_read,
                                    "created_at": message.created_at.isoformat()
                                }
                            })
                case _:
                    await websocket.send_json({"error": "Unknown message type"})

    except WebSocketDisconnect:
        del active_connections[current_user.id]

@router.post("/conversations/", response_model=ConversationPublic)
async def create_conversation(
    session: SessionDep,
    conversation_create: ConversationCreate,
    current_user: CurrentUser,
):
    conversation = crud.create_conversation(session = session, conversation_create = conversation_create, user_id = current_user.id)
    return conversation

@router.get("/conversations/", response_model=list[ConversationPublic])
async def get_conversations(
    session: SessionDep,
    current_user: CurrentUser,
):
    conversations = crud.get_user_conversations(session=session, user_id=current_user.id)
    return conversations
