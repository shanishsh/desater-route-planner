"""Authentication schemas."""
from typing import Optional
from pydantic import BaseModel, EmailStr, ConfigDict


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    role: str
    expires_in_minutes: int


class TokenPayload(BaseModel):
    sub: Optional[str] = None
    role: Optional[str] = None


class UserResponse(BaseModel):
    id: str
    email: EmailStr
    role: str
    is_active: bool

    model_config = ConfigDict(from_attributes=True)

