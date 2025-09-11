from http.client import HTTPException
import time
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends

from app import crud
from app.schemas.chat import ConversationCreate, ConversationWithLastMessage, MessageCreate, ConversationPublic, MessagePublic
from app.schemas.user import UserPublic
from app.api.deps import SessionDep, CurrentUser, CurrentUserWs
from typing import Dict, Annotated

router = APIRouter(prefix="/chat", tags=["chat"])

active_connections: Dict[int, WebSocket] = {}

@router.websocket("/ws/chat")
async def chat_websocket(
    websocket: WebSocket, 
    current_user: CurrentUserWs,
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

@router.get("/conversations/", response_model=list[ConversationWithLastMessage])
async def get_conversations(
    session: SessionDep,
    current_user: CurrentUser,
):
    conversations = crud.get_user_conversations_and_last_message(session=session, user_id=current_user.id)
    return conversations

@router.get("/conversations/{conversation_id}/messages/", response_model=list[MessagePublic])
async def get_messages(
    conversation_id: str,
    session: SessionDep,
    current_user: CurrentUser,
):
    members = crud.get_conversation_members(session, conversation_id)
    if current_user.id not in [member.user_id for member in members]:
        raise HTTPException(status_code=403, detail="Not a member of this conversation")
    messages = crud.get_messages_by_conversation(session=session, conversation_id=conversation_id)
    return messages
