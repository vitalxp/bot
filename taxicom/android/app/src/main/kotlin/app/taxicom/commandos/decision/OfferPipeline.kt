package app.taxicom.commandos.decision

import app.taxicom.commandos.data.AppPrefs
import app.taxicom.commandos.data.BusClient
import app.taxicom.commandos.data.OfferDao
import app.taxicom.commandos.data.OfferEntity
import app.taxicom.core.model.DeviceRole
import app.taxicom.core.model.OfferTag
import app.taxicom.core.model.OrderOffer
import app.taxicom.core.model.Outcome
import app.taxicom.core.model.Platform
import app.taxicom.core.parser.OfferParser
import app.taxicom.core.parser.ParseResult
import app.taxicom.core.parser.SampleCards
import app.taxicom.core.scoring.AcceptanceAlert
import app.taxicom.core.scoring.AcceptanceMonitor
import app.taxicom.core.scoring.AutoAcceptPolicy
import app.taxicom.core.scoring.AutoDecision
import app.taxicom.core.scoring.Phrases
import app.taxicom.core.scoring.Score
import app.taxicom.core.scoring.ScoringContext
import app.taxicom.core.scoring.ScoringEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

enum class Action { ACCEPT, DECLINE, IGNORE }

enum class DecidedBy { DRIVER, WHEEL, AUTO, REMOTE }

/** Performs taps inside the driver app. Implemented by the Accessibility service in the full edition. */
fun interface TapPerformer {
    fun tap(platform: Platform, action: Action): Boolean
}

data class LiveOffer(
    val offer: OrderOffer,
    val score: Score,
    val receivedAtMs: Long,
    /** Device id of the reader that captured it, null for this device. */
    val sourceDevice: String? = null,
    val autoDeadlineMs: Long? = null,
    val demo: Boolean = false,
    val onTripAtOffer: Boolean = false,
)

data class ShiftState(
    val active: Boolean = false,
    val startedAtMs: Long? = null,
    val onTripUntilMs: Long = 0,
    val manualOnTrip: Boolean? = null,
) {
    fun onTrip(now: Long): Boolean = manualOnTrip ?: (now < onTripUntilMs)
}

