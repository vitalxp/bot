import hashlib
import secrets
import uuid
from datetime import datetime, timedelta, timezone
from functools import lru_cache

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerifyMismatchError
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from .config import get_settings

_hasher = PasswordHasher(time_cost=3, memory_cost=64 * 1024, parallelism=2)


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password_hash: str, password: str) -> bool:
    try:
        return _hasher.verify(password_hash, password)
    except (VerifyMismatchError, InvalidHashError):
        return False


@lru_cache
def _keys():
    pem = get_settings().jwt_private_key_pem
    if pem:
        private = serialization.load_pem_private_key(pem.encode(), password=None)
    else:
        private = ec.generate_private_key(ec.SECP256R1())
    return private, private.public_key()


def issue_access_token(user_id: str, device_id: str | None) -> tuple[str, int]:
    s = get_settings()
    now = datetime.now(timezone.utc)
    claims = {
        "iss": s.jwt_issuer,
        "sub": user_id,
        "did": device_id,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(seconds=s.access_ttl_seconds)).timestamp()),
        "jti": uuid.uuid4().hex,
    }
    private, _ = _keys()
    return jwt.encode(claims, private, algorithm="ES256"), s.access_ttl_seconds


def decode_access_token(token: str) -> dict:
    s = get_settings()
    _, public = _keys()
    return jwt.decode(token, public, algorithms=["ES256"], issuer=s.jwt_issuer)


def new_refresh_secret() -> tuple[str, str]:
    raw = secrets.token_urlsafe(48)
    return raw, hash_refresh(raw)


def hash_refresh(raw: str) -> str:
    return hashlib.sha256(raw.encode()).hexdigest()


def new_link_code() -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(8))
