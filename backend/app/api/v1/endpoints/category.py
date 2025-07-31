from typing import Annotated, Any, List
from fastapi import APIRouter, HTTPException, status

from app.api.deps import SessionDep, CurrentUser
from app.schemas.category import CategoryPublic, CategoryCreate
from app import crud

router = APIRouter(prefix="/category", tags=["category"])

@router.get("/categories")
def get_categories(session: SessionDep) -> Any:
    return crud.get_all_categories(session=session)

@router.post("/categories", response_model=CategoryPublic)
def create_category(session: SessionDep, current_user: CurrentUser, category_create: CategoryCreate) -> Any:
    return crud.create_category(session=session, category_create=category_create, current_user=current_user)