
from pydantic import BaseModel
from datetime import datetime
import uuid


class MessageBase(BaseModel):
    sender_id: uuid.UUID
    content: str


class MessageCreate(MessageBase):
    pass


class Message(MessageBase):
    id: uuid.UUID
    created_at: datetime
