from datetime import timedelta
from sqlmodel import SQLModel

class TokenPayLoad(SQLModel):
    sub: str
    exp: int

class Token(SQLModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: timedelta  

class Message(SQLModel):
    message: str