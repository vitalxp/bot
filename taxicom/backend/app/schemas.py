from datetime import datetime
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, field_validator

Platform = Literal["BOLT", "UBER", "FREENOW"]
Role = Literal["CAPTURE", "COCKPIT", "BOTH", "VIEWER"]
Verdict = Literal["TAKE", "MAYBE", "SKIP"]
Outcome = Literal["PENDING", "ACCEPTED", "DECLINED", "IGNORED", "EXPIRED", "CANCELLED", "COMPLETED"]


class DeviceIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    model: str | None = None
    form_factor: Literal["PHONE", "TABLET"] = "PHONE"
    role: Role = "BOTH"
    platforms: list[Platform] = []
    app_version: str | None = None


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=10, max_length=128)
    display_name: str | None = Field(default=None, max_length=120)
    timezone: str = "Europe/Warsaw"
    locale: str = "pl"
    device: DeviceIn

    @field_validator("password")
    @classmethod
    def strong_enough(cls, v: str) -> str:
        if not any(c.isalpha() for c in v) or all(c.isalpha() for c in v):
            raise ValueError("password must mix letters with digits or symbols")
        return v


class LoginIn(BaseModel):
    email: EmailStr
    password: str
    device: DeviceIn


class RefreshIn(BaseModel):
    refresh_token: str


class TokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "Bearer"
    expires_in: int
    user_id: str
    device_id: str


class UserOut(BaseModel):
    id: str
    email: str
    display_name: str | None
    timezone: str
    locale: str
    report_email_enabled: bool
    report_telegram_enabled: bool
    report_extra_email: str | None
    telegram_linked: bool


class UserPatch(BaseModel):
    display_name: str | None = None
    timezone: str | None = None
    locale: str | None = None
    report_email_enabled: bool | None = None
    report_telegram_enabled: bool | None = None
    report_extra_email: EmailStr | None = None


class DeviceOut(BaseModel):
    id: str
    name: str
    model: str | None
    form_factor: str
    role: Role
    platforms: list[Platform]
    last_seen_at: datetime
    online: bool = False


class DevicePatch(BaseModel):
    name: str | None = None
    role: Role | None = None
    platforms: list[Platform] | None = None


class OfferIn(BaseModel):
    id: str = Field(min_length=8, max_length=64)
    platform: Platform
    product: str | None = None
    captured_at: datetime
    currency: str = "PLN"
    price: float = Field(ge=0)
    price_net: float = Field(ge=0)
    pickup_km: float | None = None
    pickup_min: float | None = None
    trip_km: float | None = None
    trip_min: float | None = None
    score: float | None = None
    verdict: Verdict
    outcome: Outcome = "PENDING"
    decided_by: Literal["DRIVER", "AUTO", "WHEEL", "SYSTEM"] | None = None
    is_night: bool = False
    is_long: bool = False
    on_trip: bool = False
    tags: list[str] = []
    payout: float | None = None

    @field_validator("captured_at")
    @classmethod
    def tz_aware(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            raise ValueError("captured_at must include a UTC offset")
        return v


class OfferBatchIn(BaseModel):
    offers: list[OfferIn] = Field(max_length=500)


class OfferBatchOut(BaseModel):
    stored: int
    updated: int


class SendReportIn(BaseModel):
    date: str | None = None
    channels: list[Literal["EMAIL", "TELEGRAM"]] = ["EMAIL", "TELEGRAM"]


class LinkCodeOut(BaseModel):
    code: str
    deep_link: str
    expires_at: datetime
