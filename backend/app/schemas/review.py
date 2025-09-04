from datetime import datetime
import uuid
from sqlmodel import SQLModel, Field

class ReviewCreate(SQLModel):
    rating: int = Field(ge=1, le=5)
    comment: str | None = Field(default=None, max_length=500)

class ReviewPublic(SQLModel):
    id: uuid.UUID
    user_id: uuid.UUID
    order_id: uuid.UUID
    rating: int
    comment: str | None
    created_at: datetime
    updated_at: datetime
