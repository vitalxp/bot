package app.taxicom.core.scoring

import kotlinx.serialization.Serializable

@Serializable
enum class CashPolicy { NEUTRAL, PENALTY_10, NEVER }

@Serializable
enum class ScheduledPolicy { ANNOUNCE, SILENT, IGNORE }

@Serializable
enum class DecisionMode { ADVISOR, AUTO }

/**
 * Every number that influences a decision and that the driver may change (architecture.md 4.7).
 * Defaults make the app usable with zero configuration.
 */
@Serializable
data class ScoringSettings(
    // Order evaluation
    val longTripKm: Double = 25.0,
    val returnWeightAuto: Boolean = true,
    val returnWeightFixed: Double = 1.0,
    val longTripMinRKm: Double? = null,
    val costPerKm: Double = 0.55,
    val alpha: Double = 0.4,
    val maxPickupMin: Int = 20,
    val maxPickupKmShort: Double = 8.0,
    val shortTripKm: Double = 4.0,
    val cashPolicy: CashPolicy = CashPolicy.NEUTRAL,
    val minRiderRating: Double = 4.5,
    val scheduledPolicy: ScheduledPolicy = ScheduledPolicy.ANNOUNCE,

    // Night
    val nightStartMin: Int = 22 * 60,
    val nightEndMin: Int = 6 * 60,
    val nightPMax: Double = 0.45,
    val nightCostFactor: Double = 1.0,
    val nightLongTripKm: Double? = null,
    val nightCashPolicy: CashPolicy? = null,
    val nightMinRiderRating: Double = 4.7,
    val nightBriefTts: Boolean = true,

    // Shift
    val idleSoftenMin: Int = 15,
    val breakReminderMin: Int = 240,

    // Platforms and fees
    val uberFeePct: Double = 25.0,
    val uberFeeVat: Boolean = true,
    val freenowFeePct: Double = 20.0,
    val fleetFeePct: Double = 0.0,
    val boltPriceIsNet: Boolean = true,

    // Decisions and auto-accept
    val decisionMode: DecisionMode = DecisionMode.ADVISOR,
    val autoMinScoreRatio: Double = 1.1,
    val autoNightMinScoreRatio: Double = 1.25,
    val autoVetoMs: Long = 2000,
    val autoMaxPickupMin: Int = 8,
    val autoMaxConsecutive: Int = 3,
    val autoRequireRating: Double = 4.6,
    val autoNightRequireRating: Double = 4.8,

    // Acceptance-rate protection
    val acceptanceAlertLevels: List<Double> = listOf(0.60, 0.55, 0.50),
    val acceptanceHysteresis: Double = 0.03,
    val protectAcceptanceBelow: Double = 0.55,

    // Bonus+ mode: extra income per trip promised by an active promotion
    val bonusPlusPerTrip: Double = 0.0,
) {
    val effectiveLongTripMinRKm: Double get() = longTripMinRKm ?: (costPerKm * 1.6)

    /** Uber fee estimate as a fraction: 25 % + 23 % VAT on the fee ≈ 30.75 %. */
    val uberFeeFraction: Double get() = uberFeePct / 100.0 * (if (uberFeeVat) 1.23 else 1.0)
}
