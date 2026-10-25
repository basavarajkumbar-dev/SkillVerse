package com.skillverse.app.domain.analytics

import com.skillverse.app.domain.model.Category
import java.time.LocalDate

data class CategoryAccuracy(val category: Category, val attempts: Int, val correct: Int) {
    val accuracyPercent: Int get() = AnalyticsCalculator.percent(correct, attempts)
}

data class DayActivity(val date: LocalDate, val attempts: Int, val correct: Int)

data class WeakTopic(val category: Category, val accuracyPercent: Int, val attempts: Int)

object AnalyticsCalculator {
    /** Minimum attempts before a category can be called weak, so one wrong answer is not a verdict. */
    const val WEAK_MIN_ATTEMPTS = 3
    const val WEAK_THRESHOLD_PERCENT = 60

    /** Integer percentage that is safe for a zero denominator. */
    fun percent(part: Int, whole: Int): Int = if (whole <= 0) 0 else ((part * 100.0) / whole).toInt().coerceIn(0, 100)

    fun weakTopics(stats: List<CategoryAccuracy>): List<WeakTopic> = stats
        .filter { it.attempts >= WEAK_MIN_ATTEMPTS && it.accuracyPercent < WEAK_THRESHOLD_PERCENT }
        .sortedWith(compareBy<CategoryAccuracy> { it.accuracyPercent }.thenByDescending { it.attempts })
        .map { WeakTopic(it.category, it.accuracyPercent, it.attempts) }

    /** Returns exactly [days] entries ending at [today], filling days without activity with zeros. */
    fun activitySeries(records: List<DayActivity>, today: LocalDate, days: Int): List<DayActivity> {
        val byDate = records.associateBy { it.date }
        return (days - 1 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            byDate[d] ?: DayActivity(d, 0, 0)
        }
    }

    fun weeklyGoalPercent(activeDaysThisWeek: Int, goalDays: Int): Int =
        percent(activeDaysThisWeek.coerceAtMost(goalDays), goalDays)
}
