import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..deps import Principal, current_principal
from ..models import DeliveryLog, Device, LinkCode, Offer, RefreshToken, TelegramLink, User
from ..schemas import DeviceIn, LoginIn, RefreshIn, RegisterIn, TokenOut, UserOut, UserPatch
from ..security import hash_password, hash_refresh, issue_access_token, new_refresh_secret, verify_password

router = APIRouter(prefix="/v1/auth", tags=["auth"])
me_router = APIRouter(prefix="/v1/me", tags=["account"])

# Equalises timing between "unknown e-mail" and "wrong password".
_DUMMY_HASH = hash_password("dummy-password-1")


def _as_utc(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _upsert_device(db: Session, user: User, d: DeviceIn) -> Device:
    existing = db.scalar(select(Device).where(Device.user_id == user.id, Device.name == d.name))
    device = existing or Device(user_id=user.id, name=d.name)
    device.model = d.model
    device.form_factor = d.form_factor
    device.platforms = ",".join(d.platforms)
    device.app_version = d.app_version
    if existing is None:
        device.role = d.role
        db.add(device)
    device.last_seen_at = datetime.now(timezone.utc)
    db.flush()
    return device


def _issue(db: Session, user: User, device_id: str, family_id: str | None = None) -> TokenOut:
    raw, digest = new_refresh_secret()
    rt = RefreshToken(
        user_id=user.id,
        family_id=family_id or str(uuid.uuid4()),
        token_hash=digest,
        device_id=device_id,
        expires_at=datetime.now(timezone.utc) + timedelta(days=get_settings().refresh_ttl_days),
    )
    db.add(rt)
    db.flush()
    access, ttl = issue_access_token(user.id, device_id)
    return TokenOut(access_token=access, refresh_token=raw, expires_in=ttl, user_id=user.id, device_id=device_id)


def user_out(db: Session, user: User) -> UserOut:
    linked = db.get(TelegramLink, user.id) is not None
    return UserOut(
        id=user.id,
        email=user.email,
        display_name=user.display_name,
        timezone=user.timezone,
        locale=user.locale,
        report_email_enabled=user.report_email_enabled,
        report_telegram_enabled=user.report_telegram_enabled,
        report_extra_email=user.report_extra_email,
        telegram_linked=linked,
    )


@router.post("/register", response_model=TokenOut, status_code=status.HTTP_201_CREATED)
def register(body: RegisterIn, db: Session = Depends(get_db)) -> TokenOut:
    email = body.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status.HTTP_409_CONFLICT, "email_taken")
    user = User(
        email=email,
        password_hash=hash_password(body.password),
        display_name=body.display_name,
        timezone=body.timezone,
        locale=body.locale,
    )
    db.add(user)
    db.flush()
    device = _upsert_device(db, user, body.device)
    out = _issue(db, user, device.id)
    db.commit()
    return out


@router.post("/login", response_model=TokenOut)
def login(body: LoginIn, db: Session = Depends(get_db)) -> TokenOut:
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    if user is None:
        verify_password(_DUMMY_HASH, body.password)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid_credentials")
    if not verify_password(user.password_hash, body.password):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid_credentials")
    device = _upsert_device(db, user, body.device)
    out = _issue(db, user, device.id)
    db.commit()
    return out


@router.post("/refresh", response_model=TokenOut)
def refresh(body: RefreshIn, db: Session = Depends(get_db)) -> TokenOut:
    rt = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hash_refresh(body.refresh_token)))
    if rt is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid_refresh")
    if rt.revoked:
        # Reuse of a rotated token: the family is compromised, log out every holder.
        db.execute(update(RefreshToken).where(RefreshToken.family_id == rt.family_id).values(revoked=True))
        db.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "refresh_reused")
    if _as_utc(rt.expires_at) < datetime.now(timezone.utc):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "refresh_expired")
    user = db.get(User, rt.user_id)
    out = _issue(db, user, rt.device_id, family_id=rt.family_id)
    rt.revoked = True
    rt.replaced_by = db.scalar(select(RefreshToken.id).where(RefreshToken.token_hash == hash_refresh(out.refresh_token)))
    db.commit()
    return out


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(body: RefreshIn, db: Session = Depends(get_db)) -> Response:
    rt = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hash_refresh(body.refresh_token)))
    if rt is not None:
        db.execute(update(RefreshToken).where(RefreshToken.family_id == rt.family_id).values(revoked=True))
        db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@me_router.get("", response_model=UserOut)
def me(p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> UserOut:
    return user_out(db, p.user)


@me_router.patch("", response_model=UserOut)
def patch_me(body: UserPatch, p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> UserOut:
    for field, value in body.model_dump(exclude_unset=True).items():
        setattr(p.user, field, value)
    db.add(p.user)
    db.commit()
    return user_out(db, p.user)


@me_router.delete("", status_code=status.HTTP_204_NO_CONTENT)
def delete_me(p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> Response:
    """Account and data deletion (GDPR art. 17, Google Play account-deletion requirement)."""
    for model in (Offer, RefreshToken, Device, TelegramLink, LinkCode, DeliveryLog):
        db.execute(delete(model).where(model.user_id == p.user.id))
    db.execute(delete(User).where(User.id == p.user.id))
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
