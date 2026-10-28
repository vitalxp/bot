package app.taxicom.commandos.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private fun q(s: String): String = JsonPrimitive(s).toString()

class ApiException(val code: Int, val error: String?) : IOException("HTTP $code $error")

@Serializable
data class DeviceDto(
    val name: String,
    val model: String? = null,
    @SerialName("form_factor") val formFactor: String = "PHONE",
    val role: String = "BOTH",
    val platforms: List<String> = emptyList(),
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class MeDto(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String? = null,
    val timezone: String,
    val locale: String,
    @SerialName("report_email_enabled") val reportEmail: Boolean,
    @SerialName("report_telegram_enabled") val reportTelegram: Boolean,
    @SerialName("telegram_linked") val telegramLinked: Boolean,
)

@Serializable
data class AccountDeviceDto(
    val id: String,
    val name: String,
    val model: String? = null,
    @SerialName("form_factor") val formFactor: String,
    val role: String,
    val platforms: List<String>,
    val online: Boolean,
)

@Serializable
data class OfferUploadDto(
    val id: String,
    val platform: String,
    val product: String?,
    @SerialName("captured_at") val capturedAt: String,
    val currency: String,
    val price: Double,
    @SerialName("price_net") val priceNet: Double,
    @SerialName("pickup_km") val pickupKm: Double?,
    @SerialName("pickup_min") val pickupMin: Double?,
    @SerialName("trip_km") val tripKm: Double?,
    @SerialName("trip_min") val tripMin: Double?,
    val score: Double,
    val verdict: String,
    val outcome: String,
    @SerialName("decided_by") val decidedBy: String?,
    @SerialName("is_night") val isNight: Boolean,
    @SerialName("is_long") val isLong: Boolean,
    @SerialName("on_trip") val onTrip: Boolean,
    val tags: List<String>,
)

@Serializable
data class LinkCodeDto(val code: String, @SerialName("deep_link") val deepLink: String)

class ApiClient(
    private val baseUrl: String,
    private val tokens: TokenStore,
    private val onSessionLost: () -> Unit,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val refreshLock = Mutex()
    private val jsonType = "application/json".toMediaType()

    val wsUrl: String get() = baseUrl.replaceFirst("http", "ws") + "/v1/bus"

    suspend fun register(email: String, password: String, name: String?, timezone: String, locale: String, device: DeviceDto): TokenDto =
        call("POST", "/v1/auth/register", buildString {
            append("{\"email\":").append(q(email))
            append(",\"password\":").append(q(password))
            if (name != null) append(",\"display_name\":").append(q(name))
            append(",\"timezone\":").append(q(timezone))
            append(",\"locale\":").append(q(locale))
            append(",\"device\":").append(json.encodeToString(DeviceDto.serializer(), device)).append('}')
        }, auth = false).let { json.decodeFromString(TokenDto.serializer(), it) }

    suspend fun login(email: String, password: String, device: DeviceDto): TokenDto =
        call("POST", "/v1/auth/login", buildString {
            append("{\"email\":").append(q(email))
            append(",\"password\":").append(q(password))
            append(",\"device\":").append(json.encodeToString(DeviceDto.serializer(), device)).append('}')
        }, auth = false).let { json.decodeFromString(TokenDto.serializer(), it) }

    suspend fun logout() {
        val s = tokens.load() ?: return
        runCatching { call("POST", "/v1/auth/logout", "{\"refresh_token\":${q(s.refreshToken)}}", auth = false) }
    }

    suspend fun me(): MeDto = json.decodeFromString(MeDto.serializer(), call("GET", "/v1/me"))

    suspend fun patchMe(body: JsonObject): MeDto = json.decodeFromString(MeDto.serializer(), call("PATCH", "/v1/me", body.toString()))

    suspend fun deleteAccount() { call("DELETE", "/v1/me") }

    suspend fun devices(): List<AccountDeviceDto> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AccountDeviceDto.serializer()), call("GET", "/v1/devices"))

    suspend fun setRole(deviceId: String, role: String) {
        call("PATCH", "/v1/devices/$deviceId", "{\"role\":\"$role\"}")
    }

    suspend fun uploadOffers(offers: List<OfferUploadDto>) {
        val body = "{\"offers\":" + json.encodeToString(kotlinx.serialization.builtins.ListSerializer(OfferUploadDto.serializer()), offers) + "}"
        call("POST", "/v1/offers/batch", body)
    }

    suspend fun sendReport(date: String, channels: List<String>): String =
        call("POST", "/v1/reports/send", "{\"date\":\"$date\",\"channels\":${JsonArray(channels.map { JsonPrimitive(it) }).toString()}}")

    suspend fun telegramLinkCode(): LinkCodeDto =
        json.decodeFromString(LinkCodeDto.serializer(), call("POST", "/v1/telegram/link-code"))

    private suspend fun call(method: String, path: String, body: String? = null, auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val first = execute(method, path, body, if (auth) tokens.load()?.accessToken else null)
            if (first.first != 401 || !auth) return@withContext first.unwrap()
            if (!refresh()) {
                onSessionLost()
                throw ApiException(401, "session_lost")
            }
            execute(method, path, body, tokens.load()?.accessToken).unwrap()
        }

    private suspend fun refresh(): Boolean = refreshLock.withLock {
        val s = tokens.load() ?: return false
        val (code, text) = execute("POST", "/v1/auth/refresh", "{\"refresh_token\":${q(s.refreshToken)}}", null)
        if (code != 200) return false
        val t = json.decodeFromString(TokenDto.serializer(), text)
        tokens.save(s.copy(accessToken = t.accessToken, refreshToken = t.refreshToken))
        true
    }

    private fun execute(method: String, path: String, body: String?, bearer: String?): Pair<Int, String> {
        val req = Request.Builder().url(baseUrl + path).apply {
            bearer?.let { header("Authorization", "Bearer $it") }
            val rb = body?.toRequestBody(jsonType) ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(jsonType)
            method(method, rb)
        }.build()
        http.newCall(req).execute().use { return it.code to (it.body?.string().orEmpty()) }
    }

    private fun Pair<Int, String>.unwrap(): String {
        if (first in 200..299) return second
        val err = runCatching { json.parseToJsonElement(second).let { (it as JsonObject)["detail"]?.toString()?.trim('"') } }.getOrNull()
        throw ApiException(first, err)
    }
}
