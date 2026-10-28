package app.taxicom.commandos.ui.cockpit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.taxicom.commandos.R
import app.taxicom.commandos.data.OfferEntity
import app.taxicom.commandos.decision.Action
import app.taxicom.commandos.decision.DecidedBy
import app.taxicom.commandos.decision.LiveOffer
import app.taxicom.commandos.decision.OfferPipeline
import app.taxicom.commandos.ui.components.AmbientBackground
import app.taxicom.commandos.ui.components.BrandMark
import app.taxicom.commandos.ui.components.Kpi
import app.taxicom.commandos.ui.components.PlatformDot
import app.taxicom.commandos.ui.components.ScoreRing
import app.taxicom.commandos.ui.components.SectionCard
import app.taxicom.commandos.ui.components.VerdictBadge
import app.taxicom.commandos.ui.money
import app.taxicom.commandos.ui.pct
import app.taxicom.commandos.ui.reasonRes
import app.taxicom.commandos.ui.theme.Amber
import app.taxicom.commandos.ui.theme.Skip
import app.taxicom.commandos.ui.theme.Take
import app.taxicom.core.model.Verdict
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

data class DaySummary(
    val income: Double,
    val rateHour: Double?,
    val accepted: Int,
    val offers: Int,
    val acceptance: Double?,
    val currency: String,
)

