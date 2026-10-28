package app.taxicom.core.scoring

import app.taxicom.core.model.Outcome
import app.taxicom.core.model.Platform

data class AcceptanceAlert(val platform: Platform, val level: Double, val rate: Double)

/**
 * Estimated acceptance rate per platform over the last [window] countable offers (acceptance-rate.md).
 * Silently ignored offers during a trip and "no penalty" declines are not countable. When the platform
 * screen shows its own number, [observe] replaces the estimate as the anchor.
 */
class AcceptanceMonitor(
    private val levels: List<Double> = listOf(0.60, 0.55, 0.50),
    private val hysteresis: Double = 0.03,
    private val window: Int = 100,
) {
    private val history = mutableMapOf<Platform, ArrayDeque<Boolean>>()
    private val observed = mutableMapOf<Platform, Pair<Double, Int>>()
    private val armed = mutableMapOf<Platform, MutableSet<Double>>()

    fun rate(platform: Platform): Double? {
        val h = history[platform].orEmpty()
        observed[platform]?.let { (value, atSize) ->
            // Platform value covered the last `window` offers; newer offers push the oldest out.
            val since = h.toList().takeLast((h.size - atSize).coerceIn(0, window))
            return (value * (window - since.size) + since.count { it }) / window
        }
        if (h.size < 5) return null
        return h.count { it }.toDouble() / h.size
    }

    /** Anchors the estimate to the value read from the platform's own screen. */
    fun observe(platform: Platform, rate: Double) {
        observed[platform] = rate to history[platform].orEmpty().size
    }

    fun record(platform: Platform, outcome: Outcome, freeDecline: Boolean): AcceptanceAlert? {
        val countable = when (outcome) {
            Outcome.ACCEPTED, Outcome.COMPLETED -> true
            Outcome.DECLINED, Outcome.EXPIRED -> !freeDecline
            else -> false
        }
        if (!countable) return null
        val h = history.getOrPut(platform) { ArrayDeque() }
        h.addLast(outcome == Outcome.ACCEPTED || outcome == Outcome.COMPLETED)
        while (h.size > window) h.removeFirst()
        return check(platform)
    }

    private fun check(platform: Platform): AcceptanceAlert? {
        val r = rate(platform) ?: return null
        val fired = armed.getOrPut(platform) { mutableSetOf() }
        fired.removeAll { level -> r >= level + hysteresis }
        val crossed = levels.sortedDescending().filter { r < it && it !in fired }
        if (crossed.isEmpty()) return null
        fired += crossed
        return AcceptanceAlert(platform, crossed.min(), r)
    }
}
