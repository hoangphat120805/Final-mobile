from datetime import datetime
import uuid
from app.models import TransactionMethod, TransactionStatus
from sqlmodel import SQLModel, Field
from typing import List, Optional


# Transaction Schemas
from typing import Optional

class UserReadMinimal(SQLModel):
    """
    A minimal representation of a User, designed to be safely embedded
    in other schemas without exposing sensitive data like password hashes.
    """
    id: uuid.UUID
    full_name: Optional[str] = None
    phone_number: str

# --- INPUT SCHEMAS (for Request Bodys) ---

class CompletedOrderItemPayload(SQLModel):
    """
    Defines the structure for a single item within the completion request.
    It specifies which OrderItem to update and its actual measured quantity.
    """
    # The unique ID of the OrderItem being updated.
    order_item_id: uuid.UUID 
    
    # The actual quantity measured by the collector. Must be greater than zero.
    actual_quantity: float = Field(gt=0, description="The actual quantity of the item after weighing.")


class OrderCompletionRequest(SQLModel):
    """
    This is the main schema for the JSON payload sent from the collector's app
    when they finalize an order. It validates the entire request body.
    """
    payment_method: TransactionMethod = Field(description="The payment method used (e.g., 'cash').")
    items: List[CompletedOrderItemPayload] = Field(description="A list of items with their actual quantities.")


# --- OUTPUT SCHEMAS (for API Responses) ---

class TransactionReadResponse(SQLModel):
    """
    Defines the structure of the JSON response sent back to the client
    after a transaction is successfully created. It provides rich, contextual data.
    """
    id: uuid.UUID
    order_id: uuid.UUID
    amount: float
    method: TransactionMethod
    status: TransactionStatus
    transaction_date: datetime

 
    payer: UserReadMinimal
    payee: UserReadMinimal


