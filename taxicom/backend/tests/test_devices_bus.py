import json

from conftest import auth, login, register


def test_single_cockpit_per_account(client):
    t1 = register(client, device="Tablet", role="COCKPIT", platforms=())
    t2 = login(client, device="Phone Uber", role="CAPTURE", platforms=("UBER",))
    r = client.patch(f"/v1/devices/{t2['device_id']}", headers=auth(t2), json={"role": "BOTH"})
    assert r.status_code == 200
    roles = {d["name"]: d["role"] for d in client.get("/v1/devices", headers=auth(t1)).json()}
    assert roles == {"Tablet": "VIEWER", "Phone Uber": "BOTH"}


def test_bus_relays_offer_and_decision_between_devices(client):
    capture = register(client, device="Phone Bolt", role="CAPTURE")
    cockpit = login(client, device="Tablet", role="COCKPIT", platforms=())
    with client.websocket_connect(f"/v1/bus?token={cockpit['access_token']}") as ws_cockpit:
        with client.websocket_connect(f"/v1/bus?token={capture['access_token']}") as ws_capture:
            presence = json.loads(ws_cockpit.receive_text())
            assert presence["type"] == "presence" and presence["online"] is True

            ws_capture.send_text(json.dumps({"type": "offer", "offer": {"id": "o1", "price": 26.46}}))
            got = json.loads(ws_cockpit.receive_text())
            assert got["type"] == "offer" and got["from"] == capture["device_id"]

            ws_cockpit.send_text(json.dumps({"type": "decision", "offer_id": "o1", "action": "ACCEPT",
                                             "to": capture["device_id"]}))
            got = json.loads(ws_capture.receive_text())
            assert got == {**got, "type": "decision", "action": "ACCEPT", "from": cockpit["device_id"]}

            ws_capture.send_text(json.dumps({"type": "not_allowed"}))
            ws_capture.send_text(json.dumps({"type": "ping"}))
            assert json.loads(ws_capture.receive_text()) == {"type": "pong"}


def test_bus_rejects_bad_token(client):
    import pytest
    from starlette.websockets import WebSocketDisconnect

    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect("/v1/bus?token=garbage") as ws:
            ws.receive_text()
