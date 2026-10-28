package app.taxicom.core.scoring

import app.taxicom.core.model.OfferTag
import app.taxicom.core.model.OrderOffer
import app.taxicom.core.model.Platform
import app.taxicom.core.model.Verdict
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Regional reference rates for the current daypart (server-provided; defaults until data arrives). */
@Serializable
data class ReferenceRates(
    val medianRKm: Double = 2.6,
    val medianRH: Double = 48.0,
    val nightMedianRKm: Double = 3.0,
    val nightMedianRH: Double = 52.0,
)

/** Everything outside the offer that the score depends on. */
data class ScoringContext(
    val localMinuteOfDay: Int,
    /** Offers per hour over the last 60 minutes (λ). */
    val offersPerHour: Double,
    val idleMinutes: Int = 0,
    val onTrip: Boolean = false,
    /** Current acceptance rate on the offer's platform, if known. */
    val acceptanceRate: Double? = null,
    /** Recent E values in this region/daypart; empty until enough history is collected. */
    val recentScores: List<Double> = emptyList(),
    val rates: ReferenceRates = ReferenceRates(),
    /** Learned Uber fee (fraction), overrides the settings estimate when present. */
    val learnedUberFee: Double? = null,
    /** Expected value of the next order from the drop-off zone minus from here (demand-zones.md 5). */
    val destinationBonus: Double = 0.0,
    /** 1 − P(next order near the drop-off within 20 min); null when the demand field has no data. */
    val returnWeight: Double? = null,
    val bonusPlusActive: Boolean = false,
)

@Serializable
enum class Recommendation { ACCEPT, CONSIDER, DECLINE, IGNORE }

@Serializable
enum class Reason {
    BELOW_COST, OUT_OF_RANGE_FAR, PICKUP_TOO_LONG, PICKUP_FOR_NOTHING, CASH_REFUSED, LONG_TRIP_BELOW_FLOOR,
    SCHEDULED, LOW_RATING, NIGHT, LONG_TRIP, EXCLUSIVE, ON_TRIP_IGNORE, PROTECT_ACCEPTANCE, BONUS_PLUS,
    DESTINATION_HOT, DESTINATION_DEAD, FREE_DECLINE,
}

@Serializable
data class Score(
    val offerId: String,
    val net: Double,
    val totalKm: Double,
    val totalMin: Double,
    val rKm: Double,
    val rH: Double,
    val e: Double,
    val eStar: Double,
    val verdict: Verdict,
    val recommendation: Recommendation,
    val reasons: List<Reason>,
    val isNight: Boolean,
    val isLong: Boolean,
    val returnKm: Double? = null,
    val kmPerUnit: Double? = null,
    val hardRule: Boolean = false,
) {
    /** E relative to the threshold, e.g. +0.30 means 30 % better than what is worth taking now. */
    val relative: Double get() = if (eStar > 0) e / eStar - 1 else 0.0
}

class ScoringEngine(private val settings: ScoringSettings) {

    fun isNight(minuteOfDay: Int): Boolean {
        val s = settings.nightStartMin
        val e = settings.nightEndMin
        return if (s <= e) minuteOfDay in s until e else minuteOfDay >= s || minuteOfDay < e
    }

    fun net(offer: OrderOffer, learnedUberFee: Double? = null): Double {
        val shown = offer.price
        val platformNet = when (offer.platform) {
            Platform.BOLT -> if (settings.boltPriceIsNet) shown else shown * (1 - 0.25)
            // PL: card is already after Uber fee. Other regions / legacy: estimate fee out of the shown gross.
            Platform.UBER -> if (settings.uberPriceIsNet) shown
            else shown * (1 - (learnedUberFee ?: settings.uberFeeFraction))
            Platform.FREENOW -> shown * (1 - settings.freenowFeePct / 100.0)
        }
        // Fleet partner cut (and similar) still applies on top of whatever the platform shows.
        return platformNet * (1 - settings.fleetFeePct / 100.0)
    }

