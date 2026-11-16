package app.stridelocal.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
)

@Entity(tableName = "track_points", primaryKeys = ["runId", "sequence"])
data class TrackPointEntity(
    val runId: Long,
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val activeElapsedNanos: Long,
    val timestampEpochMs: Long,
)

@Entity(tableName = "laps", primaryKeys = ["runId", "sequence"])
data class LapEntity(
    val runId: Long,
    val sequence: Int,
    val startedAtEpochMs: Long,
    val elapsedSeconds: Long,
    val distanceMeters: Double,
)

@Entity(tableName = "heart_rate_samples", primaryKeys = ["runId", "sequence"])
data class HeartRateSampleEntity(
    val runId: Long,
    val sequence: Long,
    val timestampEpochMs: Long,
    val beatsPerMinute: Int,
    val source: String,
)

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long,
    val distanceMeters: Double,
)

@Entity(tableName = "route_points", primaryKeys = ["routeId", "sequence"])
data class RoutePointEntity(
    val routeId: Long,
    val sequence: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
)

@Entity(tableName = "training_goals")
data class TrainingGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val metric: String,
    val targetValue: Double,
    val period: String,
    val createdAtEpochMs: Long,
    val active: Boolean = true,
)

@Entity(tableName = "local_challenges")
data class LocalChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetMeters: Double,
    val startsAtEpochMs: Long,
    val endsAtEpochMs: Long,
)

@Dao
interface RunDao {
    @Query("SELECT * FROM runs WHERE endedAtEpochMs IS NOT NULL ORDER BY startedAtEpochMs DESC") fun observeAll(): Flow<List<RunEntity>>
    @Insert suspend fun insertRun(run: RunEntity): Long
    @Update suspend fun updateRun(run: RunEntity)
    @Insert suspend fun insertPoint(point: TrackPointEntity)
    @Insert suspend fun insertLap(lap: LapEntity)
    @Insert suspend fun insertHeartRateSample(sample: HeartRateSampleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHeartRateSamples(samples: List<HeartRateSampleEntity>)
    @Query("DELETE FROM heart_rate_samples WHERE runId = :runId AND source = :source") suspend fun deleteHeartRateSource(runId: Long, source: String)
    @Insert suspend fun insertRoute(route: SavedRouteEntity): Long
    @Insert suspend fun insertRoutePoints(points: List<RoutePointEntity>)
    @Insert suspend fun insertGoal(goal: TrainingGoalEntity)
    @Insert suspend fun insertChallenge(challenge: LocalChallengeEntity)
    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY sequence") suspend fun pointsForRun(runId: Long): List<TrackPointEntity>
    @Query("SELECT * FROM laps WHERE runId = :runId ORDER BY sequence") suspend fun lapsForRun(runId: Long): List<LapEntity>
    @Query("SELECT * FROM heart_rate_samples WHERE runId = :runId ORDER BY timestampEpochMs") suspend fun heartRateForRun(runId: Long): List<HeartRateSampleEntity>
    @Query("SELECT * FROM saved_routes ORDER BY name") fun observeRoutes(): Flow<List<SavedRouteEntity>>
    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY sequence") suspend fun pointsForRoute(routeId: Long): List<RoutePointEntity>
    @Query("SELECT * FROM training_goals WHERE active = 1 ORDER BY createdAtEpochMs DESC") fun observeGoals(): Flow<List<TrainingGoalEntity>>
    @Query("SELECT * FROM local_challenges WHERE endsAtEpochMs >= :now ORDER BY endsAtEpochMs") fun observeChallenges(now: Long): Flow<List<LocalChallengeEntity>>
}

@Database(
    entities = [RunEntity::class, TrackPointEntity::class, LapEntity::class, HeartRateSampleEntity::class, SavedRouteEntity::class,
        RoutePointEntity::class, TrainingGoalEntity::class, LocalChallengeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao

    companion object {
        @Volatile private var instance: StrideDatabase? = null
        fun getInstance(context: android.content.Context): StrideDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StrideDatabase::class.java, "stride-local.db")
                .build().also { instance = it }
        }
    }
}
