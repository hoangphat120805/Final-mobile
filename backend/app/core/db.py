from sqlmodel import create_engine
from app.core.config import settings
from sqlmodel import Session
from app import crud

engine = create_engine(
    str(settings.POSTGRES_URL), 
    echo=True, 
    echo_pool=True,
    pool_pre_ping=True,
    pool_size=20,
    max_overflow=10,
    pool_timeout=30,
    pool_recycle=1800,
    )

def init_db(session: Session) -> None:
    pass
    