    /** Demand quantile p(λ): 0.25 at ≤ 2 offers/h … 0.70 at ≥ 10 offers/h, softened by idle time. */
    fun demandQuantile(ctx: ScoringContext, night: Boolean): Double {
        val l = ctx.offersPerHour
        var p = when {
            l <= 2 -> 0.25
            l >= 10 -> 0.70
            else -> 0.25 + (l - 2) / 8.0 * 0.45
        }
        if (ctx.idleMinutes > settings.idleSoftenMin) {
            p -= 0.1 * ((ctx.idleMinutes - settings.idleSoftenMin) / 10 + 1)
        }
        if (night) p = min(p, settings.nightPMax)
        return p.coerceIn(0.10, 0.90)
    }

    /**
     * E* = quantile of recent E at p(λ). Until 30 samples exist, assume E is log-normal around 1
     * (σ = 0.35), which reproduces the worked examples in architecture.md 4.5.
     */
    fun threshold(p: Double, recent: List<Double>): Double {
        if (recent.size >= 30) {
            val sorted = recent.sorted()
            val pos = p * (sorted.size - 1)
            val lo = sorted[pos.toInt()]
            val hi = sorted[min(sorted.size - 1, pos.toInt() + 1)]
            return lo + (hi - lo) * (pos - pos.toInt())
        }
        return exp(0.35 * inverseNormal(p))
    }

    fun score(offer: OrderOffer, ctx: ScoringContext): Score {
        val night = isNight(ctx.localMinuteOfDay)
        val reasons = mutableListOf<Reason>()
        if (night) reasons += Reason.NIGHT

        var net = net(offer, ctx.learnedUberFee) + ctx.destinationBonus
        if (ctx.bonusPlusActive && settings.bonusPlusPerTrip > 0) {
            net += settings.bonusPlusPerTrip
            reasons += Reason.BONUS_PLUS
        }
        if (ctx.destinationBonus > 2) reasons += Reason.DESTINATION_HOT
        if (ctx.destinationBonus < -2) reasons += Reason.DESTINATION_DEAD

        val pickupKm = offer.pickup?.km ?: 0.0
        val pickupMin = offer.pickup?.minutes?.toDouble() ?: 0.0
        val tripKm = offer.trip?.km ?: 0.0
        val tripMin = offer.trip?.minutes?.toDouble() ?: 0.0
        val costPerKm = settings.costPerKm * (if (night) settings.nightCostFactor else 1.0)
        val longKm = if (night) settings.nightLongTripKm ?: settings.longTripKm else settings.longTripKm
        val isLong = tripKm >= longKm

        var totalKm = pickupKm + tripKm
        var totalMin = pickupMin + tripMin
        var returnKm: Double? = null
        if (isLong) {
            reasons += Reason.LONG_TRIP
            // Without geo of the origin, the way back is approximated by the trip itself
            // (the driver started near the pickup point).
            val weight = ctx.returnWeight?.takeIf { settings.returnWeightAuto && !night } ?: settings.returnWeightFixed
            returnKm = tripKm
            totalKm += tripKm * weight
            totalMin += tripMin * weight
        }

        val rKm = if (totalKm > 0) net / totalKm else 0.0
        val rH = if (totalMin > 0) net / totalMin * 60 else 0.0
        val medKm = if (night) ctx.rates.nightMedianRKm else ctx.rates.medianRKm
        val medH = if (night) ctx.rates.nightMedianRH else ctx.rates.medianRH
        val a = settings.alpha.coerceIn(0.0, 1.0)
        var e = if (rKm > 0 && rH > 0) (rKm / medKm).pow(a) * (rH / medH).pow(1 - a) else 0.0

        val minRating = if (night) settings.nightMinRiderRating else settings.minRiderRating
        offer.riderRating?.let {
            if (it < minRating) {
                e *= if (night) 0.8 else 0.85
                reasons += Reason.LOW_RATING
            }
        }
        val cash = if (night) settings.nightCashPolicy ?: settings.cashPolicy else settings.cashPolicy
        if (OfferTag.CASH in offer.tags && cash == CashPolicy.PENALTY_10) e *= 0.9
        if (OfferTag.EXCLUSIVE in offer.tags) {
            e *= 1.03
            reasons += Reason.EXCLUSIVE
        }

        val p = demandQuantile(ctx, night)
        var eStar = threshold(p, ctx.recentScores)
        val freeDecline = OfferTag.NO_PENALTY_DECLINE in offer.tags
        if (freeDecline) reasons += Reason.FREE_DECLINE
        val acceptanceLow = ctx.acceptanceRate != null && ctx.acceptanceRate < settings.protectAcceptanceBelow
        if (!ctx.onTrip && !freeDecline && acceptanceLow) {
            // A decline costs acceptance rate: be less picky while it is low.
            eStar *= 0.85
            reasons += Reason.PROTECT_ACCEPTANCE
        }

        val hard = hardRule(offer, net, costPerKm, totalKm, pickupMin, tripKm, isLong, rKm, cash)
        reasons += hard

        val verdict = when {
            hard.isNotEmpty() -> Verdict.SKIP
            OfferTag.SCHEDULED in offer.tags -> Verdict.MAYBE
            e >= eStar * 1.15 -> Verdict.TAKE
            e >= eStar * 0.9 -> Verdict.MAYBE
            else -> Verdict.SKIP
        }
        if (OfferTag.SCHEDULED in offer.tags) reasons += Reason.SCHEDULED

        val recommendation = when {
            verdict == Verdict.TAKE -> Recommendation.ACCEPT
            ctx.onTrip -> Recommendation.IGNORE.also { reasons += Reason.ON_TRIP_IGNORE }
            verdict == Verdict.MAYBE -> Recommendation.CONSIDER
            else -> Recommendation.DECLINE
        }

        return Score(
            offerId = offer.id,
            net = net.round(2),
            totalKm = totalKm.round(1),
            totalMin = totalMin.round(0),
            rKm = rKm.round(2),
            rH = rH.round(1),
            e = e.round(3),
            eStar = eStar.round(3),
            verdict = verdict,
            recommendation = recommendation,
            reasons = reasons.distinct(),
            isNight = night,
            isLong = isLong,
            returnKm = returnKm,
            kmPerUnit = if (isLong && net > 0) (totalKm / net).round(2) else null,
            hardRule = hard.isNotEmpty(),
        )
    }

