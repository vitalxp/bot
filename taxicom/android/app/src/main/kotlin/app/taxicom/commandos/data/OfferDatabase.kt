package app.taxicom.commandos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offers", indices = [Index("localDate"), Index("synced")])
data class OfferEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val product: String?,
    val capturedAtMs: Long,
    val localDate: String,
    val localHour: Int,
    val currency: String,
    val price: Double,
    val net: Double,
    val pickupMin: Int?,
    val pickupKm: Double?,
    val tripMin: Int?,
    val tripKm: Double?,
    val pickupAddress: String?,
    val dropoffAddress: String?,
    val e: Double,
    val eStar: Double,
    val rKm: Double,
    val rH: Double,
    val verdict: String,
    val outcome: String,
    val decidedBy: String?,
    val isNight: Boolean,
    val isLong: Boolean,
    val onTrip: Boolean,
    val tags: String,
    val synced: Boolean = false,
)

@Dao
interface OfferDao {
    @Upsert
    suspend fun upsert(offer: OfferEntity)

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun get(id: String): OfferEntity?

    @Query("SELECT * FROM offers WHERE localDate = :date ORDER BY capturedAtMs DESC")
    fun forDate(date: String): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE localDate BETWEEN :from AND :to ORDER BY capturedAtMs DESC")
    fun forRange(from: String, to: String): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE synced = 0 ORDER BY capturedAtMs LIMIT 200")
    suspend fun unsynced(): List<OfferEntity>

    @Query("UPDATE offers SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT e FROM offers WHERE capturedAtMs > :sinceMs AND isNight = :night ORDER BY capturedAtMs DESC LIMIT 500")
    suspend fun recentScores(sinceMs: Long, night: Boolean): List<Double>

    @Query("DELETE FROM offers")
    suspend fun clear()
}

@Database(entities = [OfferEntity::class], version = 1, exportSchema = true)
abstract class OfferDatabase : RoomDatabase() {
    abstract fun offers(): OfferDao

    companion object {
        fun create(context: Context): OfferDatabase =
            Room.databaseBuilder(context, OfferDatabase::class.java, "taxicom.db").build()
    }
}