@Composable
fun CockpitScreen(
    pipeline: OfferPipeline,
    todayOffers: List<OfferEntity>,
    onStartShift: () -> Unit,
    onStopShift: () -> Unit,
) {
    val live by pipeline.live.collectAsStateWithLifecycle()
    val shift by pipeline.shift.collectAsStateWithLifecycle()
    val lambda by pipeline.offersPerHour.collectAsStateWithLifecycle()
    val rates by pipeline.acceptanceRates.collectAsStateWithLifecycle()
    val summary = remember(todayOffers) { summarize(todayOffers) }
    var sampleIdx by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    if (shift.active) {
                        val elapsed = ((System.currentTimeMillis() - (shift.startedAtMs ?: 0)) / 60_000).toInt()
                        Text(
                            stringResource(R.string.shift_on, "%d:%02d".format(elapsed / 60, elapsed % 60)),
                            style = MaterialTheme.typography.labelLarge, color = Take,
                        )
                    }
                }
            }
            item {
                AnimatedContent(live, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "live") { current ->
                    if (current != null) LiveCard(current, pipeline)
                    else WaitingCard(shift.active, lambda, summary.rateHour)
                }
            }
            item {
                SectionCard {
                    Text(stringResource(R.string.today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Kpi(stringResource(R.string.income), money(summary.income, summary.currency), accent = Amber)
                        Kpi(stringResource(R.string.rate_hour), summary.rateHour?.let { money(it, summary.currency) } ?: "—")
                        Kpi(stringResource(R.string.accepted_of), "${summary.accepted}/${summary.offers}")
                        Kpi(stringResource(R.string.acceptance), summary.acceptance?.let(::pct) ?: "—")
                    }
                    if (rates.any { it.value != null }) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rates.forEach { (p, r) ->
                                if (r != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PlatformDot(p.name)
                                        Spacer(Modifier.width(4.dp))
                                        Text(pct(r), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.recent_offers), style = MaterialTheme.typography.titleMedium)
            }
            if (todayOffers.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_offers_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(todayOffers.take(20), key = { it.id }) { OfferRow(it) }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp)) {
            if (!shift.active) {
                TextButton(onClick = {
                    pipeline.injectSample(sampleIdx)
                    sampleIdx++
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.try_sample), color = Amber)
                }
            }
            FloatingActionButton(
                onClick = { if (shift.active) onStopShift() else onStartShift() },
                containerColor = if (shift.active) Skip else Take,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (shift.active) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (shift.active) R.string.shift_stop else R.string.shift_start),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitingCard(active: Boolean, lambda: Double, typical: Double?) {
    SectionCard {
        Text(stringResource(R.string.waiting_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (active) stringResource(
                R.string.waiting_body, lambda,
                typical?.let { money(it) } ?: "—",
            ) else stringResource(R.string.waiting_off),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.wheel_hint), style = MaterialTheme.typography.labelMedium, color = Amber)
        }
    }
}

@Composable
private fun LiveCard(live: LiveOffer, pipeline: OfferPipeline) {
    val o = live.offer
    val s = live.score
    var remaining by remember(live.autoDeadlineMs) { mutableIntStateOf(0) }
    LaunchedEffect(live.autoDeadlineMs) {
        val deadline = live.autoDeadlineMs ?: return@LaunchedEffect
        while (true) {
            val left = ((deadline - System.currentTimeMillis()) / 1000).toInt()
            remaining = max(0, left)
            if (left <= 0) break
            delay(200)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                when (s.verdict) {
                    Verdict.TAKE -> Take.copy(alpha = 0.12f)
                    Verdict.MAYBE -> Amber.copy(alpha = 0.10f)
                    Verdict.SKIP -> Skip.copy(alpha = 0.10f)
                },
                RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                PlatformDot(o.platform.name)
                Text(o.product.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VerdictBadge(s.verdict)
            ScoreRing(s.relative)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money(o.price, o.currency), style = MaterialTheme.typography.displayMedium, color = Amber, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.net_estimate, money(s.net, o.currency)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.isNight) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.DarkMode, null, tint = Amber, modifier = Modifier.size(20.dp))
            }
            if (s.isLong) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Route, null, tint = Amber, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Kpi(stringResource(R.string.per_km), "${s.rKm}")
            Kpi(stringResource(R.string.per_hour), "${s.rH.roundToInt()}")
            Kpi(
                stringResource(R.string.vs_typical),
                if (s.relative >= 0) "+${(s.relative * 100).roundToInt()}%" else "${(s.relative * 100).roundToInt()}%",
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            o.pickup?.let {
                Text("${stringResource(R.string.pickup)} ${it.minutes}′ · ${it.km} km", style = MaterialTheme.typography.bodyMedium)
            }
            o.trip?.let {
                Text("${stringResource(R.string.trip)} ${it.minutes}′ · ${it.km} km", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (s.isLong && s.returnKm != null) {
            Text(stringResource(R.string.loop_km, "${s.totalKm}"), style = MaterialTheme.typography.bodyMedium, color = Amber)
        }
        o.trip?.address?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (s.reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val reasonText = s.reasons.take(3).map { stringResource(reasonRes(it)) }.joinToString(" · ")
            Text(
                reasonText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (remaining > 0) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.auto_countdown, remaining), color = Take, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecisionBtn(stringResource(R.string.action_accept), Take, Modifier.weight(1f)) {
                pipeline.decide(Action.ACCEPT, DecidedBy.DRIVER)
            }
            DecisionBtn(
                stringResource(if (live.onTripAtOffer) R.string.action_ignore else R.string.action_decline),
                Skip, Modifier.weight(1f),
            ) { pipeline.decide(if (live.onTripAtOffer) Action.IGNORE else Action.DECLINE, DecidedBy.DRIVER) }
        }
    }
}

@Composable
private fun DecisionBtn(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun OfferRow(o: OfferEntity) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformDot(o.platform)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${o.product ?: o.platform} · ${money(o.price, o.currency)}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${o.verdict} · ${outcomeLabel(o.outcome)} · E ${"%.2f".format(o.e)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${o.localHour}:00", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun outcomeLabel(o: String): String = stringResource(
    when (o) {
        "ACCEPTED", "COMPLETED" -> R.string.outcome_accepted
        "DECLINED" -> R.string.outcome_declined
        "IGNORED" -> R.string.outcome_ignored
        "EXPIRED" -> R.string.outcome_expired
        else -> R.string.outcome_pending
    },
)

private fun summarize(offers: List<OfferEntity>): DaySummary {
    val taken = offers.filter { it.outcome == "ACCEPTED" || it.outcome == "COMPLETED" }
    val income = taken.sumOf { it.net }
    val countable = offers.filter { it.outcome != "IGNORED" && !it.tags.contains("NO_PENALTY_DECLINE") }
    val first = offers.minOfOrNull { it.capturedAtMs }
    val last = offers.maxOfOrNull { it.capturedAtMs }
    val onlineMin = if (first != null && last != null) max(20.0, (last - first) / 60_000.0 + 20) else 0.0
    return DaySummary(
        income = income,
        rateHour = if (onlineMin > 0) income / onlineMin * 60 else null,
        accepted = taken.size,
        offers = offers.size,
        acceptance = if (countable.isNotEmpty()) {
            countable.count { it.outcome == "ACCEPTED" || it.outcome == "COMPLETED" }.toDouble() / countable.size
        } else null,
        currency = offers.firstOrNull()?.currency ?: "PLN",
    )
}
