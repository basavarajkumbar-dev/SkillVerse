package com.skillverse.app.domain

import com.skillverse.app.domain.model.Category
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.progression.AchievementEngine
import com.skillverse.app.domain.progression.AchievementStats
import com.skillverse.app.domain.progression.LevelCalculator
import com.skillverse.app.domain.progression.ProgressionConfig
import com.skillverse.app.domain.progression.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LevelCalculatorTest {
    @Test fun thresholdsGrowGradually() {
        assertEquals(0, LevelCalculator.xpToReachLevel(1))
        assertEquals(100, LevelCalculator.xpToReachLevel(2))
        assertEquals(250, LevelCalculator.xpToReachLevel(3))
        assertEquals(450, LevelCalculator.xpToReachLevel(4))
        for (level in 1..20) {
            val step = LevelCalculator.xpToReachLevel(level + 1) - LevelCalculator.xpToReachLevel(level)
            assertEquals(LevelCalculator.xpForStep(level), step)
            if (level > 1) assertTrue(step > LevelCalculator.xpForStep(level - 1))
        }
    }

    @Test fun levelForBoundaries() {
        assertEquals(1, LevelCalculator.levelFor(-5))
        assertEquals(1, LevelCalculator.levelFor(0))
        assertEquals(1, LevelCalculator.levelFor(99))
        assertEquals(2, LevelCalculator.levelFor(100))
        assertEquals(2, LevelCalculator.levelFor(249))
        assertEquals(3, LevelCalculator.levelFor(250))
    }

    @Test fun progressReportsEarnedAndRemaining() {
        val p = LevelCalculator.progress(130)
        assertEquals(2, p.level)
        assertEquals(30, p.xpIntoLevel)
        assertEquals(150, p.xpForNextLevel)
        assertEquals(120, p.xpRemaining)
        assertEquals(0.2f, p.fraction, 0.0001f)
    }

    @Test fun levelIsCapped() {
        val p = LevelCalculator.progress(Int.MAX_VALUE / 2)
        assertEquals(ProgressionConfig.MAX_LEVEL, p.level)
        assertTrue(p.isMaxLevel)
        assertEquals(1f, p.fraction, 0f)
    }

    @Test fun rewardsComeFromOneConfig() {
        assertEquals(10, ProgressionConfig.xpFor(ChallengeType.MULTIPLE_CHOICE))
        assertEquals(25, ProgressionConfig.xpFor(ChallengeType.CODE_PUZZLE))
        assertEquals(25, ProgressionConfig.xpFor(ChallengeType.FILL_BLANK))
        assertEquals(30, ProgressionConfig.xpFor(ChallengeType.IDENTIFY_BUG))
        assertEquals(30, ProgressionConfig.xpFor(ChallengeType.ARRANGE_STEPS))
        assertEquals(75, ProgressionConfig.BOSS_XP)
        assertEquals(100, ProgressionConfig.MASTERY_MILESTONE_XP)
    }
}

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test fun emptyHistoryHasNoStreak() {
        val s = StreakCalculator.compute(emptyList(), today)
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertFalse(s.activeToday)
    }

    @Test fun streakContinuesUntilTodayIsOver() {
        val dates = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        val s = StreakCalculator.compute(dates, today)
        assertEquals(3, s.current)
        assertFalse(s.activeToday)
    }

    @Test fun missedDayResetsCurrentButKeepsLongest() {
        val dates = (2L..6L).map { today.minusDays(it) } // five-day run ending two days ago
        val s = StreakCalculator.compute(dates, today)
        assertEquals(0, s.current)
        assertEquals(5, s.longest)
    }

    @Test fun duplicatesAndFutureDatesAreIgnored() {
        val dates = listOf(today, today, today.minusDays(1), today.plusDays(3))
        val s = StreakCalculator.compute(dates, today)
        assertEquals(2, s.current)
        assertEquals(2, s.longest)
    }

    @Test fun streakCrossesMonthAndYearBoundaries() {
        val newYear = LocalDate.of(2027, 1, 1)
        val s = StreakCalculator.compute(listOf(LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31), newYear), newYear)
        assertEquals(3, s.current)
    }

    @Test fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 10, 19), StreakCalculator.weekStart(today)) // today is a Sunday
        assertEquals(LocalDate.of(2026, 10, 26), StreakCalculator.weekStart(LocalDate.of(2026, 10, 26)))
        val dates = listOf(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21))
        assertEquals(2, StreakCalculator.activeDaysThisWeek(dates, today))
    }
}

class AchievementEngineTest {
    private val none = AchievementStats(0, emptyMap(), 0, 0, 0)

    @Test fun nothingEarnedOnFreshInstall() {
        assertTrue(AchievementEngine.earned(none).isEmpty())
    }

    @Test fun eachRequirementIsEnforced() {
        assertTrue(Achievement.FIRST_MISSION in AchievementEngine.earned(none.copy(completedChallenges = 1)))
        val python9 = none.copy(completedChallenges = 9, completedByCategory = mapOf(Category.PYTHON to 9))
        assertFalse(Achievement.PYTHON_EXPLORER in AchievementEngine.earned(python9))
        val python10 = python9.copy(completedByCategory = mapOf(Category.PYTHON to 10))
        assertTrue(Achievement.PYTHON_EXPLORER in AchievementEngine.earned(python10))
        val debug = none.copy(completedByCategory = mapOf(Category.DEBUGGING to 6, Category.LOGIC to 4))
        assertTrue(Achievement.DEBUG_DETECTIVE in AchievementEngine.earned(debug))
        assertTrue(Achievement.FIVE_DAY_LEARNER in AchievementEngine.earned(none.copy(longestStreak = 5)))
        assertFalse(Achievement.FIVE_DAY_LEARNER in AchievementEngine.earned(none.copy(longestStreak = 4)))
        val automation = none.copy(completedByCategory = mapOf(Category.TEST_AUTOMATION to 5, Category.API_TESTING to 3))
        assertTrue(Achievement.AUTOMATION_APPRENTICE in AchievementEngine.earned(automation))
        assertTrue(Achievement.BOSS_CHALLENGER in AchievementEngine.earned(none.copy(bossesDefeated = 1)))
        assertTrue(Achievement.MASTERY_MILESTONE in AchievementEngine.earned(none.copy(masteredSkills = 1)))
    }

    @Test fun alreadyUnlockedAreNotReturnedAgain() {
        val stats = none.copy(completedChallenges = 3, bossesDefeated = 1)
        val fresh = AchievementEngine.newlyEarned(stats, setOf(Achievement.FIRST_MISSION.name))
        assertEquals(listOf(Achievement.BOSS_CHALLENGER), fresh)
    }
}
