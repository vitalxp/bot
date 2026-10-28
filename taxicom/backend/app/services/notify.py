import logging
import smtplib
from email.message import EmailMessage

import httpx

from ..config import get_settings

log = logging.getLogger("taxicom.notify")


class DeliveryResult:
    def __init__(self, status: str, detail: str | None = None) -> None:
        self.status = status
        self.detail = detail


def send_email(to: str, subject: str, html: str, text: str) -> DeliveryResult:
    s = get_settings()
    if not s.smtp_host:
        log.info("SMTP not configured, skipping e-mail to %s: %s", to, subject)
        return DeliveryResult("SKIPPED", "smtp_not_configured")
    msg = EmailMessage()
    msg["From"] = s.mail_from
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(text)
    msg.add_alternative(html, subtype="html")
    try:
        with smtplib.SMTP(s.smtp_host, s.smtp_port, timeout=15) as smtp:
            smtp.starttls()
            if s.smtp_user:
                smtp.login(s.smtp_user, s.smtp_password)
            smtp.send_message(msg)
        return DeliveryResult("SENT")
    except (smtplib.SMTPException, OSError) as e:
        log.warning("e-mail delivery failed: %s", e)
        return DeliveryResult("FAILED", str(e))


def send_telegram(chat_id: str, html_text: str) -> DeliveryResult:
    s = get_settings()
    if not s.telegram_bot_token:
        log.info("Telegram not configured, skipping message to %s", chat_id)
        return DeliveryResult("SKIPPED", "telegram_not_configured")
    try:
        resp = httpx.post(
            f"https://api.telegram.org/bot{s.telegram_bot_token}/sendMessage",
            json={"chat_id": chat_id, "text": html_text, "parse_mode": "HTML", "disable_web_page_preview": True},
            timeout=10,
        )
        if resp.status_code == 200 and resp.json().get("ok"):
            return DeliveryResult("SENT")
        return DeliveryResult("FAILED", resp.text[:500])
    except httpx.HTTPError as e:
        return DeliveryResult("FAILED", str(e))
