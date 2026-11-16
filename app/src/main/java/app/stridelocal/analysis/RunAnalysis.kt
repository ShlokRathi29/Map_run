package app.stridelocal.analysis

import app.stridelocal.data.local.TrackPointEntity
import kotlin.math.*

data class PaceSplit(val index: Int, val distanceMeters: Double, val durationSeconds: Double) {
    val paceSecondsPerKm: Double get() = if (distanceMeters <= 0.0) 0.0 else durationSeconds * 1000.0 / distanceMeters
}

data class PersonalRecords(
    val longestRunMeters: Double = 0.0,
    val fastestKilometerSeconds: Double? = null,
    val fastestFiveKilometersSeconds: Double? = null,
)

fun personalRecords(
    runs: List<Pair<Double, List<PaceSplit>>>,
): PersonalRecords {
    val allFullKilometers = runs.flatMap { (_, splits) -> splits.filter { it.distanceMeters >= 999.5 } }
    val fiveKmWindows = runs.flatMap { (_, splits) ->
        splits.filter { it.distanceMeters >= 999.5 }.windowed(size = 5, step = 1, partialWindows = false)
            .map { window -> window.sumOf { it.durationSeconds } }
    }
    return PersonalRecords(
        longestRunMeters = runs.maxOfOrNull { it.first } ?: 0.0,
        fastestKilometerSeconds = allFullKilometers.minOfOrNull { it.durationSeconds },
        fastestFiveKilometersSeconds = fiveKmWindows.minOrNull(),
    )
}

/** Calculates distance splits from locally stored GPS points, without map matching. */
object RunAnalysis {
    fun kilometerSplits(points: List<TrackPointEntity>): List<PaceSplit> {
        if (points.size < 2) return emptyList()
        val result = mutableListOf<PaceSplit>()
        var splitDistance = 0.0
        var splitSeconds = 0.0
        var splitIndex = 1
        var prior = points.first()
        for (point in points.drop(1)) {
            val segmentDistance = distanceMeters(prior.latitude, prior.longitude, point.latitude, point.longitude)
            if (segmentDistance <= 0.5 || segmentDistance > 100.0) { prior = point; continue }
            var remainingDistance = segmentDistance
            var remainingSeconds = (point.activeElapsedNanos - prior.activeElapsedNanos).coerceAtLeast(0L) / 1_000_000_000.0
            while (remainingDistance > 0.0) {
                val needed = 1000.0 - splitDistance
                val partDistance = min(remainingDistance, needed)
                val partSeconds = remainingSeconds * partDistance / remainingDistance
                splitDistance += partDistance
                splitSeconds += partSeconds
                remainingDistance -= partDistance
                remainingSeconds -= partSeconds
                if (splitDistance >= 999.5) {
                    result += PaceSplit(splitIndex++, 1000.0, splitSeconds)
                    splitDistance = 0.0
                    splitSeconds = 0.0
                }
            }
            prior = point
        }
        if (splitDistance >= 25.0) result += PaceSplit(splitIndex, splitDistance, splitSeconds)
        return result
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
