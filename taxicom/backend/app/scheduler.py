"""Automatic summaries: yesterday's report at 08:00 local time of each driver (docs/reports.md 5.2)."""

import asyncio
import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select

from .api.reports import deliver_daily, user_zone
from .db import SessionLocal
from .models import DeliveryLog, Offer, User

log = logging.getLogger("taxicom.scheduler")

MORNING_HOUR = 8


def run_due_reports(now_utc: datetime) -> int:
    sent = 0
    with SessionLocal() as db:
        for user in db.scalars(select(User)):
            local = now_utc.astimezone(user_zone(user))
            if local.hour != MORNING_HOUR:
                continue
            yesterday = (local.date() - timedelta(days=1)).isoformat()
            has_offers = db.scalar(select(Offer.id).where(Offer.user_id == user.id, Offer.local_date == yesterday).limit(1))
            if not has_offers:
                continue
            day_start = local.replace(hour=0, minute=0, second=0, microsecond=0).astimezone(timezone.utc)
            already = db.scalar(
                select(DeliveryLog.id).where(
                    DeliveryLog.user_id == user.id, DeliveryLog.kind == "DAILY", DeliveryLog.created_at >= day_start
                ).limit(1)
            )
            if already:
                continue
            deliver_daily(db, user, yesterday, ["EMAIL", "TELEGRAM"], kind="DAILY")
            sent += 1
    return sent


async def scheduler_loop(interval_s: int = 300) -> None:
    while True:
        try:
            n = await asyncio.to_thread(run_due_reports, datetime.now().astimezone())
            if n:
                log.info("sent %d morning reports", n)
        except Exception:
            log.exception("scheduler iteration failed")
        await asyncio.sleep(interval_s)
