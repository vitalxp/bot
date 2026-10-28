package app.taxicom.core.scoring

import app.taxicom.core.model.OrderOffer
import app.taxicom.core.model.Verdict
import kotlin.math.roundToInt

/** Short spoken summaries: the driver hears the metric the decision was made on (architecture.md 4.4). */
object Phrases {
    private data class Words(
        val take: String, val maybe: String, val skip: String, val currency: String, val pickup: String,
        val trip: String, val perKm: String, val perHour: String, val night: String, val long: String,
        val back: String, val perKmLoop: String, val scheduled: String, val auto: String,
    )

    private val ru = Words("Брать", "На усмотрение", "Слабо", "злотых", "подача", "поездка", "за километр",
        "в час", "Ночь", "Длинная", "обратно", "за километр круга", "Предварительный", "Авто, принимаю")
    private val pl = Words("Bierz", "Do decyzji", "Słabe", "złotych", "dojazd", "kurs", "za kilometr",
        "na godzinę", "Noc", "Długi", "powrót", "za kilometr pętli", "Zaplanowany", "Auto, przyjmuję")
    private val en = Words("Take it", "Your call", "Weak", "zloty", "pickup", "trip", "per kilometre",
        "per hour", "Night", "Long", "back", "per loop kilometre", "Scheduled", "Auto, accepting")

    private fun words(locale: String) = when (locale.take(2)) {
        "ru" -> ru
        "pl" -> pl
        else -> en
    }

    private fun num(v: Double): String =
        if (v >= 10) v.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',')

    fun announce(offer: OrderOffer, score: Score, locale: String, brief: Boolean = false, auto: Boolean = false): String {
        val w = words(locale)
        val price = "${offer.platform.displayName}, ${offer.price.roundToInt()} ${w.currency}"
        val pickup = offer.pickup?.let { "${w.pickup} ${it.minutes}" }
        val verdict = when (score.verdict) {
            Verdict.TAKE -> w.take
            Verdict.MAYBE -> w.maybe
            Verdict.SKIP -> w.skip
        }
        if (offer.tags.contains(app.taxicom.core.model.OfferTag.SCHEDULED)) {
            return listOfNotNull(w.scheduled, price, pickup).joinToString(". ")
        }
        if (score.isLong) {
            return listOfNotNull(
                "${w.long}: ${offer.trip?.km?.roundToInt()} km, ${w.back} ${score.returnKm?.roundToInt()}",
                "${num(score.rKm)} ${w.perKmLoop}",
                verdict,
            ).joinToString(". ") + "."
        }
        val parts = mutableListOf<String>()
        if (score.isNight) parts += w.night
        parts += listOfNotNull(price, pickup).joinToString(", ")
        if (!brief) {
            when (score.verdict) {
                Verdict.MAYBE -> parts += "${num(score.rKm)} ${w.perKm}, ${score.rH.roundToInt()} ${w.perHour}"
                Verdict.SKIP -> parts += "${num(score.rKm)} ${w.perKm}"
                Verdict.TAKE -> offer.trip?.let { parts += "${w.trip} ${it.minutes}" }
            }
        }
        parts += if (auto) w.auto else verdict
        return parts.joinToString(". ") + "."
    }
}
