from datetime import datetime
import uuid
from typing import Optional
from sqlmodel import SQLModel, Field
from pydantic import validator
import re

class CategoryPublic(SQLModel):
    id: uuid.UUID
    name: str
    slug: str
    description: Optional[str] = None
    unit: str = "kg"
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None
    created_by: uuid.UUID
    last_updated_by: uuid.UUID
    created_at: datetime
    updated_at: datetime

class CategoryCreate(SQLModel):
    name: str = Field(min_length=2, max_length=100)
    slug: str = Field(min_length=2, max_length=100)
    description: Optional[str] = Field(default=None, max_length=500)
    unit: str = Field(default="kg", max_length=10)
    icon_url: Optional[str] = Field(default=None, max_length=500)
    estimated_price_per_unit: float = Field(gt=0)
    

class CategoryUpdate(SQLModel):
    name: Optional[str] = Field(default=None, min_length=2, max_length=100)
    slug: Optional[str] = Field(default=None, min_length=2, max_length=100)
    description: Optional[str] = Field(default=None, max_length=500)
    unit: Optional[str] = Field(default=None, max_length=10)
    icon_url: Optional[str] = Field(default=None, max_length=500)
    estimated_price_per_unit: Optional[float] = Field(default=None, gt=0)

