package com.skillverse.app.domain

import com.skillverse.app.TestContent
import com.skillverse.app.domain.analytics.AnalyticsCalculator
import com.skillverse.app.domain.analytics.CategoryAccuracy
import com.skillverse.app.domain.analytics.DayActivity
import com.skillverse.app.domain.missions.MissionContext
import com.skillverse.app.domain.missions.MissionGenerator
import com.skillverse.app.domain.missions.MissionSlot
import com.skillverse.app.domain.model.Category
import com.skillverse.app.domain.model.Difficulty
import com.skillverse.app.domain.progression.ProgressionConfig
import com.skillverse.app.domain.skills.NodeState
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MissionGeneratorTest {
    private val c = TestContent.curriculum
    private val day = LocalDate.of(2026, 10, 25)

    private fun ctx(
        date: LocalDate = day,
        completed: Set<String> = emptySet(),
        recent: Set<String> = emptySet(),
        accuracy: Double? = null,
        attempts: Int = 0,
    ) = MissionContext(date, SkillTreeCalculator.unlockedSkillIds(c, completed), completed, recent, accuracy, attempts)

    @Test fun freshInstallGetsThreeBalancedMissionsFromTheFirstSkill() {
        val missions = MissionGenerator.generate(c, ctx())
        assertEquals(listOf(MissionSlot.CODING, MissionSlot.QUIZ, MissionSlot.DEBUG), missions.map { it.slot })
        assertEquals(3, missions.map { it.challenge.id }.distinct().size)
        missions.forEach {
            assertEquals("python_foundations", it.challenge.skillId)
            assertTrue(it.challenge.type in it.slot.types)
        }
        val seconds = missions.sumOf { it.challenge.estimatedSeconds }
        assertTrue("session $seconds s", seconds in ProgressionConfig.DAILY_SESSION_MIN_SECONDS..ProgressionConfig.DAILY_SESSION_MAX_SECONDS)
    }

    @Test fun sameInputsGiveSameMissions() {
        assertEquals(MissionGenerator.generate(c, ctx()), MissionGenerator.generate(c, ctx()))
    }

    @Test fun recentlySeenAndCompletedAreAvoidedWhenAlternativesExist() {
        val first = MissionGenerator.generate(c, ctx()).map { it.challenge.id }.toSet()
        val next = MissionGenerator.generate(c, ctx(date = day.plusDays(1), recent = first)).map { it.challenge.id }.toSet()
        assertTrue("repeated $next", next.intersect(first).isEmpty())
        val completedMany = c.challengesForSkill("python_foundations").map { it.id }.toSet()
        val after = MissionGenerator.generate(c, ctx(completed = completedMany))
        // With the first skill fully done, the second skill is unlocked and new work is preferred.
        assertTrue(after.any { it.challenge.id !in completedMany })
    }

    @Test fun difficultyAdaptsToPerformance() {
        assertEquals(Difficulty.EASY, MissionGenerator.targetDifficulty(null, 0))
        assertEquals(Difficulty.EASY, MissionGenerator.targetDifficulty(1.0, 3))
        assertEquals(Difficulty.HARD, MissionGenerator.targetDifficulty(0.9, 20))
        assertEquals(Difficulty.MEDIUM, MissionGenerator.targetDifficulty(0.6, 20))
        assertEquals(Difficulty.EASY, MissionGenerator.targetDifficulty(0.3, 20))
    }

    @Test fun noUnlockedSkillsMeansNoMissions() {
        val empty = MissionContext(day, emptySet(), emptySet(), emptySet(), null, 0)
        assertTrue(MissionGenerator.generate(c, empty).isEmpty())
    }

    @Test fun weeklyBossIsLockedUntilItsSkillsUnlockAndStableWithinAWeek() {
        val monday = LocalDate.of(2026, 10, 19)
        assertNull(MissionGenerator.bossForWeek(c, setOf("python_foundations"), monday))
        val unlocked = setOf("python_foundations", "data_structures")
        val boss = MissionGenerator.bossForWeek(c, unlocked, monday)
        assertNotNull(boss)
        assertEquals("boss-syntax-sentinel", boss!!.id)
        assertEquals(boss, MissionGenerator.bossForWeek(c, unlocked, monday))
    }
}

