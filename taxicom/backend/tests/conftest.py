import os

os.environ["TAXICOM_DATABASE_URL"] = "sqlite://"
os.environ["TAXICOM_SCHEDULER"] = "0"
os.environ["TAXICOM_TELEGRAM_WEBHOOK_SECRET"] = "test-secret"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.db import Base, engine  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture()
def client():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    with TestClient(app) as c:
        yield c


def register(client, email="driver@example.com", device="Samsung S23 Bolt", role="BOTH", platforms=("BOLT",)):
    r = client.post(
        "/v1/auth/register",
        json={
            "email": email,
            "password": "Commando-2026",
            "timezone": "Europe/Warsaw",
            "locale": "ru",
            "device": {"name": device, "role": role, "platforms": list(platforms)},
        },
    )
    assert r.status_code == 201, r.text
    return r.json()


def login(client, email="driver@example.com", device="Pixel Uber", role="CAPTURE", platforms=("UBER",)):
    r = client.post(
        "/v1/auth/login",
        json={"email": email, "password": "Commando-2026",
              "device": {"name": device, "role": role, "platforms": list(platforms)}},
    )
    assert r.status_code == 200, r.text
    return r.json()


def auth(tokens):
    return {"Authorization": f"Bearer {tokens['access_token']}"}
