import hashlib
import logging
import random
import string
import secrets
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
import os

import emails  # type: ignore
import jwt
from jinja2 import Template
from jwt.exceptions import InvalidTokenError
import redis

from app.core import security
from app.core.config import settings

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class EmailData:
    html_content: str
    subject: str


def render_email_template(*, template_name: str, context: dict[str, Any]) -> str:
    template_str = (
        Path(__file__).parent / "email-templates" / "build" / template_name
    ).read_text()
    html_content = Template(template_str).render(context)
    return html_content


def send_email(
    *,
    email_to: str,
    subject: str = "",
    html_content: str = "",
) -> None:
    message = emails.Message(
        subject=subject,
        html=html_content,
        mail_from=(settings.EMAILS_FROM_NAME, settings.EMAILS_FROM_EMAIL),
    )

    smtp_options = {"host": settings.SMTP_HOST, "port": settings.SMTP_PORT}
    if settings.SMTP_TLS:
        smtp_options["tls"] = True
    elif settings.SMTP_SSL:
        smtp_options["ssl"] = True
    if settings.SMTP_USER:
        smtp_options["user"] = settings.SMTP_USER
    if settings.SMTP_PASSWORD:
        smtp_options["password"] = settings.SMTP_PASSWORD
    response = message.send(to=email_to, smtp=smtp_options)
    logger.info(f"send email result: {response}")


def generate_test_email(email_to: str) -> EmailData:
    project_name = settings.PROJECT_NAME
    subject = f"{project_name} - Test email"
    html_content = render_email_template(
        template_name="test_email.html",
        context={"project_name": settings.PROJECT_NAME, "email": email_to},
    )
    return EmailData(html_content=html_content, subject=subject)


def generate_reset_password_email(email_to: str, email: str, token: str) -> EmailData:
    project_name = settings.PROJECT_NAME
    subject = f"{project_name} - Password recovery for user {email}"
    link = f"{settings.FRONTEND_HOST}/reset-password?token={token}"
    html_content = render_email_template(
        template_name="reset_password.html",
        context={
            "project_name": settings.PROJECT_NAME,
            "username": email,
            "email": email_to,
            "valid_hours": settings.EMAIL_RESET_TOKEN_EXPIRE_HOURS,
            "link": link,
        },
    )
    return EmailData(html_content=html_content, subject=subject)


def generate_new_account_email(
    email_to: str, username: str, password: str
) -> EmailData:
    project_name = settings.PROJECT_NAME
    subject = f"{project_name} - New account for user {username}"
    html_content = render_email_template(
        template_name="new_account.html",
        context={
            "project_name": settings.PROJECT_NAME,
            "username": username,
            "password": password,
            "email": email_to,
            "link": settings.FRONTEND_HOST,
        },
    )
    return EmailData(html_content=html_content, subject=subject)


def generate_password_reset_token(email: str) -> str:
    delta = timedelta(hours=settings.EMAIL_RESET_TOKEN_EXPIRE_HOURS)
    now = datetime.now(timezone.utc)
    expires = now + delta
    exp = expires.timestamp()
    encoded_jwt = jwt.encode(
        {"exp": exp, "nbf": now, "sub": email},
        settings.SECRET_KEY,
        algorithm=security.ALGORITHM,
    )
    return encoded_jwt


def verify_password_reset_token(token: str) -> str | None:
    try:
        decoded_token = jwt.decode(
            token, settings.SECRET_KEY, algorithms=[security.ALGORITHM]
        )
        return str(decoded_token["sub"])
    except InvalidTokenError:
        return None


def generate_otp(length=6):
    return ''.join(random.choices(string.digits, k=length))


def send_otp_email(email_to: str, otp: str, purpose: str = "register") -> None:
    project_name = settings.PROJECT_NAME
    if purpose == "register":
        subject = f"{project_name} - Xác thực đăng ký tài khoản"
        html_content = f"<p>Mã OTP xác thực đăng ký tài khoản của bạn là: <b>{otp}</b></p>"
    elif purpose == "reset":
        subject = f"{project_name} - Xác thực đặt lại mật khẩu"
        html_content = f"<p>Mã OTP xác thực đặt lại mật khẩu của bạn là: <b>{otp}</b></p>"
    else:
        subject = f"{project_name} - OTP xác thực"
        html_content = f"<p>Mã OTP của bạn là: <b>{otp}</b></p>"
    send_email(email_to=email_to, subject=subject, html_content=html_content)


# Kết nối Redis container
redis_client = redis.Redis(
    host=settings.REDIS_HOST,
    port=settings.REDIS_PORT,
    db=settings.REDIS_DB,
    decode_responses=True
)


def hash_otp(otp: str) -> str:
    return hashlib.sha256(otp.encode()).hexdigest()


def save_otp(email: str, otp: str, purpose: str, expire_minutes: int = 5):
    key = f"otp:{purpose}:{email}"
    hashed_otp = hash_otp(otp)
    redis_client.setex(key, expire_minutes * 60, hashed_otp)


def verify_otp(email: str, otp: str, purpose: str) -> bool:
    key = f"otp:{purpose}:{email}"
    stored_otp = redis_client.get(key)
    if stored_otp and stored_otp == hash_otp(otp):
        redis_client.delete(key)
        return True
    return False


def send_and_save_otp(email_to: str, purpose: str):
    otp = generate_otp()
    send_otp_email(email_to, otp, purpose)
    save_otp(email_to, otp, purpose)


def generate_reset_token(length=32):
    return secrets.token_urlsafe(length)


def save_token(email: str, token: str, expire_minutes: int, purpose: str):
    key = f"{purpose}:{email}"
    redis_client.setex(key, expire_minutes * 60, token)


def verify_token(email: str, token: str, purpose: str) -> bool:
    key = f"{purpose}:{email}"
    stored_token = redis_client.get(key)
    if stored_token and stored_token == token:
        redis_client.delete(key)
        return True
    return False