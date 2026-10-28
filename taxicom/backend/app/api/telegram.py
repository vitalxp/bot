"""@taxicom_bot: account linking via deep link `/start CODE`, `/report yesterday|week`, `/unlink`."""

import hmac
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy import delete
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..deps import Principal, current_principal
from ..models import LinkCode, TelegramLink, User
from ..schemas import LinkCodeOut
from ..security import new_link_code
from ..services.notify import send_telegram
from ..services.render import render_telegram, render_week_telegram
from ..services.reports import build_daily, build_week
from .reports import local_today

router = APIRouter(prefix="/v1/telegram", tags=["telegram"])

REPLIES = {
    "linked": {"pl": "✅ Konto Taxi Commandos połączone. Raporty będą przychodzić tutaj.",
               "ru": "✅ Аккаунт Taxi Commandos привязан. Отчёты будут приходить сюда.",
               "en": "✅ Taxi Commandos account linked. Reports will arrive here."},
    "bad_code": {"pl": "Kod nieważny lub wygasł. Wygeneruj nowy w aplikacji: Ustawienia → Raporty → Telegram.",
                 "ru": "Код недействителен или истёк. Создайте новый в приложении: Настройки → Отчёты → Telegram.",
                 "en": "Code is invalid or expired. Create a new one in the app: Settings → Reports → Telegram."},
    "not_linked": {"pl": "Najpierw połącz konto w aplikacji Taxi Commandos.",
                   "ru": "Сначала привяжите аккаунт в приложении Taxi Commandos.",
                   "en": "Link your account in the Taxi Commandos app first."},
    "unlinked": {"pl": "Konto odłączone.", "ru": "Аккаунт отвязан.", "en": "Account unlinked."},
    "help": {"pl": "/report yesterday — wczoraj\n/report today — dziś\n/report week — tydzień\n/unlink — odłącz",
             "ru": "/report yesterday — вчера\n/report today — сегодня\n/report week — неделя\n/unlink — отвязать",
             "en": "/report yesterday\n/report today\n/report week\n/unlink"},
}


def _say(key: str, locale: str) -> str:
    return REPLIES[key].get(locale, REPLIES[key]["en"])


def _as_utc(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _locale_from_tg(code: str | None) -> str:
    return code if code in ("pl", "ru") else "en"


@router.post("/link-code", response_model=LinkCodeOut)
def link_code(p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> LinkCodeOut:
    s = get_settings()
    db.execute(delete(LinkCode).where(LinkCode.user_id == p.user.id))
    code = LinkCode(code=new_link_code(), user_id=p.user.id,
                    expires_at=datetime.now(timezone.utc) + timedelta(minutes=s.link_code_ttl_minutes))
    db.add(code)
    db.commit()
    return LinkCodeOut(code=code.code, deep_link=f"https://t.me/{s.telegram_bot_username}?start={code.code}",
                       expires_at=code.expires_at)


@router.delete("/link", status_code=status.HTTP_204_NO_CONTENT)
def unlink(p: Principal = Depends(current_principal), db: Session = Depends(get_db)) -> None:
    db.execute(delete(TelegramLink).where(TelegramLink.user_id == p.user.id))
    db.commit()


def handle_update(db: Session, update: dict) -> str | None:
    """Processes one Telegram update and returns the reply text (also sent via Bot API)."""
    msg = update.get("message") or {}
    text = (msg.get("text") or "").strip()
    chat = msg.get("chat") or {}
    sender = msg.get("from") or {}
    if not text or chat.get("type") != "private":
        return None
    chat_id = str(chat["id"])
    locale = _locale_from_tg(sender.get("language_code"))
    cmd, _, arg = text.partition(" ")
    cmd = cmd.split("@")[0].lower()
    arg = arg.strip()
    link = db.query(TelegramLink).filter(TelegramLink.chat_id == chat_id).one_or_none()
    user = db.get(User, link.user_id) if link else None
    if user:
        locale = user.locale

    if cmd == "/start":
        if not arg:
            return _say("help", locale) if user else _say("not_linked", locale)
        code = db.get(LinkCode, arg.upper())
        if code is None or _as_utc(code.expires_at) < datetime.now(timezone.utc):
            return _say("bad_code", locale)
        db.execute(delete(TelegramLink).where((TelegramLink.user_id == code.user_id) | (TelegramLink.chat_id == chat_id)))
        db.add(TelegramLink(user_id=code.user_id, chat_id=chat_id, username=sender.get("username")))
        db.delete(code)
        db.commit()
        linked_user = db.get(User, code.user_id)
        return _say("linked", linked_user.locale if linked_user else locale)

    if user is None:
        return _say("not_linked", locale)
    if cmd == "/unlink":
        db.execute(delete(TelegramLink).where(TelegramLink.chat_id == chat_id))
        db.commit()
        return _say("unlinked", locale)
    if cmd == "/report":
        today = local_today(user)
        if arg in ("week", "неделя", "tydzień", "tydzien"):
            return render_week_telegram(build_week(db, user.id, today, locale=locale), locale)
        day = today if arg in ("today", "сегодня", "dziś", "dzis") else today - timedelta(days=1)
        return render_telegram(build_daily(db, user.id, day.isoformat(), locale=locale), locale)
    return _say("help", locale)


@router.post("/webhook")
def webhook(
    update: dict,
    db: Session = Depends(get_db),
    x_telegram_bot_api_secret_token: str | None = Header(default=None),
) -> dict:
    secret = get_settings().telegram_webhook_secret
    if not secret or not hmac.compare_digest(secret, x_telegram_bot_api_secret_token or ""):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "bad_secret")
    reply = handle_update(db, update)
    if reply:
        send_telegram(str(update["message"]["chat"]["id"]), reply)
    return {"ok": True}