class OfferPipeline(
    private val scope: CoroutineScope,
    private val dao: OfferDao,
    private val bus: BusClient,
    private val voice: (String) -> Unit,
    private val locale: () -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val parser = OfferParser()
    private var prefs = AppPrefs()
    private var engine = ScoringEngine(prefs.scoring)
    private var autoPolicy = AutoAcceptPolicy(prefs.scoring)
    private val acceptance = AcceptanceMonitor()
    private val offerTimes = ArrayDeque<Long>()
    private var recentScores: List<Double> = emptyList()
    private var autoJob: Job? = null
    private var lastSpoken: String? = null
    private var lastActivityMs = clock()

    var tapPerformer: TapPerformer? = null

    private val _live = MutableStateFlow<LiveOffer?>(null)
    val live: StateFlow<LiveOffer?> = _live.asStateFlow()
    private val _shift = MutableStateFlow(ShiftState())
    val shift: StateFlow<ShiftState> = _shift.asStateFlow()
    private val _acceptance = MutableStateFlow<Map<Platform, Double?>>(emptyMap())
    val acceptanceRates: StateFlow<Map<Platform, Double?>> = _acceptance.asStateFlow()
    private val _alerts = MutableSharedFlow<AcceptanceAlert>(extraBufferCapacity = 8)
    val alerts: SharedFlow<AcceptanceAlert> = _alerts
    private val _offersPerHour = MutableStateFlow(0.0)
    val offersPerHour: StateFlow<Double> = _offersPerHour.asStateFlow()
    private val _outcomes = MutableSharedFlow<Pair<String, Outcome>>(extraBufferCapacity = 16)
    val outcomes: SharedFlow<Pair<String, Outcome>> = _outcomes

    fun updatePrefs(p: AppPrefs) {
        if (p.scoring != prefs.scoring) {
            engine = ScoringEngine(p.scoring)
            autoPolicy = AutoAcceptPolicy(p.scoring)
        }
        prefs = p
    }

    fun startShift() {
        _shift.value = ShiftState(active = true, startedAtMs = clock())
        lastActivityMs = clock()
        scope.launch { recentScores = dao.recentScores(clock() - 7 * DAY_MS, night = false) }
    }

    fun endShift() {
        autoJob?.cancel()
        _live.value = null
        _shift.value = ShiftState()
    }

    fun setManualOnTrip(onTrip: Boolean?) = _shift.update { it.copy(manualOnTrip = onTrip) }

    /** Called by the reader for every visible order card (already de-duplicated by text hash upstream). */
    fun onCard(platform: Platform, lines: List<String>) {
        val parsed = parser.parse(lines, platform, clock()) as? ParseResult.Parsed ?: return
        if (_live.value?.offer?.id == parsed.offer.id) return
        handleNewOffer(parsed.offer, sourceDevice = null, demo = false)
    }

    fun onCardGone(platform: Platform) {
        val current = _live.value ?: return
        if (current.offer.platform != platform || current.sourceDevice != null || current.demo) return
        finish(current, if (_shift.value.onTrip(clock())) Outcome.IGNORED else Outcome.EXPIRED, null)
    }

    fun injectSample(index: Int) {
        val card = SampleCards.ALL[index % SampleCards.ALL.size]
        val parsed = parser.parse(card.lines, card.platform, clock()) as? ParseResult.Parsed ?: return
        val unique = parsed.offer.copy(id = parsed.offer.id + "-" + clock().toString(36))
        handleNewOffer(unique, sourceDevice = null, demo = true)
    }

    fun onBusMessage(msg: JsonObject) {
        when (msg["type"]?.jsonPrimitive?.contentOrNull) {
            "offer" -> if (prefs.role != DeviceRole.CAPTURE) {
                val offer = runCatching {
                    kotlinx.serialization.json.Json.decodeFromJsonElement(OrderOffer.serializer(), msg["offer"]!!)
                }.getOrNull() ?: return
                handleNewOffer(offer, sourceDevice = msg["from"]?.jsonPrimitive?.contentOrNull, demo = false)
            }
            "offer_gone" -> {
                val id = msg["offer_id"]?.jsonPrimitive?.contentOrNull
                val current = _live.value
                if (current != null && current.offer.id == id) {
                    val outcome = msg["outcome"]?.jsonPrimitive?.contentOrNull?.let { Outcome.valueOf(it) } ?: Outcome.EXPIRED
                    finish(current, outcome, null, fromRemote = true)
                }
            }
            "decision" -> {
                val id = msg["offer_id"]?.jsonPrimitive?.contentOrNull
                val action = msg["action"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Action.valueOf(it) }.getOrNull() }
                if (action != null && _live.value?.offer?.id == id) decide(action, DecidedBy.REMOTE)
            }
            "trip_state" -> msg["on_trip"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { setManualOnTrip(if (it) true else null) }
        }
    }

    private fun handleNewOffer(offer: OrderOffer, sourceDevice: String?, demo: Boolean) {
        val now = clock()
        _live.value?.let { if (!it.demo) finish(it, Outcome.EXPIRED, null) }
        offerTimes.addLast(now)
        while (offerTimes.isNotEmpty() && offerTimes.first() < now - HOUR_MS) offerTimes.removeFirst()
        val shiftMinutes = ((now - (_shift.value.startedAtMs ?: now)) / 60_000.0).coerceAtLeast(1.0)
        _offersPerHour.value = offerTimes.size / minOf(60.0, shiftMinutes) * 60.0

        val local = Instant.ofEpochMilli(now).atZone(zone())
        val onTrip = _shift.value.onTrip(now)
        val ctx = ScoringContext(
            localMinuteOfDay = local.hour * 60 + local.minute,
            offersPerHour = _offersPerHour.value,
            idleMinutes = if (onTrip) 0 else ((now - lastActivityMs) / 60_000).toInt(),
            onTrip = onTrip,
            acceptanceRate = acceptance.rate(offer.platform),
            recentScores = recentScores,
        )
        val score = engine.score(offer, ctx)
        val auto = if (sourceDevice == null || prefs.role != DeviceRole.CAPTURE) autoPolicy.evaluate(offer, score, onTrip) else null
        val deadline = (auto as? AutoDecision.Accept)?.let { now + it.vetoMs }
        val live = LiveOffer(offer, score, now, sourceDevice, deadline, demo, onTrip)
        _live.value = live

        if (!demo) scope.launch { dao.upsert(entity(live, Outcome.PENDING, null)) }

        val speaksHere = prefs.role != DeviceRole.VIEWER &&
            (prefs.role != DeviceRole.CAPTURE || !bus.connected.value)
        if (prefs.role == DeviceRole.CAPTURE && sourceDevice == null && !demo) {
            bus.send(buildJsonObject {
                put("type", "offer")
                put("offer", kotlinx.serialization.json.Json.encodeToJsonElement(OrderOffer.serializer(), offer))
            })
        }
        if (speaksHere && prefs.ttsEnabled && shouldAnnounce(offer)) {
            val phrase = Phrases.announce(offer, score, locale(), brief = score.isNight && prefs.scoring.nightBriefTts, auto = deadline != null)
            lastSpoken = phrase
            voice(phrase)
        }
        if (deadline != null) {
            autoJob?.cancel()
            autoJob = scope.launch {
                delay(deadline - now)
                if (_live.value?.offer?.id == offer.id) decide(Action.ACCEPT, DecidedBy.AUTO)
            }
        }
    }

    private fun shouldAnnounce(offer: OrderOffer): Boolean =
        OfferTag.SCHEDULED !in offer.tags || prefs.scoring.scheduledPolicy == app.taxicom.core.scoring.ScheduledPolicy.ANNOUNCE

    fun repeatLast() {
        lastSpoken?.let(voice)
    }

    /** Cancels a pending auto-accept without deciding (wheel ⏮ during the veto window). */
    fun veto(): Boolean {
        val current = _live.value ?: return false
        if (current.autoDeadlineMs == null) return false
        autoJob?.cancel()
        _live.value = current.copy(autoDeadlineMs = null)
        autoPolicy.onManualDecision()
        return true
    }

    fun decide(action: Action, by: DecidedBy) {
        val current = _live.value ?: return
        val now = clock()
        if (by != DecidedBy.AUTO) {
            autoJob?.cancel()
            autoPolicy.onManualDecision()
        }
        // An explicit decline during a trip would count against the acceptance rate: always ignore instead.
        val effective = if (action == Action.DECLINE && _shift.value.onTrip(now)) Action.IGNORE else action

        if (current.sourceDevice != null && by != DecidedBy.REMOTE) {
            bus.send(buildJsonObject {
                put("type", "decision")
                put("offer_id", current.offer.id)
                put("action", effective.name)
                put("to", current.sourceDevice)
            })
        } else if (!current.demo && effective != Action.IGNORE) {
            tapPerformer?.tap(current.offer.platform, effective)
        }

        val outcome = when (effective) {
            Action.ACCEPT -> Outcome.ACCEPTED
            Action.DECLINE -> Outcome.DECLINED
            Action.IGNORE -> Outcome.IGNORED
        }
        if (effective == Action.ACCEPT) {
            if (by == DecidedBy.AUTO) autoPolicy.onAutoAccepted()
            val busyMin = (current.offer.pickup?.minutes ?: 0) + (current.offer.trip?.minutes ?: 0)
            _shift.update { it.copy(onTripUntilMs = now + (busyMin * 1.1 * 60_000).toLong() + 2 * 60_000) }
        }
        finish(current, outcome, by)
    }

    private fun finish(current: LiveOffer, outcome: Outcome, by: DecidedBy?, fromRemote: Boolean = false) {
        if (_live.value?.offer?.id == current.offer.id) _live.value = null
        autoJob?.cancel()
        lastActivityMs = clock()
        if (!current.demo) {
            val alert = acceptance.record(current.offer.platform, outcome, OfferTag.NO_PENALTY_DECLINE in current.offer.tags)
            _acceptance.value = Platform.entries.associateWith { acceptance.rate(it) }
            alert?.let { _alerts.tryEmit(it) }
        }
        _outcomes.tryEmit(current.offer.id to outcome)
        if (current.sourceDevice == null && prefs.role == DeviceRole.CAPTURE && !fromRemote && !current.demo) {
            bus.send(buildJsonObject {
                put("type", "offer_gone")
                put("offer_id", current.offer.id)
                put("outcome", JsonPrimitive(outcome.name))
            })
        }
        if (!current.demo) scope.launch { dao.upsert(entity(current, outcome, by)) }
    }

    /** Reading from the platform's own acceptance screen anchors the estimate. */
    fun observeAcceptance(platform: Platform, rate: Double) {
        acceptance.observe(platform, rate)
        _acceptance.value = Platform.entries.associateWith { acceptance.rate(it) }
    }

    private fun entity(l: LiveOffer, outcome: Outcome, by: DecidedBy?): OfferEntity {
        val local = Instant.ofEpochMilli(l.offer.capturedAtMs).atZone(zone())
        return OfferEntity(
            id = l.offer.id,
            platform = l.offer.platform.name,
            product = l.offer.product,
            capturedAtMs = l.offer.capturedAtMs,
            localDate = local.toLocalDate().toString(),
            localHour = local.hour,
            currency = l.offer.currency,
            price = l.offer.price,
            net = l.score.net,
            pickupMin = l.offer.pickup?.minutes,
            pickupKm = l.offer.pickup?.km,
            tripMin = l.offer.trip?.minutes,
            tripKm = l.offer.trip?.km,
            pickupAddress = l.offer.pickup?.address,
            dropoffAddress = l.offer.trip?.address,
            e = l.score.e,
            eStar = l.score.eStar,
            rKm = l.score.rKm,
            rH = l.score.rH,
            verdict = l.score.verdict.name,
            outcome = outcome.name,
            decidedBy = by?.let { if (it == DecidedBy.REMOTE) "DRIVER" else it.name },
            isNight = l.score.isNight,
            isLong = l.score.isLong,
            onTrip = l.onTripAtOffer,
            tags = l.offer.tags.joinToString(",") { it.name },
        )
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
    }
}
