from datetime import datetime
from typing import Optional, List
from uuid import UUID
from pydantic import BaseModel

class NotificationBase(BaseModel):
    title: str
    message: str

class NotificationCreate(NotificationBase):
    pass

class NotificationPublic(NotificationBase):
    id: UUID
    created_at: datetime
    updated_at: datetime

class UserNotification(NotificationBase):
    id: UUID
    is_read: bool
    created_at: datetime

class NotiUserBase(BaseModel):
    notification_id: UUID
    user_id: UUID
    is_read: bool = False
    created_at: datetime

class NotiUserPublic(NotiUserBase):
    pass

class NotificationWithUsers(NotificationPublic):
    recipients: List[NotiUserPublic] = []
