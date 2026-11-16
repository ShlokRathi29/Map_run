package app.stridelocal.analysis

import app.stridelocal.data.local.RoutePointEntity
import app.stridelocal.data.local.RunEntity
import app.stridelocal.data.local.TrackPointEntity
import kotlin.math.abs

data class LocalSegmentAttempt(val run: RunEntity, val durationSeconds: Long, val distanceMeters: Double)

/** Matches only the user's own route efforts by their start/end GPS locations. */
object SegmentComparison {
    fun match(route: List<RoutePointEntity>, run: RunEntity, points: List<TrackPointEntity>): LocalSegmentAttempt? {
        if (route.size < 2 || points.size < 2) return null
        val routeDistance = route.zipWithNext().sumOf { (a, b) ->
            RunAnalysis.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        if (routeDistance < 100.0) return null
        val first = route.first()
        val last = route.last()
        val forward = findOrderedAttempt(points, first.latitude, first.longitude, last.latitude, last.longitude)
        val reverse = findOrderedAttempt(points, last.latitude, last.longitude, first.latitude, first.longitude)
        val best = listOfNotNull(forward, reverse).minByOrNull { it.second }
        if (best == null || abs(best.first - routeDistance) / routeDistance > 0.5) return null
        return LocalSegmentAttempt(run, best.second, best.first)
    }

    private fun findOrderedAttempt(
        points: List<TrackPointEntity>, startLat: Double, startLon: Double, endLat: Double, endLon: Double,
    ): Pair<Double, Long>? {
        val start = points.indices.minByOrNull { i ->
            RunAnalysis.distanceMeters(points[i].latitude, points[i].longitude, startLat, startLon)
        } ?: return null
        val startDistance = RunAnalysis.distanceMeters(points[start].latitude, points[start].longitude, startLat, startLon)
        if (startDistance > MAX_ENDPOINT_DISTANCE_METERS) return null
        val end = (start + 1 until points.size).minByOrNull { i ->
            RunAnalysis.distanceMeters(points[i].latitude, points[i].longitude, endLat, endLon)
        } ?: return null
        val endDistance = RunAnalysis.distanceMeters(points[end].latitude, points[end].longitude, endLat, endLon)
        if (endDistance > MAX_ENDPOINT_DISTANCE_METERS) return null
        val elapsedNanos = points[end].activeElapsedNanos - points[start].activeElapsedNanos
        if (elapsedNanos <= 0L) return null
        var distance = 0.0
        for (i in start + 1..end) distance += RunAnalysis.distanceMeters(
            points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude,
        )
        return distance to (elapsedNanos / 1_000_000_000L)
    }

    private const val MAX_ENDPOINT_DISTANCE_METERS = 100.0
}
