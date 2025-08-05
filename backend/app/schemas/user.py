from datetime import datetime
from sqlmodel import SQLModel, Field
from pydantic import validator  
from typing import Optional
import uuid
import re

def validate_password_strength(password: str, field_name: str = "Password") -> str:
    """
    Validate password strength with comprehensive rules:
    - At least 8 characters long
    - At least one digit
    - At least one uppercase letter
    - At least one lowercase letter
    - At least one special character
    """
    if len(password) < 8:
        raise ValueError(f"{field_name} must be at least 8 characters long")
    
    # Check for at least one digit
    if not re.search(r'\d', password):
        raise ValueError(f"{field_name} must contain at least one digit")
    
    # Check for at least one uppercase letter
    if not re.search(r'[A-Z]', password):
        raise ValueError(f"{field_name} must contain at least one uppercase letter")
    
    # Check for at least one lowercase letter
    if not re.search(r'[a-z]', password):
        raise ValueError(f"{field_name} must contain at least one lowercase letter")
    
    # Check for at least one special character
    special_chars = "!@#$%^&*(),.?\":{}|<>"
    if not re.search(r'[!@#$%^&*(),.?":{}|<>]', password):
        raise ValueError(f"{field_name} must contain at least one special character ({special_chars})")
    
    return password

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
        return validate_password_strength(v, "Password")

class UserLogin(SQLModel):
    phone_number: str = Field(max_length=100)
    password: str = Field(max_length=100)

class UserUpdate(SQLModel):
    full_name: str | None = Field(default=None, max_length=100)
    phone_number: str | None = Field(default=None, max_length=15)
    email: str | None = Field(default=None, max_length=100)

    @validator("phone_number")
    def validate_phone_number(cls, v):
        if v and (not v.startswith("0") or len(v) != 10):
            raise ValueError("Invalid phone number")
        return v
    
    @validator("email")
    def validate_email(cls, v):
        if v is not None:
            v = v.strip().lower()
            email_regex = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
            if not re.match(email_regex, v):
                raise ValueError("Invalid email format")
        return v

class UserUpdatePassword(SQLModel):
    old_password: str = Field(max_length=100)
    new_password: str = Field(max_length=100)

    @validator("new_password")
    def validate_new_password(cls, v):
        return validate_password_strength(v, "New password")

class UserPublic(SQLModel):
    id: uuid.UUID
    full_name: str | None = Field(max_length=100, nullable=True)
    phone_number: str = Field(max_length=15)
    role: str

class AddressCreate(SQLModel):
    street: str = Field(min_length=5, max_length=255)
    city: str = Field(min_length=2, max_length=100)
    district: str = Field(min_length=2, max_length=100)
    ward: str = Field(min_length=2, max_length=100)
    street_address: str = Field(min_length=5, max_length=255)
    longitude: float = Field(ge=-180, le=180)
    latitude: float = Field(ge=-90, le=90)
    is_default: bool = Field(default=False)

    @validator("street", "city", "district", "ward", "street_address")
    def validate_address_fields(cls, v):
        v = v.strip()
        if not v:
            raise ValueError("Address field cannot be empty")
        if not re.search(r'[a-zA-Z0-9]', v):
            raise ValueError("Address field must contain valid characters")
        return v
    
    @validator("longitude")
    def validate_longitude(cls, v):
        # Vietnam longitude range approximately 102.1 to 109.5
        if not (102.0 <= v <= 110.0):
            raise ValueError("Longitude must be within Vietnam's valid range (102.0 to 110.0)")
        return v
    
    @validator("latitude")
    def validate_latitude(cls, v):
        # Vietnam latitude range approximately 8.2 to 23.4
        if not (8.0 <= v <= 24.0):
            raise ValueError("Latitude must be within Vietnam's valid range (8.0 to 24.0)")
        return v

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
    street: Optional[str] = Field(default=None, min_length=5, max_length=255)
    city: Optional[str] = Field(default=None, min_length=2, max_length=100)
    district: Optional[str] = Field(default=None, min_length=2, max_length=100)
    ward: Optional[str] = Field(default=None, min_length=2, max_length=100)
    street_address: Optional[str] = Field(default=None, min_length=5, max_length=255)
    longitude: Optional[float] = Field(default=None, ge=-180, le=180)
    latitude: Optional[float] = Field(default=None, ge=-90, le=90)
    is_default: Optional[bool] = Field(default=None)

    @validator("street", "city", "district", "ward", "street_address")
    def validate_address_fields(cls, v):
        if v is not None:
            v = v.strip()
            if not v:
                raise ValueError("Address field cannot be empty")
            if not re.search(r'[a-zA-Z0-9]', v):
                raise ValueError("Address field must contain valid characters")
        return v
    
    @validator("longitude")
    def validate_longitude(cls, v):
        if v is not None:
            # Vietnam longitude range approximately 102.1 to 109.5
            if not (102.0 <= v <= 110.0):
                raise ValueError("Longitude must be within Vietnam's valid range (102.0 to 110.0)")
        return v
    
    @validator("latitude")
    def validate_latitude(cls, v):
        if v is not None:
            # Vietnam latitude range approximately 8.2 to 23.4
            if not (8.0 <= v <= 24.0):
                raise ValueError("Latitude must be within Vietnam's valid range (8.0 to 24.0)")
        return v
