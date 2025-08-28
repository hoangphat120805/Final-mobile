from datetime import datetime
from typing import Optional, List
from uuid import UUID
from pydantic import BaseModel

class NotificationBase(BaseModel):
    message: str

class NotificationCreate(NotificationBase):
    pass

class NotificationPublic(NotificationBase):
    id: UUID
    created_at: datetime
    updated_at: datetime

    class Config:
        orm_mode = True

class NotiUserBase(BaseModel):
    notification_id: UUID
    user_id: UUID
    is_read: bool = False
    created_at: datetime

class NotiUserPublic(NotiUserBase):
    class Config:
        orm_mode = True

class NotificationWithUsers(NotificationPublic):
    recipients: List[NotiUserPublic] = []
