package app.taxicom.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val displayName: String, val packageName: String) {
    BOLT("Bolt", "ee.mtakso.driver"),
    UBER("Uber", "com.ubercab.driver"),
    FREENOW("FREENOW", "taxi.android.driver");

    companion object {
        fun fromPackage(pkg: String?): Platform? = entries.firstOrNull { it.packageName == pkg }
    }
}

@Serializable
enum class PriceBasis { NET_AFTER_COMMISSION, GROSS_BEFORE_VARIABLE_FEE, UNKNOWN }

@Serializable
enum class OfferTag { CASH, OUT_OF_RANGE, SCHEDULED, EXCLUSIVE, PRIORITY, NO_PENALTY_DECLINE, LONG_TRIP }

@Serializable
data class LatLng(val lat: Double, val lng: Double)

/** One trip segment as shown on the card: pickup (driver → rider) or trip (rider → destination). */
@Serializable
data class Segment(val minutes: Int, val km: Double, val address: String?)

@Serializable
data class OrderOffer(
    val id: String,
    val platform: Platform,
    val product: String?,
    val capturedAtMs: Long,
    val currency: String,
    /** Number shown on the card. For Uber it is before the variable service fee. */
    val price: Double,
    val priceBasis: PriceBasis,
    val ratePerKm: Double? = null,
    val bonus: Double? = null,
    val pickup: Segment?,
    val trip: Segment?,
    val pickupGeo: LatLng? = null,
    val dropoffGeo: LatLng? = null,
    val riderName: String? = null,
    val riderRating: Double? = null,
    val tags: Set<OfferTag> = emptySet(),
    val uiLocale: String? = null,
    val rawLines: List<String> = emptyList(),
)

@Serializable
enum class Verdict { TAKE, MAYBE, SKIP }

@Serializable
enum class Outcome { PENDING, ACCEPTED, DECLINED, IGNORED, EXPIRED, CANCELLED, COMPLETED }

@Serializable
enum class DeviceRole { CAPTURE, COCKPIT, BOTH, VIEWER }
