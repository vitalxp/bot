package app.taxicom.commandos.ui.reports

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.taxicom.commandos.R
import app.taxicom.commandos.data.OfferEntity
import app.taxicom.commandos.data.Session
import app.taxicom.commandos.ui.components.AmbientBackground
import app.taxicom.commandos.ui.components.BrandMark
import app.taxicom.commandos.ui.components.HourBars
import app.taxicom.commandos.ui.components.Kpi
import app.taxicom.commandos.ui.components.PlatformDot
import app.taxicom.commandos.ui.components.PrimaryButton
import app.taxicom.commandos.ui.components.SecondaryButton
import app.taxicom.commandos.ui.components.SectionCard
import app.taxicom.commandos.ui.money
import app.taxicom.commandos.ui.pct
import app.taxicom.commandos.ui.theme.Amber
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReportRange { TODAY, YESTERDAY, WEEK }

@Composable
fun ReportsScreen(
    offers: List<OfferEntity>,
    session: Session?,
    onSend: suspend (String, List<String>) -> Unit,
) {
    var range by remember { mutableStateOf(ReportRange.TODAY) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val filtered = remember(offers, range) {
        when (range) {
            ReportRange.TODAY -> offers.filter { it.localDate == today.toString() }
            ReportRange.YESTERDAY -> offers.filter { it.localDate == today.minusDays(1).toString() }
            ReportRange.WEEK -> {
                val from = today.minusDays(6).toString()
                offers.filter { it.localDate >= from }
            }
        }
    }
    val report = remember(filtered) { buildLocalReport(filtered) }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            BrandMark()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.nav_reports), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ReportRange.TODAY to R.string.range_today,
                    ReportRange.YESTERDAY to R.string.range_yesterday,
                    ReportRange.WEEK to R.string.range_week,
                ).forEach { (r, label) ->
                    FilterChip(selected = range == r, onClick = { range = r; status = null }, label = { Text(stringResource(label)) })
                }
            }
            Spacer(Modifier.height(16.dp))
            if (report.offers == 0) {
                Text(stringResource(R.string.report_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(money(report.income, report.currency), style = MaterialTheme.typography.displayLarge, color = Amber)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Kpi(stringResource(R.string.rate_hour), report.rateHour?.let { money(it, report.currency) } ?: "—")
                    Kpi(stringResource(R.string.per_km_label), report.rateKm?.let { "${"%.2f".format(it)} zł" } ?: "—")
                    Kpi(stringResource(R.string.acceptance), report.acceptance?.let(::pct) ?: "—")
                }
                Spacer(Modifier.height(16.dp))
                SectionCard {
                    Text(stringResource(R.string.by_hour), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    HourBars(report.byHour)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        report.byHour.firstOrNull()?.let { Text("%02d".format(it.first), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        report.byHour.lastOrNull()?.let { Text("%02d".format(it.first), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Text(stringResource(R.string.platform_split), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    report.platforms.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            PlatformDot(p.name)
                            Text("${p.accepted}/${p.offers}")
                            Text(money(p.net, report.currency))
                            Text(p.rate?.let(::pct) ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (report.insights.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        Text(stringResource(R.string.insights), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        report.insights.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.profit_after_cost) + ": ${money(report.profit, report.currency)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (session == null) {
                Text(stringResource(R.string.send_needs_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val date = when (range) {
                    ReportRange.TODAY -> today.toString()
                    ReportRange.YESTERDAY -> today.minusDays(1).toString()
                    ReportRange.WEEK -> today.minusDays(1).toString()
                }
                PrimaryButton(stringResource(R.string.send_email)) {
                    scope.launch {
                        runCatching { onSend(date, listOf("EMAIL")) }
                        status = "sent"
                    }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.send_telegram)) {
                    scope.launch {
                        runCatching { onSend(date, listOf("TELEGRAM")) }
                        status = "sent"
                    }
                }
                if (status == "sent") {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sent), color = Amber)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private data class LocalReport(
    val income: Double,
    val rateHour: Double?,
    val rateKm: Double?,
    val acceptance: Double?,
    val offers: Int,
    val accepted: Int,
    val byHour: List<Pair<Int, Double>>,
    val platforms: List<Plat>,
    val insights: List<String>,
    val profit: Double,
    val currency: String,
)

private data class Plat(val name: String, val offers: Int, val accepted: Int, val net: Double, val rate: Double?)

private fun buildLocalReport(offers: List<OfferEntity>): LocalReport {
    val taken = offers.filter { it.outcome == "ACCEPTED" || it.outcome == "COMPLETED" }
    val income = taken.sumOf { it.net }
    val km = taken.sumOf { (it.pickupKm ?: 0.0) + (it.tripKm ?: 0.0) }
    val first = offers.minOfOrNull { it.capturedAtMs }
    val last = offers.maxOfOrNull { it.capturedAtMs }
    val online = if (first != null && last != null) max(20.0, (last - first) / 60_000.0 + 20) else 0.0
    val countable = offers.filter { it.outcome != "IGNORED" && !it.tags.contains("NO_PENALTY_DECLINE") }
    val byHour = offers.groupBy { it.localHour }.toSortedMap().map { (h, list) ->
        h to list.filter { it.outcome == "ACCEPTED" || it.outcome == "COMPLETED" }.sumOf { it.net }
    }
    val platforms = listOf("BOLT", "UBER", "FREENOW").mapNotNull { name ->
        val p = offers.filter { it.platform == name }
        if (p.isEmpty()) return@mapNotNull null
        val t = p.filter { it.outcome == "ACCEPTED" || it.outcome == "COMPLETED" }
        val c = p.filter { it.outcome != "IGNORED" && !it.tags.contains("NO_PENALTY_DECLINE") }
        Plat(name, p.size, t.size, t.sumOf { it.net }, if (c.isNotEmpty()) t.size.toDouble() / c.size else null)
    }
    val ignored = offers.count { it.outcome == "IGNORED" }
    val regret = taken.count { it.verdict == "SKIP" }
    val missed = offers.filter { it.verdict == "TAKE" && it.outcome in setOf("DECLINED", "EXPIRED") && !it.onTrip }
    val insights = buildList {
        byHour.maxByOrNull { it.second }?.let { add("Best hour %02d:00: %.0f zł/h".format(it.first, it.second)) }
        if (missed.isNotEmpty()) add("Missed ${missed.size} good offers (≈ ${missed.sumOf { it.net }.roundToInt()} zł)")
        if (ignored > 0) add("$ignored offers ignored during trips — acceptance untouched")
        if (regret > 0) add("Accepted $regret weak orders against the advice")
    }
    val cost = 0.55 * km
    return LocalReport(
        income = income,
        rateHour = if (online > 0) income / online * 60 else null,
        rateKm = if (km > 0) income / km else null,
        acceptance = if (countable.isNotEmpty()) countable.count { it.outcome == "ACCEPTED" || it.outcome == "COMPLETED" }.toDouble() / countable.size else null,
        offers = offers.size,
        accepted = taken.size,
        byHour = byHour,
        platforms = platforms,
        insights = insights,
        profit = income - cost,
        currency = offers.firstOrNull()?.currency ?: "PLN",
    )
}
