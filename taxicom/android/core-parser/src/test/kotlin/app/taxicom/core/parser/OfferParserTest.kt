package app.taxicom.core.parser

import app.taxicom.core.model.OfferTag.CASH
import app.taxicom.core.model.OfferTag.EXCLUSIVE
import app.taxicom.core.model.OfferTag.NO_PENALTY_DECLINE
import app.taxicom.core.model.OfferTag.OUT_OF_RANGE
import app.taxicom.core.model.OfferTag.PRIORITY
import app.taxicom.core.model.OfferTag.SCHEDULED
import app.taxicom.core.model.Platform
import app.taxicom.core.model.PriceBasis
import app.taxicom.core.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {
    private val parser = OfferParser()

    private fun parse(card: SampleCards.Card): ParseResult.Parsed {
        val r = parser.parse(card.lines, card.platform, 0)
        assertTrue("${card.id}: $r", r is ParseResult.Parsed)
        return r as ParseResult.Parsed
    }

    @Test fun boltRu() {
        val r = parse(SampleCards.BOLT_RU_001)
        val o = r.offer
        assertEquals("Bolt", o.product)
        assertEquals(16.70, o.price, 1e-9)
        assertEquals("PLN", o.currency)
        assertEquals(PriceBasis.NET_AFTER_COMMISSION, o.priceBasis)
        assertEquals(setOf(CASH, OUT_OF_RANGE, SCHEDULED, NO_PENALTY_DECLINE), o.tags)
        assertEquals("Malwina", o.riderName)
        assertEquals(5.0, o.riderRating!!, 1e-9)
        assertEquals(Segment(45, 45.7, "Castorama, Grunwaldzka 5, Rumia"), o.pickup)
        assertEquals(Segment(10, 6.1, "Nowodworskiego 48A, Gdynia 81-025"), o.trip)
        assertEquals("ru", o.uiLocale)
        assertEquals(emptyList<String>(), r.unknownLines)
    }

    @Test fun uberPl() {
        val r = parse(SampleCards.UBER_PL_001)
        val o = r.offer
        assertEquals("UberX", o.product)
        assertEquals(9.54, o.price, 1e-9)
        assertEquals(PriceBasis.GROSS_BEFORE_VARIABLE_FEE, o.priceBasis)
        assertEquals(setOf(EXCLUSIVE), o.tags)
        assertEquals(4.95, o.riderRating!!, 1e-9)
        assertEquals(Segment(4, 1.6, "ulica Marcina Dragana 12, Gdańsk"), o.pickup)
        assertEquals(Segment(7, 5.3, "ulica płk. Jana Pałubickiego 6, Gdańsk"), o.trip)
        assertEquals("pl", o.uiLocale)
        assertEquals(emptyList<String>(), r.unknownLines)
    }

    @Test fun uberRu() {
        val o = parse(SampleCards.UBER_RU_001).offer
        assertEquals("UberX Priority", o.product)
        assertEquals(26.46, o.price, 1e-9)
        assertEquals(3.35, o.ratePerKm!!, 1e-9)
        assertEquals(4.59, o.bonus!!, 1e-9)
        assertEquals(setOf(EXCLUSIVE, PRIORITY), o.tags)
        assertEquals(4.96, o.riderRating!!, 1e-9)
        assertEquals(Segment(2, 1.3, "ulica Romana Wyrobka 1, Гданьск"), o.pickup)
        assertEquals(Segment(18, 6.6, "ulica Konna 40, Kolbudy"), o.trip)
        assertEquals("ru", o.uiLocale)
    }

    @Test fun boltPl() {
        val o = parse(SampleCards.BOLT_PL_001).offer
        assertEquals(8.93, o.price, 1e-9)
        assertEquals(setOf(OUT_OF_RANGE, NO_PENALTY_DECLINE), o.tags)
        assertEquals("Paweł", o.riderName)
        assertEquals(4.9, o.riderRating!!, 1e-9)
        assertEquals(Segment(16, 11.5, "Tadeusza Kościuszki 11a, Sopot 81-704"), o.pickup)
        assertEquals(4, o.trip!!.minutes)
        assertEquals("pl", o.uiLocale)
    }

    @Test fun reorderedLinesStillParse() {
        val shuffled = SampleCards.UBER_PL_001.lines.let { l -> listOf(l[0], l[3], l[2], l[1]) + l.drop(4) }
        val o = (parser.parse(shuffled, Platform.UBER, 0) as ParseResult.Parsed).offer
        assertEquals(9.54, o.price, 1e-9)
        assertEquals(4.95, o.riderRating!!, 1e-9)
        assertEquals(setOf(EXCLUSIVE), o.tags)
    }

    @Test fun englishCardWithHoursAndEuro() {
        val lines = listOf(
            "Bolt", "€42.80", "Cash payment", "Anna • 4.8 ★", "6 min • 3.2 km", "Main Street 1, Riga",
            "1 h 5 min • 61 km", "Airport, Riga", "Accept",
        )
        val o = (parser.parse(lines, Platform.BOLT, 0) as ParseResult.Parsed).offer
        assertEquals(42.80, o.price, 1e-9)
        assertEquals("EUR", o.currency)
        assertEquals(Segment(65, 61.0, "Airport, Riga"), o.trip)
        assertEquals(setOf(CASH), o.tags)
        assertEquals("en", o.uiLocale)
    }

    @Test fun missingPriceIsUnparsed() {
        val r = parser.parse(listOf("Bolt", "6 min • 3.2 km", "Accept"), Platform.BOLT, 0)
        assertTrue(r is ParseResult.Unparsed)
        assertEquals("no_price", (r as ParseResult.Unparsed).reason)
    }

    @Test fun onlyPickupKnown() {
        val r = parser.parse(listOf("UberX", "12,00 zł", "3 min (1.0 km)", "Rynek 1", "Akceptuj"), Platform.UBER, 0)
        assertNull((r as ParseResult.Parsed).offer.trip)
    }

    @Test fun cardDetectionAndStableId() {
        assertTrue(SampleCards.ALL.all { parser.isOrderCard(it.lines) })
        assertTrue(!parser.isOrderCard(listOf("Twoje zarobki", "120,00 zł")))
        val a = parse(SampleCards.UBER_RU_001).offer.id
        val b = parse(SampleCards.UBER_RU_001).offer.id
        assertEquals(a, b)
    }
}
