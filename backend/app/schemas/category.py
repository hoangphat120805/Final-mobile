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
    
    @validator("slug")
    def validate_slug(cls, v):
        v = v.strip().lower()
        if not re.match(r'^[a-z0-9\-_]+$', v):
            raise ValueError("Slug can only contain lowercase letters, numbers, hyphens, and underscores")
        return v

class CategoryUpdate(SQLModel):
    name: Optional[str] = Field(default=None, min_length=2, max_length=100)
    slug: Optional[str] = Field(default=None, min_length=2, max_length=100)
    description: Optional[str] = Field(default=None, max_length=500)
    unit: Optional[str] = Field(default=None, max_length=10)
    icon_url: Optional[str] = Field(default=None, max_length=500)
    estimated_price_per_unit: Optional[float] = Field(default=None, gt=0)
    
    @validator("slug")
    def validate_slug(cls, v):
        if v is not None:
            v = v.strip().lower()
            if not re.match(r'^[a-z0-9\-_]+$', v):
                raise ValueError("Slug can only contain lowercase letters, numbers, hyphens, and underscores")
        return v
    
    @validator("unit")
    def validate_unit(cls, v):
        if v is not None:
            v = v.strip().lower()
            allowed_units = ["kg", "g", "ton", "piece", "liter", "ml"]
            if v not in allowed_units:
                raise ValueError(f"Unit must be one of: {', '.join(allowed_units)}")
        return v
    
    @validator("icon_url")
    def validate_icon_url(cls, v):
        if v is not None:
            v = v.strip()
            url_regex = r'^https?:\/\/[^\s/$.?#].[^\s]*\.(jpg|jpeg|png|gif|svg|webp)$'
            if not re.match(url_regex, v, re.IGNORECASE):
                raise ValueError("Invalid image URL format")
        return v
    
    @validator("description")
    def validate_description(cls, v):
        if v is not None:
            v = v.strip()
            if len(v) < 10:
                raise ValueError("Description must be at least 10 characters long")
        return v
