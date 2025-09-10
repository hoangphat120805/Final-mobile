from typing import Annotated, Any, List
from fastapi import APIRouter, HTTPException, status, UploadFile, File, Form
import uuid

from app.schemas.auth import Message

from app.api.deps import SessionDep, CurrentUser, CurrentAdmin
from app.schemas.category import CategoryPublic, CategoryCreate, CategoryUpdate
from app import crud
from app.services.upload import upload_category_icon

router = APIRouter(prefix="/category", tags=["category"])

@router.get("/", response_model=List[CategoryPublic])
def get_categories(session: SessionDep) -> Any:
    return crud.get_all_categories(session=session)

@router.post("/", response_model=CategoryPublic, status_code=status.HTTP_201_CREATED)
def create_category(
    session: SessionDep, 
    current_user: CurrentUser, 
    category_create: CategoryCreate, 
    ):
    existing_category = crud.get_category_by_slug(session=session, slug=category_create.slug)
    if existing_category:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Category with this slug already exists",
        )
    return crud.create_category(
        session=session, 
        category_create=category_create, 
        current_user=current_user
        )

@router.delete("/{category_slug}", response_model=Message)
def delete_category(session: SessionDep, current_user: CurrentUser, category_slug: str) -> Any:
    category = crud.get_category_by_slug(session=session, slug=category_slug)
    if not category:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Category not found",
        )
    crud.delete_category(session=session, category=category)
    return {"message": "Category deleted successfully"}

@router.get("/{category_slug}", response_model=CategoryPublic)
def get_category_by_slug(session: SessionDep, category_slug: str) -> Any:
    category = crud.get_category_by_slug(session=session, slug=category_slug)
    if not category:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Category not found",
        )
    return category

@router.patch("/{category_slug}", response_model=CategoryPublic)
def update_category(
    session: SessionDep, 
    current_admin: CurrentAdmin, 
    category_slug: str, 
    category_update: CategoryUpdate
    ) -> Any:
    category = crud.get_category_by_slug(session=session, slug=category_slug)
    if not category:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Category not found",
        )
    return crud.update_category(
        session=session, 
        category=category, 
        category_update=category_update, 
        current_user=current_admin, 
    )

@router.post("/upload-icon/{category_slug}", response_model=CategoryPublic)
def upload_icon(
    session: SessionDep,
    current_admin: CurrentAdmin,
    category_slug: str,
    file: Annotated[UploadFile, File(...)]
):
    category = crud.get_category_by_slug(session=session, slug=category_slug)
    if not category:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Category not found",
        )
    try:
        icon_url = upload_category_icon(file=file, public_id=category_slug)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to upload icon: {str(e)}"
        )
    return crud.update_category_icon(
        session=session, 
        category=category, 
        current_user=current_admin, 
        icon_url=icon_url
    )
