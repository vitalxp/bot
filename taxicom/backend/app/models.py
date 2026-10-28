import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _uuid() -> str:
    return str(uuid.uuid4())


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    display_name: Mapped[str | None] = mapped_column(String(120))
    timezone: Mapped[str] = mapped_column(String(64), default="Europe/Warsaw")
    locale: Mapped[str] = mapped_column(String(8), default="pl")
    report_email_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    report_telegram_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    report_extra_email: Mapped[str | None] = mapped_column(String(320))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    devices: Mapped[list["Device"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    family_id: Mapped[str] = mapped_column(String(36), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    device_id: Mapped[str | None] = mapped_column(String(36))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)
    replaced_by: Mapped[str | None] = mapped_column(String(36))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    model: Mapped[str | None] = mapped_column(String(120))
    form_factor: Mapped[str] = mapped_column(String(16), default="PHONE")  # PHONE | TABLET
    role: Mapped[str] = mapped_column(String(16), default="BOTH")  # CAPTURE | COCKPIT | BOTH | VIEWER
    platforms: Mapped[str] = mapped_column(String(64), default="")  # csv of BOLT,UBER,FREENOW
    app_version: Mapped[str | None] = mapped_column(String(32))
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    user: Mapped[User] = relationship(back_populates="devices")


class Offer(Base):
    """One order offer seen by a CAPTURE device, with the decision and its outcome."""

    __tablename__ = "offers"
    __table_args__ = (Index("ix_offers_user_local_date", "user_id", "local_date"),)

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    device_id: Mapped[str | None] = mapped_column(String(36))
    platform: Mapped[str] = mapped_column(String(16))
    product: Mapped[str | None] = mapped_column(String(64))
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    local_date: Mapped[str] = mapped_column(String(10))
    local_hour: Mapped[int] = mapped_column(Integer)
    currency: Mapped[str] = mapped_column(String(3), default="PLN")
    price: Mapped[float] = mapped_column(Float)
    price_net: Mapped[float] = mapped_column(Float)
    pickup_km: Mapped[float | None] = mapped_column(Float)
    pickup_min: Mapped[float | None] = mapped_column(Float)
    trip_km: Mapped[float | None] = mapped_column(Float)
    trip_min: Mapped[float | None] = mapped_column(Float)
    score: Mapped[float | None] = mapped_column(Float)
    verdict: Mapped[str] = mapped_column(String(8))  # TAKE | MAYBE | SKIP
    outcome: Mapped[str] = mapped_column(String(12), default="PENDING")
    decided_by: Mapped[str | None] = mapped_column(String(12))  # DRIVER | AUTO | WHEEL
    is_night: Mapped[bool] = mapped_column(Boolean, default=False)
    is_long: Mapped[bool] = mapped_column(Boolean, default=False)
    on_trip: Mapped[bool] = mapped_column(Boolean, default=False)
    tags: Mapped[str] = mapped_column(String(255), default="")
    payout: Mapped[float | None] = mapped_column(Float)


class TelegramLink(Base):
    __tablename__ = "telegram_links"

    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    chat_id: Mapped[str] = mapped_column(String(32), unique=True)
    username: Mapped[str | None] = mapped_column(String(64))
    linked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class LinkCode(Base):
    __tablename__ = "link_codes"

    code: Mapped[str] = mapped_column(String(16), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class DeliveryLog(Base):
    __tablename__ = "delivery_log"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    channel: Mapped[str] = mapped_column(String(16))  # EMAIL | TELEGRAM
    kind: Mapped[str] = mapped_column(String(16))  # DAILY | WEEKLY | SHIFT
    status: Mapped[str] = mapped_column(String(16))  # SENT | SKIPPED | FAILED
    detail: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
