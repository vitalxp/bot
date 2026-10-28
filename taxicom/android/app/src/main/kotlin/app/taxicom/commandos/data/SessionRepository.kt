package app.taxicom.commandos.data

import android.content.Context
import android.os.Build
import app.taxicom.commandos.BuildConfig
import app.taxicom.core.model.DeviceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.TimeZone

sealed interface AuthError {
    data object Credentials : AuthError
    data object Taken : AuthError
    data object Weak : AuthError
    data object Network : AuthError
}

class SessionRepository(private val context: Context, private val tokens: TokenStore) {
    private val _session = MutableStateFlow(tokens.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    lateinit var api: ApiClient

    fun onSessionLost() {
        tokens.clear()
        _session.value = null
    }

    private fun device(role: DeviceRole): DeviceDto {
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        return DeviceDto(
            name = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            model = Build.MODEL,
            formFactor = if (tablet) "TABLET" else "PHONE",
            role = role.name,
            platforms = installedPlatforms(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    private fun installedPlatforms(): List<String> =
        app.taxicom.core.model.Platform.entries.filter {
            runCatching { context.packageManager.getPackageInfo(it.packageName, 0) }.isSuccess
        }.map { it.name }

    suspend fun login(email: String, password: String, role: DeviceRole): AuthError? =
        authenticate(email) { api.login(email.trim(), password, device(role)) }

    suspend fun register(email: String, password: String, name: String?, role: DeviceRole): AuthError? =
        authenticate(email) {
            api.register(
                email.trim(), password, name?.ifBlank { null }, TimeZone.getDefault().id,
                Locale.getDefault().language.takeIf { it in setOf("ru", "pl") } ?: "en", device(role),
            )
        }

    private suspend fun authenticate(email: String, block: suspend () -> TokenDto): AuthError? = try {
        val t = block()
        val s = Session(t.accessToken, t.refreshToken, t.userId, t.deviceId, email.trim().lowercase())
        tokens.save(s)
        _session.value = s
        null
    } catch (e: ApiException) {
        when (e.code) {
            401 -> AuthError.Credentials
            409 -> AuthError.Taken
            422 -> AuthError.Weak
            else -> AuthError.Network
        }
    } catch (e: java.io.IOException) {
        AuthError.Network
    }

    suspend fun logout() {
        api.logout()
        onSessionLost()
    }

    suspend fun deleteAccount(): Boolean = try {
        api.deleteAccount()
        onSessionLost()
        true
    } catch (e: java.io.IOException) {
        false
    }
}
