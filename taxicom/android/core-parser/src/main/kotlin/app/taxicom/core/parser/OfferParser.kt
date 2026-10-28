package app.taxicom.core.parser

import app.taxicom.core.model.OfferTag
import app.taxicom.core.model.OrderOffer
import app.taxicom.core.model.Platform
import app.taxicom.core.model.PriceBasis
import app.taxicom.core.model.Segment

sealed interface ParseResult {
    data class Parsed(val offer: OrderOffer, val unknownLines: List<String>) : ParseResult
    data class Unparsed(val reason: String, val lines: List<String>) : ParseResult
}

/**
 * Semantic, UI-language-independent parser of an order card (docs/order-capture-and-parsing.md, section 3).
 * Fields are found by the shape of the data, not by their position, so reordered cards still parse.
 */
class OfferParser(private val dict: Dictionaries = Dictionaries.DEFAULT) {

    fun parse(lines: List<String>, platform: Platform, capturedAtMs: Long): ParseResult {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val known = BooleanArray(clean.size)
        val langVotes = mutableMapOf<String, Int>()
        fun vote(lang: String) { langVotes[lang] = (langVotes[lang] ?: 0) + 1 }

        var price: Double? = null
        var currency: String? = null
        var bonus: Double? = null
        var ratePerKm: Double? = null
        val segments = mutableListOf<Pair<Int, Segment>>()
        var riderName: String? = null
        var riderRating: Double? = null
        val tags = mutableSetOf<OfferTag>()

        clean.forEachIndexed { i, line ->
            RATE.find(line)?.let {
                ratePerKm = it.groupValues[1].toDecimal()
                known[i] = true
                if (it.groupValues[3].first() in 'а'..'я') vote("ru")
                return@forEachIndexed
            }
            BONUS.find(line)?.let {
                bonus = it.groupValues[1].toDecimal()
                known[i] = true
                return@forEachIndexed
            }
            if (price == null) {
                PRICE.find(line)?.let {
                    price = it.groupValues[1].toDecimal()
                    currency = currencyCode(it.groupValues[2])
                    known[i] = true
                } ?: PRICE_PREFIX.find(line)?.let {
                    price = it.groupValues[2].toDecimal()
                    currency = currencyCode(it.groupValues[1])
                    known[i] = true
                }
                if (known[i]) return@forEachIndexed
            }
            SEGMENT.find(line)?.let {
                val hours = it.groupValues[1].toIntOrNull() ?: 0
                val minutes = hours * 60 + it.groupValues[2].toInt()
                val unit = it.groupValues[3]
                if (unit.first() in 'а'..'я') vote("ru")
                segments += i to Segment(minutes, it.groupValues[4].toDecimal(), null)
                known[i] = true
                return@forEachIndexed
            }
            RATING_WITH_NAME.matchEntire(line)?.let {
                val value = "${it.groupValues[2]}.${it.groupValues[3]}".toDouble()
                if (value in 1.0..5.0) {
                    riderName = it.groupValues[1].trim().ifEmpty { null }
                    riderRating = value
                    known[i] = true
                    return@forEachIndexed
                }
            }
            RATING_ALONE.matchEntire(line)?.let {
                val value = "${it.groupValues[1]}.${it.groupValues[2]}".toDouble()
                if (value in 1.0..5.0 && riderRating == null) {
                    riderRating = value
                    known[i] = true
                    return@forEachIndexed
                }
            }
            for ((tag, byLang) in dict.tags) {
                for ((lang, entries) in byLang) {
                    if (entries.any { matches(line, it) }) {
                        tags += tag
                        known[i] = true
                        vote(lang)
                    }
                }
            }
            if (known[i]) return@forEachIndexed
            for ((lang, entries) in dict.qualifiers) {
                if (entries.any { matches(line, it) }) { known[i] = true; vote(lang) }
            }
            for (buttons in listOf(dict.accept, dict.decline)) {
                for ((lang, entries) in buttons) {
                    if (entries.any { normalize(it) == normalize(line) }) { known[i] = true; vote(lang) }
                }
            }
        }

        // Product: first line of the card when it is a known product or a short label without digits.
        var product: String? = null
        clean.firstOrNull()?.let { first ->
            val isKnownProduct = dict.products.any { normalize(it) == normalize(first) }
            val looksLikeLabel = first.length <= 24 && first.none { it.isDigit() } && !known[0]
            if (isKnownProduct || looksLikeLabel) {
                product = first
                known[0] = true
                if (normalize(first).contains("priority")) tags += OfferTag.PRIORITY
            }
        }

        // Address of each segment: following unrecognised lines up to the next recognised one.
        val resolved = segments.mapIndexed { idx, (lineIdx, seg) ->
            val end = segments.getOrNull(idx + 1)?.first ?: clean.size
            val parts = mutableListOf<String>()
            for (j in lineIdx + 1 until end) {
                if (known[j]) break
                parts += clean[j]
                known[j] = true
                if (parts.size == 2) break
            }
            seg.copy(address = parts.joinToString(", ").ifEmpty { null })
        }

        val p = price ?: return ParseResult.Unparsed("no_price", clean)
        val pickup = resolved.getOrNull(0) ?: return ParseResult.Unparsed("no_pickup", clean)
        val trip = resolved.getOrNull(1)

        val uiLocale = langVotes.maxByOrNull { it.value }?.key
            ?: if (clean.any { l -> l.any { it in 'а'..'я' || it in 'А'..'Я' } }) "ru" else null

        val offer = OrderOffer(
            id = offerId(platform, p, pickup.address, trip?.address),
            platform = platform,
            product = product,
            capturedAtMs = capturedAtMs,
            currency = currency ?: "PLN",
            price = p,
            priceBasis = basisFor(platform),
            ratePerKm = ratePerKm,
            bonus = bonus,
            pickup = pickup,
            trip = trip,
            riderName = riderName,
            riderRating = riderRating,
            tags = tags,
            uiLocale = uiLocale,
            rawLines = clean,
        )
        return ParseResult.Parsed(offer, clean.filterIndexed { i, _ -> !known[i] })
    }

