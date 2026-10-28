"""Hourly and daily reports over every offer the driver saw (docs/reports.md, sections 3.1–3.3)."""

from collections import defaultdict
from dataclasses import asdict, dataclass, field
from datetime import date, timedelta
from statistics import median

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Offer

PLATFORMS = ("BOLT", "UBER", "FREENOW")
TAKEN = {"ACCEPTED", "COMPLETED"}


@dataclass
class HourReport:
    hour: int
    offers_total: int = 0
    offers_by_platform: dict[str, int] = field(default_factory=dict)
    accepted: int = 0
    declined: int = 0
    ignored_on_trip: int = 0
    expired: int = 0
    auto_accepted: int = 0
    net_accepted: float = 0.0
    net_actual: float | None = None
    km_paid: float = 0.0
    km_pickup: float = 0.0
    busy_min: float = 0.0
    online_min: float = 0.0
    r_h_realized: float | None = None
    r_km_realized: float | None = None
    e_median_accepted: float | None = None
    e_median_declined: float | None = None
    is_night_hour: bool = False


@dataclass
class PlatformSplit:
    platform: str
    offers: int
    accepted: int
    net: float
    acceptance_rate: float | None


@dataclass
class DailyReport:
    date: str
    currency: str
    hours: list[HourReport]
    offers_total: int
    accepted: int
    declined: int
    ignored_on_trip: int
    expired: int
    net_accepted: float
    km_paid: float
    km_pickup: float
    busy_min: float
    online_min: float
    r_h_realized: float | None
    r_km_realized: float | None
    acceptance_rate: float | None
    platform_split: list[PlatformSplit]
    best_hour: int | None
    worst_hour: int | None
    advisor_agreement: float | None
    missed_value_est: float
    regret_accepted: int
    long_trips_accepted: int
    night_net: float
    cash_share: float | None
    cost_est: float
    profit_est: float
    insights: list[str]
    estimated: bool

    def as_dict(self) -> dict:
        return asdict(self)


def _offers_for(db: Session, user_id: str, local_date: str) -> list[Offer]:
    return list(
        db.scalars(
            select(Offer).where(Offer.user_id == user_id, Offer.local_date == local_date).order_by(Offer.captured_at)
        )
    )


def _ratio(a: float, b: float) -> float | None:
    return round(a / b, 3) if b > 0 else None


def _online_minutes(offers: list[Offer]) -> float:
    """Minutes on line inside one hour, inferred from the offer stream (no shift log yet).

    Span between the first and last offer of the hour, padded by 10 minutes on each side,
    and never less than the paid busy time of that hour.
    """
    if not offers:
        return 0.0
    first, last = offers[0].captured_at, offers[-1].captured_at
    span = (last - first).total_seconds() / 60 + 20
    busy = sum((o.pickup_min or 0) + (o.trip_min or 0) for o in offers if o.outcome in TAKEN)
    return float(min(60.0, max(span, busy)))


def build_hour(hour: int, offers: list[Offer]) -> HourReport:
    h = HourReport(hour=hour)
    h.offers_total = len(offers)
    h.offers_by_platform = {p: sum(1 for o in offers if o.platform == p) for p in PLATFORMS}
    taken = [o for o in offers if o.outcome in TAKEN]
    h.accepted = len(taken)
    h.declined = sum(1 for o in offers if o.outcome == "DECLINED")
    h.ignored_on_trip = sum(1 for o in offers if o.outcome == "IGNORED")
    h.expired = sum(1 for o in offers if o.outcome == "EXPIRED")
    h.auto_accepted = sum(1 for o in taken if o.decided_by == "AUTO")
    h.net_accepted = round(sum(o.price_net for o in taken), 2)
    payouts = [o.payout for o in taken if o.payout is not None]
    h.net_actual = round(sum(payouts), 2) if payouts and len(payouts) == len(taken) else None
    h.km_paid = round(sum(o.trip_km or 0 for o in taken), 2)
    h.km_pickup = round(sum(o.pickup_km or 0 for o in taken), 2)
    h.busy_min = round(sum((o.pickup_min or 0) + (o.trip_min or 0) for o in taken), 1)
    h.online_min = round(_online_minutes(offers), 1)
    income = h.net_actual if h.net_actual is not None else h.net_accepted
    h.r_h_realized = round(income / h.online_min * 60, 2) if h.online_min else None
    h.r_km_realized = _ratio(income, h.km_paid + h.km_pickup)
    acc_e = [o.score for o in taken if o.score is not None]
    dec_e = [o.score for o in offers if o.outcome in ("DECLINED", "IGNORED", "EXPIRED") and o.score is not None]
    h.e_median_accepted = round(median(acc_e), 3) if acc_e else None
    h.e_median_declined = round(median(dec_e), 3) if dec_e else None
    h.is_night_hour = any(o.is_night for o in offers)
    return h


