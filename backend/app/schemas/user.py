from datetime import datetime
from sqlmodel import SQLModel, Field
from pydantic import validator  
from typing import Optional
import uuid

class UserCreate(SQLModel):
    phone_number: str = Field(max_length=100)
    password: str = Field(max_length=100)

    @validator("phone_number")
    def validate_phone_number(cls, v):
        if not v.startswith("0") or len(v) != 10:
            raise ValueError("Invalid phone number")
        return v
    
    @validator("password")
    def validate_password(cls, v):
        if len(v) < 8:
            raise ValueError("Password must be at least 8 characters long and contain at least one digit")
        return v

class UserLogin(SQLModel):
    phone_number: str = Field(max_length=100)
    password: str = Field(max_length=100)

class UserUpdate(SQLModel):
    phone_number: Optional[str] = Field(default=None, max_length=15)
    email: Optional[str] = Field(default=None, max_length=100)

    @validator("phone_number")
    def validate_phone_number(cls, v):
        if v and (not v.startswith("0") or len(v) != 10):
            raise ValueError("Invalid phone number")
        return v

class UserUpdatePassword(SQLModel):
    old_password: str = Field(max_length=100)
    new_password: str = Field(max_length=100)

    @validator("new_password")
    def validate_new_password(cls, v):
        if len(v) < 8:
            raise ValueError("New password must be at least 8 characters long and contain at least one digit")
        return v

class UserPublic(SQLModel):
    id: uuid.UUID
    full_name: str | None = Field(max_length=100, nullable=True)
    phone_number: str = Field(max_length=15)
    role: str

class AddressCreate(SQLModel):
    street: str = Field(max_length=255)
    city: str = Field(max_length=100)
    district: str = Field(max_length=100)
    ward: str = Field(max_length=100)
    street_address: str = Field(max_length=255)
    longitude: float = Field(ge=-180, le=180)
    latitude: float = Field(ge=-90, le=90)

class AddressPublic(SQLModel):
    id: uuid.UUID
    user_id: uuid.UUID
    street: str
    city: str
    district: str
    ward: str
    street_address: str
    longitude: float
    latitude: float
    created_at: datetime
    updated_at: datetime

class AddressUpdate(SQLModel):
    street: Optional[str] = Field(default=None, max_length=255)
    city: Optional[str] = Field(default=None, max_length=100)
    district: Optional[str] = Field(default=None, max_length=100)
    ward: Optional[str] = Field(default=None, max_length=100)
    street_address: Optional[str] = Field(default=None, max_length=255)
    longitude: Optional[float] = Field(default=None, ge=-180, le=180)
    latitude: Optional[float] = Field(default=None, ge=-90, le=90)