class SkillTreeCalculatorTest {
    private val c = TestContent.curriculum

    @Test fun onlyTheRootIsUnlockedOnFreshInstall() {
        val nodes = SkillTreeCalculator.compute(c, emptySet())
        assertEquals(10, nodes.size)
        assertEquals(NodeState.AVAILABLE, nodes.first().state)
        assertTrue(nodes.drop(1).all { it.state == NodeState.LOCKED && it.unlockHint != null })
        assertTrue(nodes[1].unlockHint!!.contains("Python Foundations"))
    }

    @Test fun reachingTheThresholdUnlocksTheNextNode() {
        val root = c.challengesForSkill("python_foundations").map { it.id }
        val needed = (c.unlockThresholdPercent * root.size + 99) / 100
        val almost = SkillTreeCalculator.compute(c, root.take(needed - 1).toSet())
        assertEquals(NodeState.LOCKED, almost[1].state)
        val enough = SkillTreeCalculator.compute(c, root.take(needed).toSet())
        assertEquals(NodeState.COMPLETED, enough[0].state)
        assertEquals(NodeState.AVAILABLE, enough[1].state)
        val all = SkillTreeCalculator.compute(c, root.toSet())
        assertEquals(NodeState.MASTERED, all[0].state)
        assertEquals(100, all[0].masteryPercent)
    }

    @Test fun masteryHandlesEmptySkills() {
        assertEquals(0, SkillTreeCalculator.masteryPercent(0, 0))
        assertEquals(33, SkillTreeCalculator.masteryPercent(1, 3))
    }
}

class AnalyticsCalculatorTest {
    @Test fun percentIsSafeForZero() {
        assertEquals(0, AnalyticsCalculator.percent(0, 0))
        assertEquals(0, AnalyticsCalculator.percent(5, 0))
        assertEquals(67, AnalyticsCalculator.percent(2, 3))
        assertEquals(100, AnalyticsCalculator.percent(3, 3))
    }

    @Test fun weakTopicsNeedEnoughAttempts() {
        val stats = listOf(
            CategoryAccuracy(Category.PYTHON, attempts = 10, correct = 9),
            CategoryAccuracy(Category.API_TESTING, attempts = 5, correct = 1),
            CategoryAccuracy(Category.GIT, attempts = 2, correct = 0),
            CategoryAccuracy(Category.LINUX, attempts = 4, correct = 2),
        )
        val weak = AnalyticsCalculator.weakTopics(stats)
        assertEquals(listOf(Category.API_TESTING, Category.LINUX), weak.map { it.category })
        assertTrue(AnalyticsCalculator.weakTopics(emptyList()).isEmpty())
    }

    @Test fun activitySeriesFillsGaps() {
        val today = LocalDate.of(2026, 10, 25)
        val series = AnalyticsCalculator.activitySeries(listOf(DayActivity(today.minusDays(2), 4, 3)), today, 7)
        assertEquals(7, series.size)
        assertEquals(today, series.last().date)
        assertEquals(4, series[4].attempts)
        assertEquals(0, series.sumOf { it.correct } - 3)
    }

    @Test fun weeklyGoalIsCapped() {
        assertEquals(100, AnalyticsCalculator.weeklyGoalPercent(7, 5))
        assertEquals(60, AnalyticsCalculator.weeklyGoalPercent(3, 5))
    }
}

class ReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun schedulesLaterToday() {
        val d = ReminderScheduler.delayUntil(LocalDateTime.of(2026, 10, 25, 18, 30), zone, 19, 0)
        assertEquals(Duration.ofMinutes(30), d)
    }

    @Test fun schedulesTomorrowWhenTimePassed() {
        val d = ReminderScheduler.delayUntil(LocalDateTime.of(2026, 10, 25, 19, 0), zone, 19, 0)
        assertEquals(Duration.ofHours(24), d)
    }

    @Test fun handlesDaylightSavingGaps() {
        val ny = ZoneId.of("America/New_York")
        // 2027-03-14 02:00 does not exist in New York; the delay must still be positive and finite.
        val d = ReminderScheduler.delayUntil(LocalDateTime.of(2027, 3, 14, 1, 0), ny, 2, 30)
        assertFalse(d.isNegative)
        assertTrue(d <= Duration.ofHours(2))
    }
}
