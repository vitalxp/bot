import csv
import io
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import HTMLResponse, StreamingResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import Principal, current_principal
from ..models import DeliveryLog, Offer, TelegramLink, User
from ..schemas import OfferBatchIn, OfferBatchOut, SendReportIn
from ..services.notify import send_email, send_telegram
from ..services.render import render_email, render_telegram
from ..services.reports import build_daily, build_week

offers_router = APIRouter(prefix="/v1/offers", tags=["offers"])
reports_router = APIRouter(prefix="/v1/reports", tags=["reports"])


def user_zone(user: User) -> ZoneInfo:
    try:
        return ZoneInfo(user.timezone)
    except ZoneInfoNotFoundError:
        return ZoneInfo("UTC")


def local_today(user: User) -> date:
    return datetime.now(user_zone(user)).date()


def _parse_date(value: str | None, user: User, default_offset_days: int = 0) -> str:
    if value is None:
        return (local_today(user) - timedelta(days=default_offset_days)).isoformat()
    try:
        return date.fromisoformat(value).isoformat()
    except ValueError:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "bad_date")


@offers_router.post("/batch", response_model=OfferBatchOut)
def ingest(body: OfferBatchIn, p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> OfferBatchOut:
    """Idempotent upsert: devices re-send offers when the outcome becomes known (accepted → completed, payout)."""
    zone = user_zone(p.user)
    stored = updated = 0
    for o in body.offers:
        local = o.captured_at.astimezone(zone)
        row = db.get(Offer, o.id)
        if row is not None and row.user_id != p.user.id:
            continue
        values = o.model_dump(exclude={"tags"})
        values["tags"] = ",".join(sorted(set(o.tags)))
        if row is None:
            db.add(Offer(**values, user_id=p.user.id, device_id=p.device_id,
                         local_date=local.date().isoformat(), local_hour=local.hour))
            stored += 1
        else:
            for k, v in values.items():
                setattr(row, k, v)
            row.local_date, row.local_hour = local.date().isoformat(), local.hour
            updated += 1
    db.commit()
    return OfferBatchOut(stored=stored, updated=updated)


@reports_router.get("/daily")
def daily(date: str | None = None, p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> dict:
    return build_daily(db, p.user.id, _parse_date(date, p.user), locale=p.user.locale).as_dict()


@reports_router.get("/range")
def range_(
    to: str | None = Query(default=None),
    days: int = Query(default=7, ge=1, le=62),
    p: Principal = Depends(current_principal),
    db: Session = Depends(get_db),
) -> list[dict]:
    end = date.fromisoformat(_parse_date(to, p.user))
    out = []
    for i in range(days - 1, -1, -1):
        r = build_daily(db, p.user.id, (end - timedelta(days=i)).isoformat(), locale=p.user.locale)
        out.append({k: v for k, v in r.as_dict().items() if k != "hours"})
    return out


@reports_router.get("/daily.html", response_class=HTMLResponse)
def daily_html(date: str | None = None, p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> str:
    r = build_daily(db, p.user.id, _parse_date(date, p.user), locale=p.user.locale)
    return render_email(r, p.user.locale)[1]


@reports_router.get("/export")
def export_csv(
    from_: str = Query(alias="from"),
    to: str = Query(),
    p: Principal = Depends(current_principal),
    db: Session = Depends(get_db),
) -> StreamingResponse:
    rows = db.scalars(
        select(Offer)
        .where(Offer.user_id == p.user.id, Offer.local_date >= _parse_date(from_, p.user),
               Offer.local_date <= _parse_date(to, p.user))
        .order_by(Offer.captured_at)
    ).all()
    buf = io.StringIO()
    w = csv.writer(buf, delimiter=";")
    cols = ["local_date", "local_hour", "platform", "product", "price", "price_net", "pickup_km", "pickup_min",
            "trip_km", "trip_min", "score", "verdict", "outcome", "decided_by", "is_night", "is_long", "tags", "payout"]
    w.writerow(cols)
    for o in rows:
        w.writerow([getattr(o, c) for c in cols])
    buf.seek(0)
    return StreamingResponse(
        iter([buf.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="taxicom_{from_}_{to}.csv"'},
    )


def deliver_daily(db: Session, user: User, local_date: str, channels: list[str], kind: str = "DAILY") -> dict:
    r = build_daily(db, user.id, local_date, locale=user.locale)
    results: dict[str, str] = {}
    if "EMAIL" in channels and user.report_email_enabled:
        subject, html, text = render_email(r, user.locale)
        for addr in filter(None, {user.email, user.report_extra_email}):
            res = send_email(addr, subject, html, text)
            results[f"EMAIL:{addr}"] = res.status
            db.add(DeliveryLog(user_id=user.id, channel="EMAIL", kind=kind, status=res.status, detail=res.detail))
    if "TELEGRAM" in channels and user.report_telegram_enabled:
        link = db.get(TelegramLink, user.id)
        if link is None:
            results["TELEGRAM"] = "NOT_LINKED"
        else:
            res = send_telegram(link.chat_id, render_telegram(r, user.locale))
            results["TELEGRAM"] = res.status
            db.add(DeliveryLog(user_id=user.id, channel="TELEGRAM", kind=kind, status=res.status, detail=res.detail))
    db.commit()
    return {"date": local_date, "results": results}


@reports_router.post("/send")
def send(body: SendReportIn, p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> dict:
    return deliver_daily(db, p.user, _parse_date(body.date, p.user, default_offset_days=1), list(body.channels), "SHIFT")


__all__ = ["offers_router", "reports_router", "deliver_daily", "build_week", "local_today"]
