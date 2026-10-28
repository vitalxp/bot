"""Graphical summaries for e-mail (inline-styled HTML, works in Gmail/Outlook) and Telegram (HTML mode)."""

from html import escape

from .reports import DailyReport

LABELS = {
    "pl": {"title": "Raport dnia", "income": "Dochód", "per_h": "na godzinę", "per_km": "na km",
           "offers": "Oferty", "accepted": "Przyjęte", "acceptance": "Akceptacja", "profit": "Zysk po kosztach auta",
           "hours": "Dochód według godzin", "est": "szacunkowo", "empty": "Brak ofert w tym dniu."},
    "ru": {"title": "Отчёт за день", "income": "Доход", "per_h": "в час", "per_km": "за км",
           "offers": "Предложения", "accepted": "Принято", "acceptance": "Принятие", "profit": "Прибыль за вычетом авто",
           "hours": "Доход по часам", "est": "оценочно", "empty": "В этот день заказов не было."},
    "en": {"title": "Daily report", "income": "Income", "per_h": "per hour", "per_km": "per km",
           "offers": "Offers", "accepted": "Accepted", "acceptance": "Acceptance", "profit": "Profit after car costs",
           "hours": "Income by hour", "est": "estimated", "empty": "No offers that day."},
}

BRAND = "#FFB800"
BG = "#0E1116"
CARD = "#171B22"
TEXT = "#E8EAED"
MUTED = "#9AA0A6"
PLATFORM_COLORS = {"BOLT": "#34D186", "UBER": "#E8EAED", "FREENOW": "#E5004B"}

SPARK = "▁▂▃▄▅▆▇█"


def _cur(r: DailyReport) -> str:
    return "zł" if r.currency == "PLN" else r.currency


def _fmt(v: float | None, digits: int = 0) -> str:
    return "—" if v is None else f"{v:,.{digits}f}".replace(",", " ")


def render_email(r: DailyReport, locale: str = "pl") -> tuple[str, str, str]:
    """Returns (subject, html, plain_text)."""
    t = LABELS.get(locale, LABELS["en"])
    cur = _cur(r)
    subject = f"Taxi Commandos · {t['title']} {r.date}: {_fmt(r.net_accepted)} {cur}"
    if not r.offers_total:
        return subject, f"<p>{t['empty']}</p>", t["empty"]

    peak = max((h.net_accepted for h in r.hours), default=0) or 1
    bars = "".join(
        f'<td style="vertical-align:bottom;padding:0 2px;text-align:center">'
        f'<div style="background:{BRAND};width:18px;height:{max(2, int(h.net_accepted / peak * 90))}px;'
        f'border-radius:4px 4px 0 0"></div>'
        f'<div style="color:{MUTED};font-size:10px;padding-top:4px">{h.hour:02d}</div></td>'
        for h in r.hours
    )
    split = "".join(
        f'<tr><td style="padding:4px 0;color:{PLATFORM_COLORS.get(p.platform, TEXT)};font-weight:600">'
        f"{p.platform.title()}</td>"
        f'<td style="text-align:right;color:{TEXT}">{p.accepted}/{p.offers}</td>'
        f'<td style="text-align:right;color:{TEXT}">{_fmt(p.net)} {cur}</td>'
        f'<td style="text-align:right;color:{MUTED}">{_fmt((p.acceptance_rate or 0) * 100)}%</td></tr>'
        for p in r.platform_split
    )
    insights = "".join(f'<li style="margin:6px 0">{escape(i)}</li>' for i in r.insights)
    est = f' <span style="color:{MUTED};font-size:12px">({t["est"]})</span>' if r.estimated else ""

    def kpi(label: str, value: str) -> str:
        return (
            f'<td style="background:{CARD};border-radius:12px;padding:12px 14px;width:33%">'
            f'<div style="color:{MUTED};font-size:12px">{label}</div>'
            f'<div style="color:{TEXT};font-size:20px;font-weight:700;padding-top:2px">{value}</div></td>'
        )

    html = f"""<!doctype html><html><body style="margin:0;background:{BG};font-family:Inter,Roboto,Arial,sans-serif">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;margin:0 auto;padding:24px">
<tr><td style="color:{BRAND};font-weight:800;letter-spacing:.5px">TAXI COMMANDOS</td></tr>
<tr><td style="color:{TEXT};font-size:14px;padding-top:4px">{t['title']} · {r.date}</td></tr>
<tr><td style="padding-top:16px;color:{MUTED};font-size:13px">{t['income']}{est}</td></tr>
<tr><td style="color:{TEXT};font-size:40px;font-weight:800">{_fmt(r.net_accepted)} {cur}</td></tr>
<tr><td style="padding-top:12px"><table role="presentation" width="100%" cellspacing="8" cellpadding="0"><tr>
{kpi(t['per_h'], f"{_fmt(r.r_h_realized)} {cur}")}{kpi(t['per_km'], f"{_fmt(r.r_km_realized, 2)} {cur}")}
{kpi(t['acceptance'], f"{_fmt((r.acceptance_rate or 0) * 100)}%")}</tr></table></td></tr>
<tr><td style="padding-top:16px;color:{MUTED};font-size:13px">{t['hours']}</td></tr>
<tr><td style="background:{CARD};border-radius:12px;padding:16px"><table role="presentation" cellspacing="0"
cellpadding="0"><tr style="height:110px">{bars}</tr></table></td></tr>
<tr><td style="padding-top:16px"><table role="presentation" width="100%" style="background:{CARD};border-radius:12px;
padding:12px 16px;font-size:14px">{split}</table></td></tr>
<tr><td style="padding-top:16px;color:{TEXT};font-size:14px"><ul style="padding-left:18px;margin:0">{insights}</ul></td></tr>
<tr><td style="padding-top:12px;color:{MUTED};font-size:13px">{t['profit']}: <b style="color:{TEXT}">
{_fmt(r.profit_est)} {cur}</b></td></tr>
<tr><td style="padding-top:24px;color:{MUTED};font-size:11px">taxicom.app</td></tr>
</table></body></html>"""

    text = "\n".join(
        [
            f"{t['title']} {r.date}",
            f"{t['income']}: {_fmt(r.net_accepted)} {cur} ({_fmt(r.r_h_realized)} {cur}/h)",
            f"{t['offers']}: {r.offers_total}, {t['accepted']}: {r.accepted}",
            *[f"- {i}" for i in r.insights],
        ]
    )
    return subject, html, text


