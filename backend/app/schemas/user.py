from datetime import datetime, date
from app.models import UserRole
from sqlmodel import SQLModel, Field 
from pydantic import EmailStr
import uuid

class UserBase(SQLModel):
    email: EmailStr = Field(max_length=100)
    phone_number: str = Field(max_length=15)
    role: UserRole = Field(default=UserRole.USER)
    full_name: str = Field(max_length=100)
    gender: str | None = Field(default=None, max_length=10)
    birth_date: str | None = Field(default=None)
    avt_url: str = Field(default="https://i.ibb.co/5xt2NvW0/453178253-471506465671661-2781666950760530985-n.png")

class UserCreate(UserBase):
    password: str

class UserRegister(SQLModel):
    email: str = Field(max_length=100)
    phone_number: str = Field(max_length=15)
    password: str
    full_name: str = Field(max_length=100)
    # avt_url: str = Field(default="https://i.ibb.co/5xt2NvW0/453178253-471506465671661-2781666950760530985-n.png")

class UserLogin(SQLModel):
    phone_number: str = Field(max_length=100)
    password: str = Field(max_length=100)

class UserUpdate(UserBase):
    email: EmailStr | None = Field(default=None, max_length=100)
    phone_number: str | None = Field(default=None, max_length=15)
    full_name: str | None = Field(default=None, max_length=100)
    role: UserRole | None = Field(default=None)
    gender: str | None = Field(default=None, max_length=10)
    birth_date: str | None = Field(default=None)
    avt_url: str | None = Field(default=None)


class UserUpdateMe(SQLModel):
    email: EmailStr | None = Field(default=None, max_length=100)
    phone_number: str | None = Field(default=None, max_length=15)
    full_name: str | None = Field(default=None, max_length=100)
    gender: str | None = Field(default=None, max_length=10)
    birth_date: str | None = Field(default=None)
    avt_url: str | None = Field(default=None)

class UpdatePassword(SQLModel):
    old_password: str = Field(max_length=100)
    new_password: str = Field(max_length=100)

class UserPublic(UserBase):
    id: uuid.UUID

class UsersPublic(SQLModel):
    data: list[UserPublic]
    count: int

