from app import crud
from fastapi import APIRouter, HTTPException, status, Body
from pydantic import EmailStr, BaseModel
from app.services.email import send_and_save_otp, verify_otp, generate_token
from app.models import User
from app.api.deps import SessionDep
from enum import Enum

router = APIRouter(prefix="/otp", tags=["otp"])

class OTPPurpose(str, Enum):
    REGISTER = "register"
    RESET = "reset"

class OTPRequest(BaseModel):
    email: EmailStr
    purpose: OTPPurpose

class OTPVerifyRequest(BaseModel):
    email: EmailStr
    otp: str
    purpose: OTPPurpose

class OTPResponse(BaseModel):
    token: str

@router.post("/send-otp")
def send_otp(session: SessionDep, otp_request: OTPRequest):
    user = crud.get_user_by_email(session, otp_request.email)
    if otp_request.purpose == OTPPurpose.RESET:
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    if otp_request.purpose == OTPPurpose.REGISTER:
        if user:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="User already exists")
    send_and_save_otp(otp_request.email, purpose=otp_request.purpose)
    return {"message": f"OTP sent to email for {otp_request.purpose}"}

@router.post("/verify-otp", response_model=OTPResponse)
def verify_otp_endpoint(otp_request: OTPVerifyRequest):
    if verify_otp(otp_request.email, otp_request.otp, purpose=otp_request.purpose):
        if otp_request.purpose == OTPPurpose.RESET:
            token = generate_token(email=otp_request.email, purpose="reset")
            return OTPResponse(token=token)
        else:
            token = generate_token(email=otp_request.email, purpose="register")
            return OTPResponse(token=token)
    raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired OTP")


