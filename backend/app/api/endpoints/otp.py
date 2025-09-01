from app import crud
from fastapi import APIRouter, HTTPException, status, Body
from pydantic import EmailStr, BaseModel
from app.utils import send_and_save_otp, verify_otp, generate_reset_token, save_reset_token, verify_reset_token
from app.models import User
from app.api.deps import SessionDep

router = APIRouter(prefix="/otp", tags=["otp"])

class OTPRequest(BaseModel):
    email: EmailStr

class OTPVerifyRequest(BaseModel):
    email: EmailStr
    otp: str

@router.post("/send-register-otp")
def send_register_otp(otp_request: OTPRequest):
    send_and_save_otp(otp_request.email, purpose="register")
    return {"message": "Register OTP sent to email"}

@router.post("/send-reset-password-otp")
def send_reset_password_otp(session: SessionDep, otp_request: OTPRequest):
    user = crud.get_user_by_email(session, otp_request.email)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    send_and_save_otp(otp_request.email, purpose="reset")
    return {"message": "Reset password OTP sent to email"}

@router.post("/verify-register-otp")
def verify_register_otp(otp_request: OTPVerifyRequest):
    if verify_otp(otp_request.email, otp_request.otp, purpose="register"):
        return {"message": "Register OTP verified"}
    raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired OTP")

@router.post("/verify-reset-otp")
def verify_reset_otp(otp_request: OTPVerifyRequest):
    if verify_otp(otp_request.email, otp_request.otp, purpose="reset"):
        token = generate_reset_token()
        save_reset_token(otp_request.email, token)
        return {"reset_token": token}
    raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired OTP")


