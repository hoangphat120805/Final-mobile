from datetime import timedelta
from sqlmodel import SQLModel, Field
from pydantic import validator

class TokenPayLoad(SQLModel):
    sub: str = Field(min_length=1)
    exp: int = Field(gt=0)


class Token(SQLModel):
    access_token: str = Field(min_length=10)
    token_type: str = Field(default="bearer", regex="^bearer$")


class Message(SQLModel):
    message: str = Field(min_length=1, max_length=1000)
