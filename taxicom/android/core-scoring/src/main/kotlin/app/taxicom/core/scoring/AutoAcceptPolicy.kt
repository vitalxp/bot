package app.taxicom.core.scoring

import app.taxicom.core.model.OfferTag
import app.taxicom.core.model.OrderOffer

sealed interface AutoDecision {
    data class Accept(val vetoMs: Long) : AutoDecision
    data class Manual(val why: String) : AutoDecision
}

/** Auto-accept guard rails (order-capture-and-parsing.md, sections 4 and 6). */
class AutoAcceptPolicy(private val s: ScoringSettings) {
    private var consecutive = 0

    fun evaluate(offer: OrderOffer, score: Score, onTrip: Boolean, regionAutotapEnabled: Boolean = true): AutoDecision {
        if (s.decisionMode != DecisionMode.AUTO) return AutoDecision.Manual("advisor_mode")
        if (!regionAutotapEnabled) return AutoDecision.Manual("disabled_for_region")
        if (onTrip) return AutoDecision.Manual("on_trip")
        if (score.hardRule) return AutoDecision.Manual("hard_rule")
        val ratio = if (score.isNight) s.autoNightMinScoreRatio else s.autoMinScoreRatio
        if (score.e < score.eStar * ratio) return AutoDecision.Manual("score_below_auto")
        if ((offer.pickup?.minutes ?: Int.MAX_VALUE) > s.autoMaxPickupMin) return AutoDecision.Manual("pickup_long")
        if (offer.tags.any { it == OfferTag.OUT_OF_RANGE || it == OfferTag.SCHEDULED }) return AutoDecision.Manual("blocked_tag")
        val minRating = if (score.isNight) s.autoNightRequireRating else s.autoRequireRating
        offer.riderRating?.let { if (it < minRating) return AutoDecision.Manual("rating") }
        if (consecutive >= s.autoMaxConsecutive) return AutoDecision.Manual("consecutive_limit")
        return AutoDecision.Accept(s.autoVetoMs)
    }

    fun onAutoAccepted() { consecutive++ }

    fun onManualDecision() { consecutive = 0 }
}
