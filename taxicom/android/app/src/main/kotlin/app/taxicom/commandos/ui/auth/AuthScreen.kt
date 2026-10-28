package app.taxicom.commandos.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.taxicom.commandos.R
import app.taxicom.commandos.data.AuthError
import app.taxicom.commandos.ui.components.AmbientBackground
import app.taxicom.commandos.ui.components.BrandMark
import app.taxicom.commandos.ui.components.PrimaryButton
import app.taxicom.commandos.ui.components.SecondaryButton
import app.taxicom.commandos.ui.theme.Amber
import app.taxicom.core.model.DeviceRole
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    role: DeviceRole,
    onLogin: suspend (String, String) -> AuthError?,
    onRegister: suspend (String, String, String?) -> AuthError?,
    onOffline: suspend () -> Unit,
    onDone: () -> Unit,
) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        AmbientBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            BrandMark()
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (register) R.string.auth_title_register else R.string.auth_title_login),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(20.dp))
            if (register) {
                Field(stringResource(R.string.auth_name), name, { name = it })
                Spacer(Modifier.height(10.dp))
            }
            Field(stringResource(R.string.auth_email), email, { email = it }, KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            Field(
                stringResource(R.string.auth_password), password, { password = it }, KeyboardType.Password,
                password = true, hint = stringResource(R.string.auth_password_hint),
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        when (it) {
                            AuthError.Credentials -> R.string.auth_error_credentials
                            AuthError.Taken -> R.string.auth_error_taken
                            AuthError.Weak -> R.string.auth_error_weak
                            AuthError.Network -> R.string.auth_error_network
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                stringResource(if (register) R.string.auth_submit_register else R.string.auth_submit_login),
                enabled = !busy && email.isNotBlank() && password.length >= 10,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val err = if (register) onRegister(email, password, name.ifBlank { null })
                        else onLogin(email, password)
                        busy = false
                        if (err == null) onDone() else error = err
                    }
                },
            )
            TextButton(onClick = { register = !register; error = null }) {
                Text(
                    stringResource(if (register) R.string.auth_switch_to_login else R.string.auth_switch_to_register),
                    color = Amber,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.auth_offline_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.auth_offline)) {
                scope.launch { onOffline(); onDone() }
            }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    type: KeyboardType = KeyboardType.Text, password: Boolean = false, hint: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}
