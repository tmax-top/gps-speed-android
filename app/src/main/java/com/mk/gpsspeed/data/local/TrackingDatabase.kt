package com.mk.gpsspeed.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.mk.gpsspeed.data.RideSessionRecord
import com.mk.gpsspeed.data.SpeedSample
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ride_sessions")
data class RideSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAt: Long,
    val durationMillis: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val averageSpeedKmh: Double,
)

@Entity(
    tableName = "speed_samples",
    foreignKeys = [
        ForeignKey(
            entity = RideSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class SpeedSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val elapsedMillis: Long,
    val recordedAt: Long,
    val speedKmh: Double,
    val accuracyMeters: Float,
)

data class RideSessionWithSamples(
    @Embedded val session: RideSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val samples: List<SpeedSampleEntity>,
)

@Dao
interface TrackingDao {
    @Transaction
    @Query("SELECT * FROM ride_sessions ORDER BY startedAt DESC")
    fun observeRideSessions(): Flow<List<RideSessionWithSamples>>

    @Insert
    suspend fun insertRideSession(session: RideSessionEntity): Long

    @Insert
    suspend fun insertSpeedSamples(samples: List<SpeedSampleEntity>)

    @Transaction
    suspend fun insertRideWithSamples(
        session: RideSessionEntity,
        samples: List<SpeedSampleEntity>,
    ) {
        val sessionId = insertRideSession(session)
        if (samples.isNotEmpty()) {
            insertSpeedSamples(samples.map { it.copy(sessionId = sessionId) })
        }
    }
}

@Database(
    entities = [RideSessionEntity::class, SpeedSampleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao

    companion object {
        @Volatile
        private var instance: TrackingDatabase? = null

        fun getInstance(context: Context): TrackingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackingDatabase::class.java,
                    "tracking.db",
                ).build().also { instance = it }
            }
        }
    }
}

fun RideSessionWithSamples.toRecord(): RideSessionRecord {
    return RideSessionRecord(
        id = session.id,
        startedAt = session.startedAt,
        durationMillis = session.durationMillis,
        distanceKm = session.distanceKm,
        maxSpeedKmh = session.maxSpeedKmh,
        averageSpeedKmh = session.averageSpeedKmh,
        speedSamples = samples
            .sortedBy { it.elapsedMillis }
            .map {
                SpeedSample(
                    elapsedMillis = it.elapsedMillis,
                    speedKmh = it.speedKmh,
                )
            },
    )
}