    fun isOrderCard(lines: List<String>): Boolean =
        lines.any { normalize(it) in dict.acceptLabels } && lines.any { PRICE.containsMatchIn(it) || PRICE_PREFIX.containsMatchIn(it) }

    companion object {
        private const val CUR = "zł|PLN|€|EUR|Kč|CZK|Ft|HUF|lei|RON|лв|BGN|kr|SEK|NOK|DKK|£|GBP|CHF"

        private val PRICE = Regex("(?<![+\\d,.])(\\d{1,4}[,.]\\d{2})\\s*($CUR)(?!\\s*/)")
        private val PRICE_PREFIX = Regex("(?<![+\\d])(€|£)\\s?(\\d{1,4}[,.]\\d{2})(?!\\s*/)")
        private val BONUS = Regex("^\\+\\s?(\\d{1,4}[,.]\\d{2})\\s*($CUR)?")
        private val RATE = Regex("(\\d{1,3}[,.]\\d{1,2})\\s*($CUR)?\\s*/\\s*(км|km)", RegexOption.IGNORE_CASE)
        private val SEGMENT = Regex(
            "(?:(\\d{1,2})\\s*(?:h|ч|godz\\.?)\\s*)?(\\d{1,3})\\s*(мин\\.?|min\\.?|хв|Min\\.?)\\W{0,3}\\(?(\\d{1,3}(?:[.,]\\d)?)\\s*(?:km|км)\\)?",
        )
        private val RATING_WITH_NAME = Regex("^(.{0,40}?)\\s*•\\s*(\\d)[.,](\\d{1,2})\\s*★?$")
        private val RATING_ALONE = Regex("^(\\d)[.,](\\d{1,2})\\s*★?$")

        /**
         * Poland 2025+: Uber shows the amount after its own commission (same basis as Bolt).
         * Free Now still treated as gross until we confirm on real cards. Server/region table can override.
         */
        fun basisFor(platform: Platform): PriceBasis = when (platform) {
            Platform.BOLT, Platform.UBER -> PriceBasis.NET_AFTER_COMMISSION
            Platform.FREENOW -> PriceBasis.GROSS_BEFORE_VARIABLE_FEE
        }

        fun offerId(platform: Platform, price: Double, from: String?, to: String?): String {
            val key = "${platform.name}|$price|${from.orEmpty()}|${to.orEmpty()}"
            val h = key.fold(1125899906842597L) { acc, c -> 31 * acc + c.code }
            return "${platform.name.lowercase()}-${java.lang.Long.toHexString(h)}"
        }

        private fun currencyCode(symbol: String): String = when (symbol) {
            "zł", "PLN" -> "PLN"
            "€", "EUR" -> "EUR"
            "Kč", "CZK" -> "CZK"
            "Ft", "HUF" -> "HUF"
            "lei", "RON" -> "RON"
            "лв", "BGN" -> "BGN"
            "£", "GBP" -> "GBP"
            "CHF" -> "CHF"
            "kr" -> "SEK"
            else -> symbol
        }

        private fun String.toDecimal(): Double = replace(',', '.').toDouble()
    }
}
