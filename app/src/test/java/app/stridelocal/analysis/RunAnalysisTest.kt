package app.stridelocal.analysis

import app.stridelocal.data.local.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class RunAnalysisTest {
    @Test
    fun haversineDistanceIsZeroAtSamePointAndReasonableForOneDegree() {
        assertEquals(0.0, RunAnalysis.distanceMeters(0.0, 0.0, 0.0, 0.0), 0.001)
        assertEquals(111_195.0, RunAnalysis.distanceMeters(0.0, 0.0, 0.0, 1.0), 20.0)
    }

    @Test
    fun kilometerSplitsKeepFullKilometerAndTrailingDistance() {
        val stepDegrees = Math.toDegrees(40.0 / 6_371_000.0)
        val points = (0..30).map { index ->
            TrackPointEntity(
                runId = 1L,
                sequence = index.toLong(),
                latitude = index * stepDegrees,
                longitude = 0.0,
                altitudeMeters = null,
                accuracyMeters = 5f,
                activeElapsedNanos = index * 40_000_000_000L,
                timestampEpochMs = index * 40_000L,
            )
        }

        val splits = RunAnalysis.kilometerSplits(points)

        assertEquals(2, splits.size)
        assertEquals(1_000.0, splits[0].distanceMeters, 1.0)
        assertEquals(1_000.0, splits[0].durationSeconds, 1.0)
        assertEquals(200.0, splits[1].distanceMeters, 1.0)
        assertEquals(200.0, splits[1].durationSeconds, 1.0)
    }

    @Test
    fun personalRecordsUseCompletedKilometerSegmentsAndFullFiveKmWindows() {
        val records = personalRecords(
            listOf(
                5_300.0 to listOf(
                    PaceSplit(1, 1_000.0, 310.0),
                    PaceSplit(2, 1_000.0, 305.0),
                    PaceSplit(3, 1_000.0, 300.0),
                    PaceSplit(4, 1_000.0, 295.0),
                    PaceSplit(5, 1_000.0, 290.0),
                    PaceSplit(6, 300.0, 120.0),
                ),
                8_000.0 to listOf(PaceSplit(1, 1_000.0, 320.0)),
            ),
        )

        assertEquals(8_000.0, records.longestRunMeters, 0.0)
        assertEquals(290.0, records.fastestKilometerSeconds!!, 0.0)
        assertEquals(1_500.0, records.fastestFiveKilometersSeconds!!, 0.0)
    }
}
