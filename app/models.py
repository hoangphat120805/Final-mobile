import uuid
import datetime

from sqlmodel import SQLModel, Field, Relationship
from typing import Optional 

class Role(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    name: str = Field(max_length=50, index=True, unique=True)
    description: Optional[str] = Field(default=None, max_length=200)
    created_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)
    updated_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)

    users: list["User"] = Relationship(back_populates="role", sa_relationship_kwargs={"lazy": "selectin"})

class User(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    email: Optional[str] = Field(default=None, max_length=100, index=True, unique=True)
    phone: Optional[str] = Field(default=None, max_length=15, index=True, unique=True)
    username: str = Field(max_length=100)
    hashed_password: str = Field(max_length=100)
    role_id: uuid.UUID = Field(foreign_key="role.id")
    created_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)
    updated_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)

    role: Role = Relationship(back_populates="users", sa_relationship_kwargs={"lazy": "selectin"})
    agency_prices: list["AgencyItemPrice"] = Relationship(back_populates="agency", sa_relationship_kwargs={"lazy": "selectin"})

class Item(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    name: str = Field(max_length=100)
    description: Optional[str] = Field(default=None, max_length=500)
    base_price: float = Field(gt=0)
    created_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)
    updated_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)

    agency_prices: list["AgencyItemPrice"] = Relationship(back_populates="item", sa_relationship_kwargs={"lazy": "selectin"})

class AgencyItemPrice(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    item_id: uuid.UUID = Field(foreign_key="item.id")
    agency_id: uuid.UUID = Field(foreign_key="user.id") 
    price: float = Field(gt=0)
    created_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)
    updated_at: datetime.datetime = Field(default_factory=datetime.datetime.utcnow)

    item: Item = Relationship(sa_relationship_kwargs={"lazy": "selectin"})
    agency: User = Relationship(sa_relationship_kwargs={"lazy": "selectin"}) 

