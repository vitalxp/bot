package app.taxicom.commandos.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.taxicom.commandos.ui.components.AmbientBackground
import app.taxicom.commandos.ui.components.BrandMark
import app.taxicom.commandos.ui.components.PrimaryButton
import app.taxicom.commandos.ui.components.SecondaryButton
import app.taxicom.commandos.ui.theme.Amber
import app.taxicom.core.model.DeviceRole
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onRole: suspend (DeviceRole) -> Unit,
    onDone: suspend () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var role by remember { mutableStateOf(DeviceRole.BOTH) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            BrandMark()
            Spacer(Modifier.height(24.dp))
            when (step) {
                0 -> WelcomeStep { step = 1 }
                1 -> RoleStep(role, { role = it }) {
                    scope.launch { onRole(role); step = 2 }
                }
                2 -> PermissionsStep(
                    onBack = { step = 1 },
                    onNext = { scope.launch { onDone() } },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = Amber)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        listOf(
            Icons.Outlined.Speed to R.string.onb_welcome_1,
            Icons.Outlined.Bluetooth to R.string.onb_welcome_2,
            Icons.Outlined.PhoneAndroid to R.string.onb_welcome_3,
        ).forEach { (icon, text) ->
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = Amber, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(14.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.onb_start), onClick = onNext)
    }
}

@Composable
private fun RoleStep(selected: DeviceRole, onSelect: (DeviceRole) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.onb_role_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_role_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        listOf(
            DeviceRole.BOTH to (R.string.role_both to R.string.role_both_hint),
            DeviceRole.CAPTURE to (R.string.role_capture to R.string.role_capture_hint),
            DeviceRole.COCKPIT to (R.string.role_cockpit to R.string.role_cockpit_hint),
            DeviceRole.VIEWER to (R.string.role_viewer to R.string.role_viewer_hint),
        ).forEach { (role, texts) ->
            val on = selected == role
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(
                        if (on) Amber.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.dp, if (on) Amber else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { onSelect(role) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (role == DeviceRole.VIEWER) Icons.Outlined.Tablet else Icons.Outlined.PhoneAndroid,
                    null, tint = if (on) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(stringResource(texts.first), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(texts.second), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.next), onClick = onNext)
    }
}

@Composable
private fun PermissionsStep(onBack: () -> Unit, onNext: () -> Unit) {
    val ctx = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.onb_permissions_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_permissions_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!BuildConfig.FULL_CAPTURE) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.perm_lite_note), style = MaterialTheme.typography.bodyMedium, color = Amber)
        }
        Spacer(Modifier.height(16.dp))
        if (BuildConfig.FULL_CAPTURE) {
            PermRow(R.string.perm_accessibility_title, R.string.perm_accessibility_body, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            PermRow(R.string.perm_notifications_title, R.string.perm_notifications_body, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            PermRow(R.string.perm_overlay_title, R.string.perm_overlay_body, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            PermRow(R.string.perm_battery_title, R.string.perm_battery_body, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        PermRow(
            R.string.perm_post_notifications_title, R.string.perm_post_notifications_body, action = null,
            granted = notifGranted,
            onGrant = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else notifGranted = true
            },
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.onb_finish), onClick = onNext)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(stringResource(R.string.back), onClick = onBack)
    }
}

@Composable
private fun PermRow(title: Int, body: Int, action: String?, granted: Boolean = false, onGrant: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            if (granted) stringResource(R.string.granted) else stringResource(R.string.grant),
            onClick = {
                if (onGrant != null) onGrant()
                else if (action != null) {
                    val intent = Intent(action).apply {
                        if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                }
            },
            enabled = !granted,
        )
    }
}
