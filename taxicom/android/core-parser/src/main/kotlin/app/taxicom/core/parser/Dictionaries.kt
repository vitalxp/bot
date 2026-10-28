package app.taxicom.core.parser

import app.taxicom.core.model.OfferTag

/**
 * Multilingual vocabulary. Ships with the app and is replaced by the server-delivered version
 * when available; every version must pass all fixtures in `src/test/resources/cards`.
 */
data class Dictionaries(
    val tags: Map<OfferTag, Map<String, List<String>>>,
    val qualifiers: Map<String, List<String>>,
    val accept: Map<String, List<String>>,
    val decline: Map<String, List<String>>,
    val products: List<String>,
) {
    val acceptLabels: Set<String> by lazy { accept.values.flatten().map(::normalize).toSet() }
    val declineLabels: Set<String> by lazy { decline.values.flatten().map(::normalize).toSet() }

    companion object {
        val DEFAULT = Dictionaries(
            tags = mapOf(
                OfferTag.CASH to mapOf(
                    "ru" to listOf("Оплата наличными", "Наличные"),
                    "pl" to listOf("Płatność gotówką", "Gotówka"),
                    "en" to listOf("Cash payment", "Cash"),
                ),
                OfferTag.OUT_OF_RANGE to mapOf(
                    "ru" to listOf("Вне радиуса"),
                    "pl" to listOf("Poza zasięgiem"),
                    "en" to listOf("Out of range", "Outside your area"),
                ),
                OfferTag.SCHEDULED to mapOf(
                    "ru" to listOf("Срок", "Запланирован", "Предварительный"),
                    "pl" to listOf("Zaplanowany", "Zaplanowane", "Rezerwacja"),
                    "en" to listOf("Scheduled", "Reservation"),
                ),
                OfferTag.EXCLUSIVE to mapOf(
                    "ru" to listOf("Эксклюзивный", "Только для вас"),
                    "pl" to listOf("Tylko dla Ciebie", "Na wyłączność"),
                    "en" to listOf("Exclusive", "Only for you"),
                ),
                OfferTag.PRIORITY to mapOf(
                    "ru" to listOf("Приоритетный"),
                    "pl" to listOf("Priorytet"),
                    "en" to listOf("Priority"),
                ),
                OfferTag.NO_PENALTY_DECLINE to mapOf(
                    "ru" to listOf("Отказ не повлияет"),
                    "pl" to listOf("Odrzucenie nie wpłynie"),
                    "en" to listOf("Declining won't affect", "Declining will not affect"),
                ),
                OfferTag.LONG_TRIP to mapOf(
                    "ru" to listOf("Длинная поездка"),
                    "pl" to listOf("Długi przejazd", "Długa trasa"),
                    "en" to listOf("Long trip"),
                ),
            ),
            qualifiers = mapOf(
                "ru" to listOf("После вычета комиссии", "С вычетом сервисного сбора"),
                "pl" to listOf("netto z podatkiem", "Kwota nie zawiera prowizji"),
                "en" to listOf("After commission", "Excludes service fee", "Includes taxes"),
            ),
            accept = mapOf(
                "ru" to listOf("Принять"),
                "pl" to listOf("Akceptuj", "Akceptuję", "Przyjmij"),
                "en" to listOf("Accept"),
                "uk" to listOf("Прийняти"),
                "de" to listOf("Annehmen", "Akzeptieren"),
                "fr" to listOf("Accepter"),
                "es" to listOf("Aceptar"),
                "it" to listOf("Accetta"),
            ),
            decline = mapOf(
                "ru" to listOf("Отклонить", "Закрыть"),
                "pl" to listOf("Odrzuć", "Zamknij"),
                "en" to listOf("Decline", "Close"),
                "uk" to listOf("Відхилити"),
                "de" to listOf("Ablehnen"),
                "fr" to listOf("Refuser"),
                "es" to listOf("Rechazar"),
                "it" to listOf("Rifiuta"),
            ),
            products = listOf(
                "Bolt", "Bolt XL", "Bolt Comfort", "Comfort", "Green", "Bolt Green", "Premium", "Bolt Pet",
                "UberX", "UberX Priority", "Uber Comfort", "Comfort Electric", "UberXL", "Uber Black", "Uber Green",
                "Uber Pet", "FREENOW", "Taxi", "Ride", "Lite", "Exec",
            ),
        )
    }
}

private val PUNCT = Regex("[\\p{Punct}…«»„”“’‘•★]+")
private val SPACES = Regex("\\s+")

fun normalize(s: String): String =
    s.lowercase().replace('ё', 'е').replace(PUNCT, " ").replace(SPACES, " ").trim()

/**
 * Prefix match in both directions: cards truncate long labels with "…", so a line may be a prefix
 * of the dictionary entry, and an entry may be the prefix of a longer line. Short entries (< 6 chars)
 * must match exactly to avoid false positives like "Срок" inside an address.
 */
internal fun matches(line: String, entry: String): Boolean {
    val l = normalize(line)
    val e = normalize(entry)
    if (l.isEmpty() || e.isEmpty()) return false
    if (e.length < 6 || l.length < 6) return l == e
    return l.startsWith(e) || e.startsWith(l)
}
