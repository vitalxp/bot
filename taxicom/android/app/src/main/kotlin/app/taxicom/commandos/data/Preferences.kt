package app.taxicom.commandos.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.taxicom.core.model.DeviceRole
import app.taxicom.core.scoring.ScoringSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "taxicom_prefs")

@Serializable
data class AppPrefs(
    val onboardingDone: Boolean = false,
    val offlineMode: Boolean = false,
    val role: DeviceRole = DeviceRole.BOTH,
    val scoring: ScoringSettings = ScoringSettings(),
    val ttsEnabled: Boolean = true,
    val wheelEnabled: Boolean = true,
)

class PreferencesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val offline = booleanPreferencesKey("offline_mode")
        val role = stringPreferencesKey("device_role")
        val scoring = stringPreferencesKey("scoring_settings")
        val tts = booleanPreferencesKey("tts")
        val wheel = booleanPreferencesKey("wheel")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            onboardingDone = p[Keys.onboarding] ?: false,
            offlineMode = p[Keys.offline] ?: false,
            role = p[Keys.role]?.let { runCatching { DeviceRole.valueOf(it) }.getOrNull() } ?: DeviceRole.BOTH,
            scoring = p[Keys.scoring]?.let { runCatching { json.decodeFromString<ScoringSettings>(it) }.getOrNull() }
                ?: ScoringSettings(),
            ttsEnabled = p[Keys.tts] ?: true,
            wheelEnabled = p[Keys.wheel] ?: true,
        )
    }

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboarding] = done }
    suspend fun setOffline(offline: Boolean) = context.dataStore.edit { it[Keys.offline] = offline }
    suspend fun setRole(role: DeviceRole) = context.dataStore.edit { it[Keys.role] = role.name }
    suspend fun setTts(on: Boolean) = context.dataStore.edit { it[Keys.tts] = on }
    suspend fun setWheel(on: Boolean) = context.dataStore.edit { it[Keys.wheel] = on }

    suspend fun updateScoring(transform: (ScoringSettings) -> ScoringSettings) = context.dataStore.edit { p ->
        val current = p[Keys.scoring]?.let { runCatching { json.decodeFromString<ScoringSettings>(it) }.getOrNull() }
            ?: ScoringSettings()
        p[Keys.scoring] = json.encodeToString(ScoringSettings.serializer(), transform(current))
    }
}
