"""Account bus: relays messages between the devices of one account (CAPTURE phones, COCKPIT, tablets).

Single-process hub. With several API replicas this is backed by Redis pub/sub on channel
`account:{user_id}`; the interface stays the same.
"""

import asyncio
import json
import time
from collections import defaultdict

from fastapi import WebSocket

# Message types a device may publish. Anything else is rejected so a buggy client
# cannot flood the cockpit with unknown payloads.
ALLOWED_TYPES = {
    "offer",  # CAPTURE -> COCKPIT: parsed and scored offer
    "offer_gone",  # CAPTURE -> COCKPIT: card disappeared (accepted elsewhere / expired)
    "decision",  # COCKPIT -> CAPTURE: accept / decline / ignore for an offer id
    "decision_ack",  # CAPTURE -> COCKPIT: tap performed or failed
    "trip_state",  # any -> all: on trip / free, per platform
    "acceptance",  # CAPTURE -> all: acceptance-rate reading from platform screen
    "ping",
}


class AccountBus:
    def __init__(self) -> None:
        self._sockets: dict[str, dict[str, WebSocket]] = defaultdict(dict)
        self._lock = asyncio.Lock()

    async def join(self, user_id: str, device_id: str, ws: WebSocket) -> None:
        async with self._lock:
            old = self._sockets[user_id].get(device_id)
            self._sockets[user_id][device_id] = ws
        if old is not None and old is not ws:
            await _safe_close(old)
        await self.broadcast(user_id, device_id, {"type": "presence", "device_id": device_id, "online": True})

    async def leave(self, user_id: str, device_id: str, ws: WebSocket) -> None:
        async with self._lock:
            if self._sockets[user_id].get(device_id) is ws:
                del self._sockets[user_id][device_id]
        await self.broadcast(user_id, device_id, {"type": "presence", "device_id": device_id, "online": False})

    def online(self, user_id: str) -> set[str]:
        return set(self._sockets.get(user_id, {}))

    async def broadcast(self, user_id: str, sender: str, message: dict, to: str | None = None) -> int:
        envelope = {**message, "from": sender, "ts": int(time.time() * 1000)}
        data = json.dumps(envelope, separators=(",", ":"))
        targets = [
            (did, ws)
            for did, ws in list(self._sockets.get(user_id, {}).items())
            if did != sender and (to is None or did == to)
        ]
        delivered = 0
        for _, ws in targets:
            try:
                await ws.send_text(data)
                delivered += 1
            except Exception:
                pass
        return delivered


async def _safe_close(ws: WebSocket) -> None:
    try:
        await ws.close(code=4000, reason="replaced")
    except Exception:
        pass


bus = AccountBus()
