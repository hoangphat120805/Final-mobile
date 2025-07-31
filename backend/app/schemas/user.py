from sqlmodel import SQLModel, Field
from typing import Optional
import uuid

class UserCreate(SQLModel):
    phone_number: str = Field(max_length=100)
    password: str = Field(max_length=100)

class UserLogin(SQLModel):
    phone_number: str = Field(max_length=100)
    password: str = Field(max_length=100)

class UserUpdate(SQLModel):
    phone_number: Optional[str] = Field(default=None, max_length=15)
    email: Optional[str] = Field(default=None, max_length=100)

class UserUpdatePassword(SQLModel):
    old_password: str = Field(max_length=100)
    new_password: str = Field(max_length=100)

class UserPublic(SQLModel):
    id: uuid.UUID
    username: str
    email: Optional[str] = None
    phone: Optional[str] = None

