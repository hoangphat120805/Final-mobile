
from pydantic import BaseModel
from datetime import datetime
import uuid


class MessageBase(BaseModel):
    sender_id: uuid.UUID
    receiver_id: uuid.UUID
    content: str


class MessageCreate(MessageBase):
    pass


class Message(MessageBase):
    id: uuid.UUID
    timestamp: datetime
