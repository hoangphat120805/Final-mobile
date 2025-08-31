from fastapi import APIRouter, HTTPException, status, Body, Depends
from pydantic import EmailStr
from app.utils import send_and_save_otp, verify_otp, generate_reset_token, save_reset_token, verify_reset_token
from app.models import User
from app.api.deps import SessionDep
from app.core.security import get_password_hash
from sqlmodel import select

router = APIRouter(prefix="/otp", tags=["otp"])

@router.post("/send-register-otp")
def send_register_otp(email: EmailStr = Body(...)):
    send_and_save_otp(email, purpose="register")
    return {"message": "Register OTP sent to email"}

@router.post("/send-reset-password-otp")
def send_reset_password_otp(session: SessionDep, email: EmailStr = Body(...)):
    user = session.exec(select(User).where(User.email == email)).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    send_and_save_otp(email, purpose="reset")
    return {"message": "Reset password OTP sent to email"}

@router.post("/verify-register-otp")
def verify_register_otp(email: EmailStr = Body(...), otp: str = Body(...)):
    if verify_otp(email, otp, purpose="register"):
        return {"message": "Register OTP verified"}
    raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired OTP")

@router.post("/verify-reset-otp")
def verify_reset_otp(email: EmailStr = Body(...), otp: str = Body(...)):
    if verify_otp(email, otp, purpose="reset"):
        token = generate_reset_token()
        save_reset_token(email, token)
        return {"reset_token": token}
    raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired OTP")


