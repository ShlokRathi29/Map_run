package app.stridelocal.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.stridelocal.data.local.HeartRateSampleEntity
import app.stridelocal.data.local.RunDao
import app.stridelocal.data.local.RunEntity
import java.time.Instant

object HealthConnectHeartRate {
    val readPermission = HealthPermission.getReadPermission(HeartRateRecord::class)

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun importForRun(context: Context, dao: RunDao, run: RunEntity): Int {
        val client = HealthConnectClient.getOrCreate(context)
        val samples = mutableListOf<HeartRateSampleEntity>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(run.startedAtEpochMs),
                        Instant.ofEpochMilli(run.endedAtEpochMs ?: System.currentTimeMillis()),
                    ),
                    pageToken = pageToken,
                ),
            )
            response.records.forEach { record ->
                record.samples.forEach { sample ->
                    samples += HeartRateSampleEntity(
                        runId = run.id,
                        sequence = samples.size.toLong(),
                        timestampEpochMs = sample.time.toEpochMilli(),
                        beatsPerMinute = sample.beatsPerMinute.toInt(),
                        source = "Health Connect",
                    )
                }
            }
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        dao.deleteHeartRateSource(run.id, "Health Connect")
        if (samples.isNotEmpty()) dao.insertHeartRateSamples(samples)
        return samples.size
    }
}
