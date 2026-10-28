package app.taxicom.commandos.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.taxicom.commandos.TaxiCommandosApp
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Uploads decisions and outcomes for reports. Card texts and addresses never leave the phone. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as TaxiCommandosApp).graph
        if (graph.sessions.session.value == null) return Result.success()
        val dao = graph.db.offers()
        while (true) {
            val batch = dao.unsynced()
            if (batch.isEmpty()) return Result.success()
            try {
                graph.api.uploadOffers(batch.map { it.toUpload() })
                dao.markSynced(batch.map { it.id })
            } catch (e: java.io.IOException) {
                return Result.retry()
            }
        }
    }

    companion object {
        private const val PERIODIC = "taxicom_sync_periodic"
        private const val NOW = "taxicom_sync_now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build(),
            )
        }
    }
}

fun OfferEntity.toUpload() = OfferUploadDto(
    id = id,
    platform = platform,
    product = product,
    capturedAt = Instant.ofEpochMilli(capturedAtMs).toString(),
    currency = currency,
    price = price,
    priceNet = net,
    pickupKm = pickupKm,
    pickupMin = pickupMin?.toDouble(),
    tripKm = tripKm,
    tripMin = tripMin?.toDouble(),
    score = e,
    verdict = verdict,
    outcome = outcome,
    decidedBy = decidedBy,
    isNight = isNight,
    isLong = isLong,
    onTrip = onTrip,
    tags = tags.split(',').filter { it.isNotBlank() },
)
