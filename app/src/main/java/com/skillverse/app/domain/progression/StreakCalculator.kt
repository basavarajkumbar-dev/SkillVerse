package com.skillverse.app.domain.progression

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class StreakInfo(
    /** Consecutive active days ending today, or ending yesterday if today has no activity yet. */
    val current: Int,
    val longest: Int,
    val activeToday: Boolean,
)

/**
 * Streaks are derived from the set of local dates with at least one correct answer.
 * A missed day resets only the current streak; XP, levels, achievements and the longest
 * streak are never reduced.
 */
object StreakCalculator {
    fun compute(activeDates: Collection<LocalDate>, today: LocalDate): StreakInfo {
        val dates = activeDates.toSortedSet()
        val activeToday = today in dates
        val anchor = when {
            activeToday -> today
            today.minusDays(1) in dates -> today.minusDays(1)
            else -> null
        }
        var current = 0
        if (anchor != null) {
            var d: LocalDate = anchor
            while (d in dates) {
                current++
                d = d.minusDays(1)
            }
        }
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in dates) {
            if (d.isAfter(today)) continue // ignore dates from a clock that was set ahead
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }
        return StreakInfo(current = current, longest = maxOf(longest, current), activeToday = activeToday)
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Number of distinct active days in the Monday-to-Sunday week containing [today]. */
    fun activeDaysThisWeek(activeDates: Collection<LocalDate>, today: LocalDate): Int {
        val start = weekStart(today)
        val end = start.plusDays(6)
        return activeDates.toSet().count { !it.isBefore(start) && !it.isAfter(end) }
    }
}
