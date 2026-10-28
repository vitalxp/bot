package app.taxicom.core.scoring

import app.taxicom.core.model.OfferTag
import app.taxicom.core.model.OrderOffer
import app.taxicom.core.model.Outcome
import app.taxicom.core.model.Platform
import app.taxicom.core.model.PriceBasis
import app.taxicom.core.model.Segment
import app.taxicom.core.model.Verdict
import app.taxicom.core.parser.OfferParser
import app.taxicom.core.parser.ParseResult
import app.taxicom.core.parser.SampleCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {
    private val engine = ScoringEngine(ScoringSettings())
    private val parser = OfferParser()
    private val day = ScoringContext(localMinuteOfDay = 14 * 60, offersPerHour = 6.0)

    private fun offer(card: SampleCards.Card) = (parser.parse(card.lines, card.platform, 0) as ParseResult.Parsed).offer

    private fun synthetic(price: Double, pickup: Segment, trip: Segment, platform: Platform = Platform.BOLT, tags: Set<OfferTag> = emptySet()) =
        OrderOffer("x", platform, "Bolt", 0, "PLN", price, PriceBasis.NET_AFTER_COMMISSION, pickup = pickup, trip = trip, tags = tags)

    @Test fun worksExamplesFromArchitecture() {
        // Poland 2025+: Uber card is already after Uber's commission (22%+VAT≈27.06%).
        // Medians 2.6 zł/km and 48 zł/h. Exclusive tag adds ×1.03.
        val priority = engine.score(offer(SampleCards.UBER_RU_001), day)
        assertEquals(26.46, priority.net, 0.01)
        assertEquals(3.35, priority.rKm, 0.01) // matches the "≈3,35 PLN/км" on the card
        assertEquals(1.54, priority.e, 0.02)
        assertEquals(Verdict.TAKE, priority.verdict)

        val uberX = engine.score(offer(SampleCards.UBER_PL_001), day)
        assertEquals(9.54, uberX.net, 0.01)
        assertEquals(0.84, uberX.e, 0.02)
        assertEquals(Verdict.SKIP, uberX.verdict)

        val boltFar = engine.score(offer(SampleCards.BOLT_RU_001), day)
        assertTrue(boltFar.hardRule)
        assertTrue(Reason.BELOW_COST in boltFar.reasons)
        assertTrue(Reason.OUT_OF_RANGE_FAR in boltFar.reasons)

        val boltPl = engine.score(offer(SampleCards.BOLT_PL_001), day)
        assertEquals(0.41, boltPl.e, 0.02)
        assertEquals(Verdict.SKIP, boltPl.verdict)
        assertTrue(Reason.PICKUP_FOR_NOTHING in boltPl.reasons)
    }

    @Test fun thresholdFollowsOfferFlow() {
        val quiet = engine.threshold(engine.demandQuantile(day.copy(offersPerHour = 1.0), false), emptyList())
        val busy = engine.threshold(engine.demandQuantile(day.copy(offersPerHour = 12.0), false), emptyList())
        assertTrue("quiet=$quiet busy=$busy", quiet < 0.85 && busy > 1.15)
        val idle = engine.demandQuantile(day.copy(offersPerHour = 12.0, idleMinutes = 35), false)
        assertEquals(0.40, idle, 1e-9)
        assertEquals(0.45, engine.demandQuantile(day.copy(offersPerHour = 12.0), night = true), 1e-9)
    }

    @Test fun thresholdUsesHistoryWhenAvailable() {
        val history = (1..100).map { it / 100.0 }
        assertEquals(0.505, engine.threshold(0.5, history), 1e-6)
    }

    @Test fun goodShortOrderIsTake() {
        val s = engine.score(synthetic(32.0, Segment(3, 1.2, "A"), Segment(14, 7.5, "B")), day)
        assertEquals(Verdict.TAKE, s.verdict)
        assertEquals(Recommendation.ACCEPT, s.recommendation)
    }

    @Test fun duringTripWeakOrdersAreIgnoredNotDeclined() {
        val s = engine.score(offer(SampleCards.UBER_PL_001), day.copy(onTrip = true))
        assertEquals(Recommendation.IGNORE, s.recommendation)
        assertTrue(Reason.ON_TRIP_IGNORE in s.reasons)
    }

    @Test fun longTripCountsTheWayBack() {
        val cheap = engine.score(synthetic(40.0, Segment(4, 2.0, "A"), Segment(35, 40.0, "Far")), day)
        assertTrue(cheap.isLong)
        assertEquals(82.0, cheap.totalKm, 0.01)
        assertEquals(Verdict.SKIP, cheap.verdict)
        assertTrue(Reason.LONG_TRIP_BELOW_FLOOR in cheap.reasons)

        val fair = engine.score(synthetic(190.0, Segment(4, 2.0, "A"), Segment(35, 40.0, "Far")), day)
        assertEquals(2.32, fair.rKm, 0.01)
        assertEquals(0.43, fair.kmPerUnit!!, 0.01)
        assertTrue(!fair.hardRule)

        val custom = ScoringEngine(ScoringSettings(longTripKm = 50.0))
        assertTrue(!custom.score(synthetic(40.0, Segment(4, 2.0, "A"), Segment(35, 40.0, "Far")), day).isLong)
    }

    @Test fun nightUsesNightReferenceAndRules() {
        val night = day.copy(localMinuteOfDay = 23 * 60 + 30)
        assertTrue(engine.isNight(23 * 60 + 30) && engine.isNight(3 * 60) && !engine.isNight(7 * 60))
        val o = synthetic(30.0, Segment(3, 1.2, "A"), Segment(14, 7.5, "B")).copy(riderRating = 4.6)
        val s = engine.score(o, night)
        assertTrue(s.isNight)
        assertTrue(Reason.LOW_RATING in s.reasons) // 4.6 is fine by day, penalised at night (< 4.7)
        assertTrue(Reason.LOW_RATING !in engine.score(o, day).reasons)
    }

    @Test fun lowAcceptanceSoftensThreshold() {
        val o = offer(SampleCards.UBER_RU_001)
        val normal = engine.score(o, day)
        val protect = engine.score(o, day.copy(acceptanceRate = 0.52))
        assertTrue(protect.eStar < normal.eStar)
        assertTrue(Reason.PROTECT_ACCEPTANCE in protect.reasons)
    }

    @Test fun bonusPlusAndDestinationAddValue() {
        val settings = ScoringSettings(bonusPlusPerTrip = 6.0)
        val e = ScoringEngine(settings)
        val o = offer(SampleCards.UBER_PL_001)
        val base = e.score(o, day)
        val boosted = e.score(o, day.copy(bonusPlusActive = true, destinationBonus = 3.0))
        assertEquals(base.net + 9.0, boosted.net, 0.01)
        assertTrue(Reason.BONUS_PLUS in boosted.reasons && Reason.DESTINATION_HOT in boosted.reasons)
    }

    @Test fun cashNeverIsHardRule() {
        val e = ScoringEngine(ScoringSettings(cashPolicy = CashPolicy.NEVER))
        val o = synthetic(32.0, Segment(3, 1.2, "A"), Segment(14, 7.5, "B"), tags = setOf(OfferTag.CASH))
        assertTrue(Reason.CASH_REFUSED in e.score(o, day).reasons)
    }

    @Test fun uberFeeEstimateCanBeLearned() {
        val o = offer(SampleCards.UBER_RU_001)
        // Default PL: card is net — fee estimate is not applied again.
        assertEquals(26.46, engine.net(o, learnedUberFee = 0.2), 1e-9)
        val legacy = ScoringEngine(ScoringSettings(uberPriceIsNet = false, uberFeePct = 22.0, uberFeeVat = true))
        assertEquals(26.46 * (1 - 0.22 * 1.23), legacy.net(o), 0.01)
        assertEquals(26.46 * 0.8, legacy.net(o, learnedUberFee = 0.2), 1e-9)
    }

    @Test fun autoAcceptRespectsGuardRails() {
        val auto = ScoringSettings(decisionMode = DecisionMode.AUTO, autoMaxConsecutive = 2)
        val engine = ScoringEngine(auto)
        val policy = AutoAcceptPolicy(auto)
        val good = synthetic(40.0, Segment(3, 1.2, "A"), Segment(14, 7.5, "B")).copy(riderRating = 4.9)
        val score = engine.score(good, day)
        assertTrue(policy.evaluate(good, score, onTrip = false) is AutoDecision.Accept)
        assertTrue(policy.evaluate(good, score, onTrip = true) is AutoDecision.Manual)
        policy.onAutoAccepted(); policy.onAutoAccepted()
        assertEquals(AutoDecision.Manual("consecutive_limit"), policy.evaluate(good, score, onTrip = false))
        policy.onManualDecision()
        val scheduled = good.copy(tags = setOf(OfferTag.SCHEDULED))
        assertEquals(AutoDecision.Manual("blocked_tag"), policy.evaluate(scheduled, engine.score(scheduled, day), false))
    }

    @Test fun acceptanceAlertsWithHysteresis() {
        val m = AcceptanceMonitor()
        assertNull(m.rate(Platform.UBER))
        m.observe(Platform.UBER, 0.62)
        val alerts = mutableListOf<AcceptanceAlert>()
        repeat(5) { m.record(Platform.UBER, Outcome.DECLINED, freeDecline = false)?.let(alerts::add) }
        assertEquals(0.60, alerts.single().level, 1e-9)
        // Ignored-on-trip and free declines don't move the rate.
        val before = m.rate(Platform.UBER)
        m.record(Platform.UBER, Outcome.IGNORED, false)
        m.record(Platform.UBER, Outcome.DECLINED, freeDecline = true)
        assertEquals(before!!, m.rate(Platform.UBER)!!, 1e-9)
        repeat(20) { m.record(Platform.UBER, Outcome.DECLINED, false)?.let(alerts::add) }
        assertEquals(listOf(0.60, 0.55, 0.50), alerts.map { it.level })
    }

    @Test fun phrasesAreShortAndLocalised() {
        val o = offer(SampleCards.UBER_RU_001)
        val s = engine.score(o, day)
        assertEquals("Uber, 26 злотых, подача 2. поездка 18. Брать.", Phrases.announce(o, s, "ru"))
        val good = synthetic(32.0, Segment(3, 1.2, "A"), Segment(14, 7.5, "B"))
        assertEquals("Bolt, 32 złotych, dojazd 3. kurs 14. Bierz.", Phrases.announce(good, engine.score(good, day), "pl"))
    }
}