def sparkline(values: list[float]) -> str:
    if not values:
        return ""
    peak = max(values) or 1
    return "".join(SPARK[min(len(SPARK) - 1, int(v / peak * (len(SPARK) - 1)))] for v in values)


def render_telegram(r: DailyReport, locale: str = "pl") -> str:
    t = LABELS.get(locale, LABELS["en"])
    cur = _cur(r)
    if not r.offers_total:
        return f"<b>{t['title']} {r.date}</b>\n{t['empty']}"
    lines = [
        f"<b>🚕 {t['title']} · {r.date}</b>",
        f"<b>{_fmt(r.net_accepted)} {cur}</b>{' (' + t['est'] + ')' if r.estimated else ''}",
        f"{_fmt(r.r_h_realized)} {cur}/h · {_fmt(r.r_km_realized, 2)} {cur}/km · "
        f"{t['acceptance']} {_fmt((r.acceptance_rate or 0) * 100)}%",
        "",
        f"<code>{sparkline([h.net_accepted for h in r.hours])}</code>",
        f"<code>{r.hours[0].hour:02d}{' ' * max(0, len(r.hours) - 4)}{r.hours[-1].hour:02d}</code>",
        "",
    ]
    for p in r.platform_split:
        lines.append(f"{p.platform.title()}: {p.accepted}/{p.offers} · {_fmt(p.net)} {cur}")
    if r.insights:
        lines.append("")
        lines += [f"• {escape(i)}" for i in r.insights]
    return "\n".join(lines)


def render_week_telegram(days: list[DailyReport], locale: str = "pl") -> str:
    cur = _cur(days[-1]) if days else "zł"
    total = sum(d.net_accepted for d in days)
    head = {"pl": "Tydzień", "ru": "Неделя", "en": "Week"}.get(locale, "Week")
    rows = [f"<b>🚕 {head} {days[0].date} – {days[-1].date}</b>", f"<b>{_fmt(total)} {cur}</b>", ""]
    rows.append(f"<code>{sparkline([d.net_accepted for d in days])}</code>")
    for d in days:
        rows.append(f"{d.date[5:]}: {_fmt(d.net_accepted)} {cur} · {d.accepted}/{d.offers_total}")
    return "\n".join(rows)