def build_daily(db: Session, user_id: str, local_date: str, c_km: float = 0.55, locale: str = "pl") -> DailyReport:
    offers = _offers_for(db, user_id, local_date)
    by_hour: dict[int, list[Offer]] = defaultdict(list)
    for o in offers:
        by_hour[o.local_hour].append(o)
    hours = [build_hour(hr, by_hour[hr]) for hr in sorted(by_hour)]

    taken = [o for o in offers if o.outcome in TAKEN]
    net = round(sum(h.net_actual if h.net_actual is not None else h.net_accepted for h in hours), 2)
    km_paid = round(sum(h.km_paid for h in hours), 2)
    km_pickup = round(sum(h.km_pickup for h in hours), 2)
    online = round(sum(h.online_min for h in hours), 1)
    busy = round(sum(h.busy_min for h in hours), 1)

    # Offers silently ignored during a trip do not count against the platform acceptance rate.
    countable = [o for o in offers if o.outcome != "IGNORED" and "NO_PENALTY_DECLINE" not in o.tags.split(",")]
    split = []
    for p in PLATFORMS:
        po = [o for o in offers if o.platform == p]
        if not po:
            continue
        pc = [o for o in countable if o.platform == p]
        pt = [o for o in po if o.outcome in TAKEN]
        split.append(
            PlatformSplit(
                platform=p,
                offers=len(po),
                accepted=len(pt),
                net=round(sum(o.price_net for o in pt), 2),
                acceptance_rate=_ratio(sum(1 for o in pc if o.outcome in TAKEN), len(pc)),
            )
        )

    rated = [h for h in hours if h.r_h_realized is not None and h.online_min >= 15]
    best = max(rated, key=lambda h: h.r_h_realized, default=None)
    worst = min(rated, key=lambda h: h.r_h_realized, default=None)

    decided = [o for o in offers if o.outcome in TAKEN | {"DECLINED", "IGNORED"}]
    agree = sum(
        1
        for o in decided
        if (o.outcome in TAKEN and o.verdict == "TAKE") or (o.outcome not in TAKEN and o.verdict == "SKIP")
    )
    missed = [o for o in offers if o.verdict == "TAKE" and o.outcome in ("DECLINED", "EXPIRED") and not o.on_trip]
    regret = [o for o in taken if o.verdict == "SKIP"]
    cash = [o for o in taken if "CASH" in o.tags.split(",")]
    cost = round(c_km * (km_paid + km_pickup), 2)
    currency = offers[0].currency if offers else "PLN"

    report = DailyReport(
        date=local_date,
        currency=currency,
        hours=hours,
        offers_total=len(offers),
        accepted=len(taken),
        declined=sum(h.declined for h in hours),
        ignored_on_trip=sum(h.ignored_on_trip for h in hours),
        expired=sum(h.expired for h in hours),
        net_accepted=net,
        km_paid=km_paid,
        km_pickup=km_pickup,
        busy_min=busy,
        online_min=online,
        r_h_realized=round(net / online * 60, 2) if online else None,
        r_km_realized=_ratio(net, km_paid + km_pickup),
        acceptance_rate=_ratio(sum(1 for o in countable if o.outcome in TAKEN), len(countable)),
        platform_split=split,
        best_hour=best.hour if best else None,
        worst_hour=worst.hour if worst else None,
        advisor_agreement=_ratio(agree, len(decided)),
        missed_value_est=round(sum(o.price_net for o in missed), 2),
        regret_accepted=len(regret),
        long_trips_accepted=sum(1 for o in taken if o.is_long),
        night_net=round(sum(o.price_net for o in taken if o.is_night), 2),
        cash_share=_ratio(len(cash), len(taken)),
        cost_est=cost,
        profit_est=round(net - cost, 2),
        insights=[],
        estimated=any(h.net_actual is None for h in hours if h.accepted),
    )
    report.insights = insights(report, best, worst, len(missed), locale)
    return report


