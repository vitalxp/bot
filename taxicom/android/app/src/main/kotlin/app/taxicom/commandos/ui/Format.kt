package app.taxicom.commandos.ui

import app.taxicom.commandos.R
import app.taxicom.core.scoring.Reason
import java.util.Locale
import kotlin.math.roundToInt

fun money(amount: Double, currency: String = "PLN"): String {
    val n = if (amount >= 100) amount.roundToInt().toString()
    else String.format(Locale.ROOT, "%.2f", amount).trimEnd('0').trimEnd('.')
    return when (currency) {
        "PLN" -> "$n zł"
        "EUR" -> "€$n"
        else -> "$n $currency"
    }
}

fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

fun relative(v: Double): String {
    val p = (v * 100).roundToInt()
    return if (p >= 0) "+$p %" else "$p %"
}

fun reasonRes(r: Reason): Int = when (r) {
    Reason.BELOW_COST -> R.string.reason_below_cost
    Reason.OUT_OF_RANGE_FAR -> R.string.reason_out_of_range_far
    Reason.PICKUP_TOO_LONG -> R.string.reason_pickup_too_long
    Reason.PICKUP_FOR_NOTHING -> R.string.reason_pickup_for_nothing
    Reason.CASH_REFUSED -> R.string.reason_cash_refused
    Reason.LONG_TRIP_BELOW_FLOOR -> R.string.reason_long_floor
    Reason.SCHEDULED -> R.string.reason_scheduled
    Reason.LOW_RATING -> R.string.reason_low_rating
    Reason.NIGHT -> R.string.reason_night
    Reason.LONG_TRIP -> R.string.reason_long
    Reason.EXCLUSIVE -> R.string.reason_exclusive
    Reason.ON_TRIP_IGNORE -> R.string.reason_on_trip
    Reason.PROTECT_ACCEPTANCE -> R.string.reason_protect
    Reason.BONUS_PLUS -> R.string.reason_bonus
    Reason.DESTINATION_HOT -> R.string.reason_dest_hot
    Reason.DESTINATION_DEAD -> R.string.reason_dest_dead
    Reason.FREE_DECLINE -> R.string.reason_free_decline
}
