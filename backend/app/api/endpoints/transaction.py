from fastapi import APIRouter, Depends, Query, HTTPException, status
from sqlmodel import Session, select
from app.api.deps import get_db
from app.models import Transaction, Order
from app.schemas.transaction import TransactionReadResponse
from app.api.deps import get_current_user
from typing import List, Optional
import uuid

router = APIRouter()

@router.get("/user/{user_id}", response_model=List[TransactionReadResponse])
def get_transactions_by_user(
    user_id: uuid.UUID,
    session: Session = Depends(get_db),
    current_user=Depends(get_current_user),
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0)
):
    # Only allow access to your own transactions
    if current_user.id != user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to view these transactions.")
    query = select(Transaction).where(
        (Transaction.payer_id == user_id) | (Transaction.payee_id == user_id)
    ).offset(offset).limit(limit)
    results = session.exec(query).all()
    return results

@router.get("/order/{order_id}", response_model=List[TransactionReadResponse])
def get_transactions_by_order(
    order_id: uuid.UUID,
    session: Session = Depends(get_db),
    current_user=Depends(get_current_user)
):
    # Only allow access if the user owns the order or is payer/payee in the transaction
    order = session.get(Order, order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if current_user.id != order.owner_id:
        # Check if user is payer/payee in any transaction for this order
        tx_query = select(Transaction).where(
            (Transaction.order_id == order_id) & (
                (Transaction.payer_id == current_user.id) | (Transaction.payee_id == current_user.id)
            )
        )
        txs = session.exec(tx_query).all()
        if not txs:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to view transactions for this order.")
        return txs
    # If owner, return all transactions for the order
    query = select(Transaction).where(Transaction.order_id == order_id)
    results = session.exec(query).all()
    return results

@router.get("/", response_model=List[TransactionReadResponse])
def get_transactions(
    status: Optional[str] = None,
    session: Session = Depends(get_db),
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0)
):
    query = select(Transaction)
    if status:
        query = query.where(Transaction.status == status)
    query = query.offset(offset).limit(limit)
    results = session.exec(query).all()
    return results