INSIGHT_TEMPLATES = {
    "pl": {
        "best_worst": "Najlepsza godzina {bh:02d}:00–{bh1:02d}:00: {brh:.0f} {cur}/h przy {bo} ofertach. "
        "Najsłabsza {wh:02d}:00: {wrh:.0f} {cur}/h.",
        "missed": "Pominięto {n} opłacalnych ofert (≈ {v:.0f} {cur}), gdy byłeś wolny.",
        "ignored": "{n} ofert w trakcie kursu zignorowano po cichu — wskaźnik akceptacji nienaruszony.",
        "acceptance": "{p}: akceptacja dziś {r:.0%} — uważaj na próg 50%.",
        "regret": "Przyjęto {n} słabych zleceń wbrew rekomendacji.",
        "low_rkm": "Średnio {rkm:.2f} {cur}/km z dojazdem — blisko kosztu auta ({cost:.0f} {cur}).",
    },
    "ru": {
        "best_worst": "Лучший час {bh:02d}:00–{bh1:02d}:00: {brh:.0f} {cur}/ч при {bo} предложениях. "
        "Худший {wh:02d}:00: {wrh:.0f} {cur}/ч.",
        "missed": "Пропущено {n} выгодных заказов (≈ {v:.0f} {cur}), пока вы были свободны.",
        "ignored": "{n} заказов во время поездки тихо проигнорированы — показатель принятия не пострадал.",
        "acceptance": "{p}: принятие сегодня {r:.0%} — осторожно с порогом 50%.",
        "regret": "Принято {n} слабых заказов вопреки совету.",
        "low_rkm": "В среднем {rkm:.2f} {cur}/км с подачей — близко к себестоимости ({cost:.0f} {cur}).",
    },
    "en": {
        "best_worst": "Best hour {bh:02d}:00–{bh1:02d}:00: {brh:.0f} {cur}/h from {bo} offers. "
        "Worst {wh:02d}:00: {wrh:.0f} {cur}/h.",
        "missed": "Missed {n} good offers (≈ {v:.0f} {cur}) while you were free.",
        "ignored": "{n} offers during trips were silently ignored — acceptance rate untouched.",
        "acceptance": "{p}: acceptance today {r:.0%} — mind the 50% threshold.",
        "regret": "Accepted {n} weak orders against the advice.",
        "low_rkm": "Average {rkm:.2f} {cur}/km incl. pickup — close to car cost ({cost:.0f} {cur}).",
    },
}


def insights(
    r: DailyReport, best: HourReport | None, worst: HourReport | None, missed_n: int, locale: str = "pl"
) -> list[str]:
    t = INSIGHT_TEMPLATES.get(locale, INSIGHT_TEMPLATES["en"])
    cur = "zł" if r.currency == "PLN" else r.currency
    out: list[str] = []
    if best and worst and best.hour != worst.hour:
        out.append(
            t["best_worst"].format(
                bh=best.hour, bh1=(best.hour + 1) % 24, brh=best.r_h_realized, bo=best.offers_total,
                wh=worst.hour, wrh=worst.r_h_realized, cur=cur,
            )
        )
    if missed_n:
        out.append(t["missed"].format(n=missed_n, v=r.missed_value_est, cur=cur))
    if r.ignored_on_trip:
        out.append(t["ignored"].format(n=r.ignored_on_trip))
    for p in r.platform_split:
        if p.acceptance_rate is not None and p.acceptance_rate < 0.6:
            out.append(t["acceptance"].format(p=p.platform, r=p.acceptance_rate))
    if r.regret_accepted:
        out.append(t["regret"].format(n=r.regret_accepted))
    if r.r_km_realized is not None and r.r_km_realized < 1.0:
        out.append(t["low_rkm"].format(rkm=r.r_km_realized, cost=r.cost_est, cur=cur))
    return out[:5]


def build_week(db: Session, user_id: str, end: date, locale: str = "pl") -> list[DailyReport]:
    return [build_daily(db, user_id, (end - timedelta(days=i)).isoformat(), locale=locale) for i in range(6, -1, -1)]
