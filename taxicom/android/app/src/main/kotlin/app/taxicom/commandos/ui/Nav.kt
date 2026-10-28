package app.taxicom.commandos.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.taxicom.commandos.AppGraph
import app.taxicom.commandos.R
import app.taxicom.commandos.data.AccountDeviceDto
import app.taxicom.commandos.data.AppPrefs
import app.taxicom.commandos.data.Session
import app.taxicom.commandos.data.SyncWorker
import app.taxicom.commandos.service.TaxiCommandosService
import app.taxicom.commandos.ui.auth.AuthScreen
import app.taxicom.commandos.ui.cockpit.CockpitScreen
import app.taxicom.commandos.ui.devices.DevicesScreen
import app.taxicom.commandos.ui.onboarding.OnboardingScreen
import app.taxicom.commandos.ui.reports.ReportsScreen
import app.taxicom.commandos.ui.settings.SettingsScreen
import app.taxicom.commandos.ui.theme.Amber
import java.time.LocalDate

private enum class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Cockpit("cockpit", R.string.nav_cockpit, Icons.Outlined.Speed),
    Reports("reports", R.string.nav_reports, Icons.Outlined.BarChart),
    Devices("devices", R.string.nav_devices, Icons.Outlined.Devices),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun TaxiCommandosApp(graph: AppGraph) {
    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(AppPrefs())
    val session by graph.sessions.session.collectAsStateWithLifecycle()
    var showAuth by remember { mutableStateOf(false) }

    when {
        !prefs.onboardingDone -> OnboardingScreen(
            onRole = { graph.prefs.setRole(it) },
            onDone = { graph.prefs.setOnboardingDone(true); showAuth = true },
        )
        showAuth && session == null && !prefs.offlineMode -> AuthScreen(
            role = prefs.role,
            onLogin = { e, p -> graph.sessions.login(e, p, prefs.role) },
            onRegister = { e, p, n -> graph.sessions.register(e, p, n, prefs.role) },
            onOffline = { graph.prefs.setOffline(true) },
            onDone = { showAuth = false },
        )
        else -> MainShell(graph, prefs, session, onShowAuth = { showAuth = true })
    }
}

@Composable
private fun MainShell(graph: AppGraph, prefs: AppPrefs, session: Session?, onShowAuth: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = LocalDate.now().toString()
    val from = LocalDate.now().minusDays(6).toString()
    val todayOffers by graph.db.offers().forDate(today).collectAsStateWithLifecycle(emptyList())
    val weekOffers by graph.db.offers().forRange(from, today).collectAsStateWithLifecycle(emptyList())
    var devices by remember { mutableStateOf<List<AccountDeviceDto>>(emptyList()) }

    LaunchedEffect(session) {
        if (session != null) {
            devices = runCatching { graph.api.devices() }.getOrDefault(emptyList())
        } else {
            devices = emptyList()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF12161E)) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.navigate(tab.route) { launchSingleTop = true; restoreState = true; popUpTo(Tab.Cockpit.route) { saveState = true } } },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Amber,
                            selectedTextColor = Amber,
                            indicatorColor = Amber.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Cockpit.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Cockpit.route) {
                CockpitScreen(
                    pipeline = graph.pipeline,
                    todayOffers = todayOffers,
                    onStartShift = { TaxiCommandosService.start(ctx) },
                    onStopShift = { TaxiCommandosService.stop(ctx) },
                )
            }
            composable(Tab.Reports.route) {
                ReportsScreen(
                    offers = weekOffers,
                    session = session,
                    onSend = { date, channels ->
                        graph.api.sendReport(date, channels)
                        SyncWorker.syncNow(ctx)
                    },
                )
            }
            composable(Tab.Devices.route) {
                DevicesScreen(session = session, thisRole = prefs.role, devices = devices, onSignIn = onShowAuth)
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    prefs = prefs,
                    session = session,
                    onUpdate = { graph.prefs.updateScoring(it) },
                    onTts = { graph.prefs.setTts(it) },
                    onWheel = { graph.prefs.setWheel(it) },
                    onLinkTelegram = { graph.api.telegramLinkCode().deepLink },
                    onSignIn = onShowAuth,
                    onSignOut = { graph.sessions.logout() },
                    onDelete = {
                        graph.sessions.deleteAccount()
                        graph.db.offers().clear()
                    },
                )
            }
        }
    }
}
