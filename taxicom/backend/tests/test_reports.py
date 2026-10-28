from conftest import auth, register

from app.api.telegram import handle_update
from app.db import SessionLocal


def offer(i, hour, platform="BOLT", net=30.0, outcome="ACCEPTED", verdict="TAKE", on_trip=False, tags=(), score=1.2):
    return {
        "id": f"offer-{i:04d}",
        "platform": platform,
        "captured_at": f"2026-10-27T{hour:02d}:{(i * 7) % 60:02d}:00+01:00",
        "price": net if platform != "UBER" else round(net / 0.6925, 2),
        "price_net": net,
        "pickup_km": 1.5,
        "pickup_min": 4,
        "trip_km": 8.0,
        "trip_min": 16,
        "score": score,
        "verdict": verdict,
        "outcome": outcome,
        "on_trip": on_trip,
        "tags": list(tags),
    }


def seed(client, t):
    offers = [
        offer(1, 7, net=28),
        offer(2, 7, platform="UBER", net=22, outcome="DECLINED", verdict="TAKE"),
        offer(3, 8, net=12, outcome="IGNORED", verdict="SKIP", on_trip=True, score=0.5),
        offer(4, 8, platform="UBER", net=40),
        offer(5, 8, platform="FREENOW", net=18, outcome="EXPIRED", verdict="MAYBE", score=0.95),
        offer(6, 17, net=9, outcome="ACCEPTED", verdict="SKIP", score=0.4, tags=("CASH",)),
        offer(7, 17, net=8, outcome="DECLINED", verdict="SKIP", tags=("NO_PENALTY_DECLINE",), score=0.3),
    ]
    r = client.post("/v1/offers/batch", headers=auth(t), json={"offers": offers})
    assert r.json() == {"stored": 7, "updated": 0}


def test_ingest_is_idempotent_and_updates_outcome(client):
    t = register(client)
    seed(client, t)
    upd = offer(5, 8, platform="FREENOW", net=18, outcome="ACCEPTED", verdict="MAYBE")
    r = client.post("/v1/offers/batch", headers=auth(t), json={"offers": [upd]})
    assert r.json() == {"stored": 0, "updated": 1}


def test_daily_report_metrics(client):
    t = register(client)
    seed(client, t)
    r = client.get("/v1/reports/daily", params={"date": "2026-10-27"}, headers=auth(t)).json()
    assert r["offers_total"] == 7
    assert r["accepted"] == 3
    assert r["ignored_on_trip"] == 1
    assert r["net_accepted"] == 77.0
    assert [h["hour"] for h in r["hours"]] == [7, 8, 17]
    # IGNORED and no-penalty declines are excluded: 3 taken of 5 countable.
    assert r["acceptance_rate"] == 0.6
    split = {p["platform"]: p for p in r["platform_split"]}
    assert split["UBER"]["accepted"] == 1 and split["UBER"]["acceptance_rate"] == 0.5
    assert r["missed_value_est"] == 22.0
    assert r["regret_accepted"] == 1
    assert r["cash_share"] == round(1 / 3, 3)
    assert any("тихо проигнорированы" in i for i in r["insights"])


def test_report_html_and_csv(client):
    t = register(client)
    seed(client, t)
    html = client.get("/v1/reports/daily.html", params={"date": "2026-10-27"}, headers=auth(t)).text
    assert "TAXI COMMANDOS" in html and "77" in html
    csv = client.get("/v1/reports/export", params={"from": "2026-10-27", "to": "2026-10-27"}, headers=auth(t)).text
    assert csv.count("\n") == 8


def test_send_report_without_providers_is_skipped(client):
    t = register(client)
    seed(client, t)
    r = client.post("/v1/reports/send", headers=auth(t), json={"date": "2026-10-27"}).json()
    assert r["results"] == {"EMAIL:driver@example.com": "SKIPPED", "TELEGRAM": "NOT_LINKED"}


def test_telegram_linking_and_report_command(client):
    t = register(client)
    seed(client, t)
    code = client.post("/v1/telegram/link-code", headers=auth(t)).json()
    assert code["deep_link"].startswith("https://t.me/taxicom_bot?start=")

    def msg(text):
        return {"message": {"text": text, "chat": {"id": 555, "type": "private"},
                            "from": {"username": "kierowca", "language_code": "pl"}}}

    with SessionLocal() as db:
        assert "Kod nieważny" in handle_update(db, msg("/start WRONG123"))
        assert "привязан" in handle_update(db, msg(f"/start {code['code']}"))
        assert "/report" in handle_update(db, msg("/help"))
    assert client.get("/v1/me", headers=auth(t)).json()["telegram_linked"] is True


def test_webhook_requires_secret(client):
    r = client.post("/v1/telegram/webhook", json={"message": {}})
    assert r.status_code == 401
    r = client.post("/v1/telegram/webhook", json={"message": {}},
                    headers={"X-Telegram-Bot-Api-Secret-Token": "test-secret"})
    assert r.json() == {"ok": True}