    private fun hardRule(
        offer: OrderOffer,
        net: Double,
        costPerKm: Double,
        totalKm: Double,
        pickupMin: Double,
        tripKm: Double,
        isLong: Boolean,
        rKm: Double,
        cash: CashPolicy,
    ): List<Reason> {
        val out = mutableListOf<Reason>()
        if (net < costPerKm * totalKm) out += Reason.BELOW_COST
        if (OfferTag.OUT_OF_RANGE in offer.tags && pickupMin > 20) out += Reason.OUT_OF_RANGE_FAR
        if (pickupMin > settings.maxPickupMin) out += Reason.PICKUP_TOO_LONG
        val pickupKm = offer.pickup?.km ?: 0.0
        if (offer.trip != null && tripKm < settings.shortTripKm && pickupKm > settings.maxPickupKmShort) {
            out += Reason.PICKUP_FOR_NOTHING
        }
        if (OfferTag.CASH in offer.tags && cash == CashPolicy.NEVER) out += Reason.CASH_REFUSED
        if (isLong && rKm < settings.effectiveLongTripMinRKm) out += Reason.LONG_TRIP_BELOW_FLOOR
        return out
    }
}

internal fun Double.round(digits: Int): Double {
    val f = 10.0.pow(digits)
    return kotlin.math.round(this * f) / f
}

/** Acklam's rational approximation of the standard normal quantile (|error| < 1.2e-9). */
internal fun inverseNormal(p: Double): Double {
    require(p > 0 && p < 1)
    val a = doubleArrayOf(-39.69683028665376, 220.9460984245205, -275.9285104469687, 138.3577518672690, -30.66479806614716, 2.506628277459239)
    val b = doubleArrayOf(-54.47609879822406, 161.5858368580409, -155.6989798598866, 66.80131188771972, -13.28068155288572)
    val c = doubleArrayOf(-0.007784894002430293, -0.3223964580411365, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783)
    val d = doubleArrayOf(0.007784695709041462, 0.3224671290700398, 2.445134137142996, 3.754408661907416)
    val pl = 0.02425
    return when {
        p < pl -> {
            val q = sqrt(-2 * ln(p))
            (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
        }
        p <= 1 - pl -> {
            val q = p - 0.5
            val r = q * q
            (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
        }
        else -> {
            val q = sqrt(-2 * ln(1 - p))
            -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
        }
    }.let { max(-8.0, min(8.0, it)) }
}
