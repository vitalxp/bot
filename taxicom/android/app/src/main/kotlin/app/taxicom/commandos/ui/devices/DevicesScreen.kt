package app.taxicom.commandos.ui.devices

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.taxicom.commandos.R
import app.taxicom.commandos.data.AccountDeviceDto
import app.taxicom.commandos.data.Session
import app.taxicom.commandos.ui.components.AmbientBackground
import app.taxicom.commandos.ui.components.BrandMark
import app.taxicom.commandos.ui.components.PrimaryButton
import app.taxicom.commandos.ui.components.SectionCard
import app.taxicom.commandos.ui.theme.Amber
import app.taxicom.commandos.ui.theme.Take
import app.taxicom.core.model.DeviceRole

@Composable
fun DevicesScreen(
    session: Session?,
    thisRole: DeviceRole,
    devices: List<AccountDeviceDto>,
    onSignIn: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            BrandMark()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.devices_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (session == null) {
                Text(stringResource(R.string.devices_need_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.set_sign_in), onClick = onSignIn)
            } else {
                SectionCard {
                    Text(stringResource(R.string.this_device), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(roleLabel(thisRole), style = MaterialTheme.typography.titleLarge, color = Amber)
                    Text(session.email, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                devices.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(if (d.online) Take else MaterialTheme.colorScheme.outline, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${d.role} · ${d.platforms.joinToString().ifEmpty { "—" }} · ${if (d.online) stringResource(R.string.online) else stringResource(R.string.offline)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (d.id == session.deviceId) {
                            Text(stringResource(R.string.this_device), color = Amber, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: DeviceRole): String = stringResource(
    when (role) {
        DeviceRole.BOTH -> R.string.role_both
        DeviceRole.CAPTURE -> R.string.role_capture
        DeviceRole.COCKPIT -> R.string.role_cockpit
        DeviceRole.VIEWER -> R.string.role_viewer
    },
)
