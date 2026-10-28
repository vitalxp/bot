package app.taxicom.commandos.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.taxicom.commandos.BuildConfig
import app.taxicom.commandos.R
import app.taxicom.commandos.data.AppPrefs
import app.taxicom.commandos.data.Session
import app.taxicom.commandos.ui.components.AmbientBackground
import app.taxicom.commandos.ui.components.BrandMark
import app.taxicom.commandos.ui.components.PrimaryButton
import app.taxicom.commandos.ui.components.SectionCard
import app.taxicom.commandos.ui.theme.Amber
// SectionCard used in Section()
import app.taxicom.core.scoring.CashPolicy
import app.taxicom.core.scoring.DecisionMode
import app.taxicom.core.scoring.ScoringSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    session: Session?,
    onUpdate: suspend ((ScoringSettings) -> ScoringSettings) -> Unit,
    onTts: suspend (Boolean) -> Unit,
    onWheel: suspend (Boolean) -> Unit,
    onLinkTelegram: suspend () -> String?,
    onSignIn: () -> Unit,
    onSignOut: suspend () -> Unit,
    onDelete: suspend () -> Unit,
) {
    var deleteOpen by remember { mutableStateOf(false) }
    var tgLink by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val s = prefs.scoring

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            BrandMark()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))

            Section(stringResource(R.string.set_scoring)) {
                SliderRow(stringResource(R.string.set_long_trip_km), s.longTripKm.toFloat(), 10f..100f, "%.0f km") {
                    scope.launch { onUpdate { c -> c.copy(longTripKm = it.toDouble()) } }
                }
                SliderRow(stringResource(R.string.set_cost_km), s.costPerKm.toFloat(), 0.1f..3f, "%.2f zł") {
                    scope.launch { onUpdate { c -> c.copy(costPerKm = it.toDouble()) } }
                }
                SliderRow(stringResource(R.string.set_alpha), s.alpha.toFloat(), 0f..1f, "%.2f") {
                    scope.launch { onUpdate { c -> c.copy(alpha = it.toDouble()) } }
                }
                SliderRow(stringResource(R.string.set_max_pickup), s.maxPickupMin.toFloat(), 5f..60f, "%.0f min") {
                    scope.launch { onUpdate { c -> c.copy(maxPickupMin = it.roundToInt()) } }
                }
                SliderRow(stringResource(R.string.set_min_rating), s.minRiderRating.toFloat(), 4f..5f, "%.1f") {
                    scope.launch { onUpdate { c -> c.copy(minRiderRating = it.toDouble()) } }
                }
                Text(stringResource(R.string.set_cash), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CashPolicy.NEUTRAL to R.string.cash_neutral,
                        CashPolicy.PENALTY_10 to R.string.cash_penalty,
                        CashPolicy.NEVER to R.string.cash_never,
                    ).forEach { (p, label) ->
                        FilterChip(selected = s.cashPolicy == p, onClick = {
                            scope.launch { onUpdate { c -> c.copy(cashPolicy = p) } }
                        }, label = { Text(stringResource(label)) })
                    }
                }
            }

            Section(stringResource(R.string.set_night)) {
                Text(
                    stringResource(R.string.set_night_hours) + ": %02d:00–%02d:00".format(s.nightStartMin / 60, s.nightEndMin / 60),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SliderRow(stringResource(R.string.set_night_rating), s.nightMinRiderRating.toFloat(), 4f..5f, "%.1f") {
                    scope.launch { onUpdate { c -> c.copy(nightMinRiderRating = it.toDouble()) } }
                }
                SliderRow(stringResource(R.string.set_night_pmax), s.nightPMax.toFloat(), 0.2f..0.7f, "%.2f") {
                    scope.launch { onUpdate { c -> c.copy(nightPMax = it.toDouble()) } }
                }
            }

            Section(stringResource(R.string.set_fees)) {
                SwitchRow(stringResource(R.string.set_uber_price_is_net), s.uberPriceIsNet) {
                    scope.launch { onUpdate { c -> c.copy(uberPriceIsNet = it) } }
                }
                Text(
                    stringResource(R.string.set_uber_price_is_net_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!s.uberPriceIsNet) {
                    SliderRow(stringResource(R.string.set_uber_fee), s.uberFeePct.toFloat(), 10f..40f, "%.0f %%") {
                        scope.launch { onUpdate { c -> c.copy(uberFeePct = it.toDouble()) } }
                    }
                    SwitchRow(stringResource(R.string.set_uber_vat), s.uberFeeVat) {
                        scope.launch { onUpdate { c -> c.copy(uberFeeVat = it) } }
                    }
                }
                SliderRow(stringResource(R.string.set_freenow_fee), s.freenowFeePct.toFloat(), 0f..35f, "%.0f %%") {
                    scope.launch { onUpdate { c -> c.copy(freenowFeePct = it.toDouble()) } }
                }
                SliderRow(stringResource(R.string.set_fleet_fee), s.fleetFeePct.toFloat(), 0f..30f, "%.0f %%") {
                    scope.launch { onUpdate { c -> c.copy(fleetFeePct = it.toDouble()) } }
                }
            }

            Section(stringResource(R.string.set_decisions)) {
                SwitchRow(stringResource(R.string.set_mode_auto), s.decisionMode == DecisionMode.AUTO) {
                    scope.launch { onUpdate { c -> c.copy(decisionMode = if (it) DecisionMode.AUTO else DecisionMode.ADVISOR) } }
                }
                Text(stringResource(R.string.set_mode_auto_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SliderRow(stringResource(R.string.set_auto_ratio), s.autoMinScoreRatio.toFloat(), 1f..1.5f, "%.2f × E*") {
                    scope.launch { onUpdate { c -> c.copy(autoMinScoreRatio = it.toDouble()) } }
                }
                SliderRow(stringResource(R.string.set_veto), s.autoVetoMs / 1000f, 1f..4f, "%.0f s") {
                    scope.launch { onUpdate { c -> c.copy(autoVetoMs = (it * 1000).toLong()) } }
                }
                SliderRow(stringResource(R.string.set_max_consecutive), s.autoMaxConsecutive.toFloat(), 1f..10f, "%.0f") {
                    scope.launch { onUpdate { c -> c.copy(autoMaxConsecutive = it.roundToInt()) } }
                }
            }

            Section(stringResource(R.string.set_acceptance)) {
                SliderRow(stringResource(R.string.set_protect_below), s.protectAcceptanceBelow.toFloat(), 0.4f..0.7f, "%.0f %%") {
                    scope.launch { onUpdate { c -> c.copy(protectAcceptanceBelow = it.toDouble()) } }
                }
            }

            Section(stringResource(R.string.set_bonus)) {
                Text(stringResource(R.string.set_bonus_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SliderRow(stringResource(R.string.set_bonus_per_trip), s.bonusPlusPerTrip.toFloat(), 0f..20f, "%.0f zł") {
                    scope.launch { onUpdate { c -> c.copy(bonusPlusPerTrip = it.toDouble()) } }
                }
            }

            Section(stringResource(R.string.set_voice)) {
                SwitchRow(stringResource(R.string.set_tts), prefs.ttsEnabled) { scope.launch { onTts(it) } }
                SwitchRow(stringResource(R.string.set_brief_night), s.nightBriefTts) {
                    scope.launch { onUpdate { c -> c.copy(nightBriefTts = it) } }
                }
                SwitchRow(stringResource(R.string.set_wheel), prefs.wheelEnabled) { scope.launch { onWheel(it) } }
            }

            Section(stringResource(R.string.set_reports)) {
                if (session == null) {
                    PrimaryButton(stringResource(R.string.set_sign_in), onClick = onSignIn)
                } else {
                    Text(session.email, style = MaterialTheme.typography.bodyLarge, color = Amber)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(stringResource(R.string.set_link_telegram)) {
                        scope.launch {
                            tgLink = onLinkTelegram()
                            tgLink?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }
                    }
                }
            }

            Section(stringResource(R.string.set_account)) {
                if (session != null) {
                    PrimaryButton(stringResource(R.string.set_sign_out)) { scope.launch { onSignOut() } }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { deleteOpen = true }) {
                        Text(stringResource(R.string.set_delete_account), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    PrimaryButton(stringResource(R.string.set_sign_in), onClick = onSignIn)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://taxicom.app/privacy")))
                }) { Text(stringResource(R.string.set_privacy), color = Amber) }
                Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.set_delete_account)) },
            text = { Text(stringResource(R.string.set_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false; scope.launch { onDelete() } }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    SectionCard { content() }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(format.format(local), color = Amber, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
