import json

from fastapi import APIRouter, Depends, HTTPException, WebSocket, WebSocketDisconnect, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import SessionLocal, get_db
from ..deps import Principal, current_principal, principal_from_token
from ..models import Device
from ..schemas import DeviceOut, DevicePatch
from ..services.bus import ALLOWED_TYPES, bus

router = APIRouter(prefix="/v1/devices", tags=["devices"])
ws_router = APIRouter(tags=["bus"])


def _out(d: Device, online: set[str]) -> DeviceOut:
    return DeviceOut(
        id=d.id,
        name=d.name,
        model=d.model,
        form_factor=d.form_factor,
        role=d.role,
        platforms=[x for x in d.platforms.split(",") if x],
        last_seen_at=d.last_seen_at,
        online=d.id in online,
    )


@router.get("", response_model=list[DeviceOut])
def list_devices(p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> list[DeviceOut]:
    online = bus.online(p.user.id)
    rows = db.scalars(select(Device).where(Device.user_id == p.user.id).order_by(Device.created_at)).all()
    return [_out(d, online) for d in rows]


@router.patch("/{device_id}", response_model=DeviceOut)
async def patch_device(
    device_id: str, body: DevicePatch, p: Principal = Depends(current_principal), db: Session = Depends(get_db)
) -> DeviceOut:
    d = db.get(Device, device_id)
    if d is None or d.user_id != p.user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "device_not_found")
    if body.name is not None:
        d.name = body.name
    if body.platforms is not None:
        d.platforms = ",".join(body.platforms)
    if body.role is not None and body.role != d.role:
        if body.role in ("COCKPIT", "BOTH"):
            # Exactly one decision-maker per account: demote the previous cockpit to capture-only.
            for other in db.scalars(select(Device).where(Device.user_id == p.user.id, Device.id != d.id)):
                if other.role == "COCKPIT":
                    other.role = "VIEWER" if not other.platforms else "CAPTURE"
                elif other.role == "BOTH":
                    other.role = "CAPTURE"
        d.role = body.role
    db.commit()
    await bus.broadcast(p.user.id, "server", {"type": "devices_changed"})
    return _out(d, bus.online(p.user.id))


@router.delete("/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_device(device_id: str, p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> None:
    d = db.get(Device, device_id)
    if d is None or d.user_id != p.user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "device_not_found")
    db.delete(d)
    db.commit()


@ws_router.websocket("/v1/bus")
async def account_bus(ws: WebSocket, token: str) -> None:
    with SessionLocal() as db:
        try:
            principal = principal_from_token(db, token)
        except HTTPException:
            await ws.close(code=4401)
            return
        user_id, device_id = principal.user.id, principal.device_id
    if device_id is None:
        await ws.close(code=4400)
        return
    await ws.accept()
    await bus.join(user_id, device_id, ws)
    try:
        while True:
            raw = await ws.receive_text()
            if len(raw) > 32_000:
                continue
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if not isinstance(msg, dict) or msg.get("type") not in ALLOWED_TYPES:
                continue
            if msg["type"] == "ping":
                await ws.send_text('{"type":"pong"}')
                continue
            to = msg.pop("to", None)
            await bus.broadcast(user_id, device_id, msg, to=to if isinstance(to, str) else None)
    except WebSocketDisconnect:
        pass
    finally:
        await bus.leave(user_id, device_id, ws)
