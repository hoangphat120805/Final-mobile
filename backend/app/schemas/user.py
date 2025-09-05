
from datetime import datetime, date
from app.models import UserRole
from sqlmodel import SQLModel, Field 
from pydantic import EmailStr
import uuid

class UserBase(SQLModel):
    email: EmailStr
    phone_number: str
    full_name: str
    gender: str | None = Field(default=None)
    birth_date: str | None = Field(default=None)
    avt_url: str | None = Field(default=None)

class UserCreate(UserBase):
    role: UserRole
    password: str

class UserRegister(SQLModel):
    email: str
    phone_number: str
    password: str
    full_name: str
    register_token: str

class UserLogin(SQLModel):
    phone_number: str
    password: str

class UserUpdate(UserBase):
    email: EmailStr | None = Field(default=None)
    phone_number: str | None = Field(default=None)
    full_name: str | None = Field(default=None)
    role: UserRole | None = Field(default=None)

class UserUpdateMe(UserBase):
    email: EmailStr | None = Field(default=None)
    phone_number: str | None = Field(default=None)
    full_name: str | None = Field(default=None)

class UpdatePassword(SQLModel):
    old_password: str = Field(max_length=100)
    new_password: str = Field(max_length=100)

class UserPublic(UserBase):
    id: uuid.UUID
    role: UserRole

class UsersPublic(SQLModel):
    data: list[UserPublic]
    count: int

class CollectorPublic(UserPublic):
    average_rating: float | None = None

