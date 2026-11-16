package app.stridelocal.analysis

import app.stridelocal.data.local.RunEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object TrainingProgress {
    fun startOfPeriod(period: String, nowEpochMs: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val start = when (period) {
            "month" -> now.withDayOfMonth(1)
            else -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun distanceSince(runs: List<RunEntity>, startEpochMs: Long, endEpochMs: Long = Long.MAX_VALUE): Double =
        runs.asSequence().filter { it.startedAtEpochMs in startEpochMs until endEpochMs }.sumOf { it.distanceMeters }

    fun challengeDistance(runs: List<RunEntity>, startsAtEpochMs: Long, endsAtEpochMs: Long): Double =
        distanceSince(runs, startsAtEpochMs, endsAtEpochMs + 1)
}
