from conftest import auth, login, register


def test_register_login_me(client):
    t = register(client)
    me = client.get("/v1/me", headers=auth(t)).json()
    assert me["email"] == "driver@example.com"
    assert me["telegram_linked"] is False
    t2 = login(client)
    assert t2["device_id"] != t["device_id"]


def test_duplicate_email_and_weak_password(client):
    register(client)
    r = client.post("/v1/auth/register", json={"email": "DRIVER@example.com", "password": "Commando-2026",
                                               "device": {"name": "x"}})
    assert r.status_code == 409
    r = client.post("/v1/auth/register", json={"email": "b@example.com", "password": "onlyletters",
                                               "device": {"name": "x"}})
    assert r.status_code == 422


def test_wrong_password(client):
    register(client)
    r = client.post("/v1/auth/login", json={"email": "driver@example.com", "password": "nope-12345",
                                            "device": {"name": "x"}})
    assert r.status_code == 401
    r = client.post("/v1/auth/login", json={"email": "ghost@example.com", "password": "nope-12345",
                                            "device": {"name": "x"}})
    assert r.status_code == 401


def test_refresh_rotation_and_reuse_detection(client):
    t = register(client)
    r1 = client.post("/v1/auth/refresh", json={"refresh_token": t["refresh_token"]})
    assert r1.status_code == 200
    new = r1.json()
    assert new["refresh_token"] != t["refresh_token"]
    # Replaying the rotated token revokes the whole family, including the fresh one.
    assert client.post("/v1/auth/refresh", json={"refresh_token": t["refresh_token"]}).status_code == 401
    assert client.post("/v1/auth/refresh", json={"refresh_token": new["refresh_token"]}).status_code == 401


def test_delete_account_removes_everything(client):
    t = register(client)
    assert client.delete("/v1/me", headers=auth(t)).status_code == 204
    assert client.get("/v1/me", headers=auth(t)).status_code == 401
    r = client.post("/v1/auth/login", json={"email": "driver@example.com", "password": "Commando-2026",
                                            "device": {"name": "x"}})
    assert r.status_code == 401
