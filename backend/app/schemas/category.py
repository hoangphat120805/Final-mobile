from datetime import datetime
import uuid
from sqlmodel import SQLModel, Field

class CategoryPublic(SQLModel):
    id: uuid.UUID
    name: str
    slug: str
    description: str | None = None
    unit: str = "kg"
    icon_url: str | None = None
    estimated_price_per_unit: float | None = None
    created_by: uuid.UUID
    last_updated_by: uuid.UUID
    created_at: datetime
    updated_at: datetime

class CategoryCreate(SQLModel):
    name: str = Field(min_length=2, max_length=100)
    slug: str = Field(min_length=2, max_length=100)
    description: str  = Field(max_length=500)
    unit: str = Field(default="kg", max_length=10)
    estimated_price_per_unit: float = Field(gt=0)
    
class CategoryUpdate(SQLModel):
    name: str | None = Field(default=None, min_length=2, max_length=100)
    slug: str | None = Field(default=None, min_length=2, max_length=100)
    description: str | None = Field(default=None, max_length=500)
    unit: str | None = Field(default=None, max_length=10)
    estimated_price_per_unit: float | None = Field(default=None, gt=0)

