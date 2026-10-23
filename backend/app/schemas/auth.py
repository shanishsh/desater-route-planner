"""Authentication schemas."""
from typing import Optional
from pydantic import BaseModel, EmailStr, ConfigDict, Field


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class UserCreate(BaseModel):
    email: EmailStr
    password: str = Field(..., min_length=6, description="Password must be at least 6 characters")
    role: Optional[str] = "tourist"


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    role: str
    expires_in_minutes: int
    user_id: Optional[str] = None
    email: Optional[str] = None


class TokenPayload(BaseModel):
    sub: Optional[str] = None
    role: Optional[str] = None


class UserResponse(BaseModel):
    id: str
    email: EmailStr
    role: str
    is_active: bool

    model_config = ConfigDict(from_attributes=True)

