package com.skillverse.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skillverse.app.TestContent
import com.skillverse.app.core.time.FakeTimeProvider
import com.skillverse.app.data.db.SkillVerseDatabase
import com.skillverse.app.data.progress.ProgressRepository
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.progression.ProgressionConfig
import com.skillverse.app.domain.validation.LocalExerciseValidationService
import com.skillverse.app.domain.validation.UserAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProgressRepositoryTest {
    private val c = TestContent.curriculum
    private lateinit var db: SkillVerseDatabase
    private lateinit var time: FakeTimeProvider
    private lateinit var repo: ProgressRepository
    private val day = LocalDate.of(2026, 10, 25)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SkillVerseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        time = FakeTimeProvider(day.atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"))
        repo = ProgressRepository(db, LocalExerciseValidationService(Dispatchers.Unconfined), time)
        runBlocking { repo.syncContent(c) }
    }

    @After fun tearDown() = db.close()

    private fun correct(ch: Challenge): UserAnswer = when (val s = ch.answer) {
        is AnswerSpec.Choice -> UserAnswer.Choice(s.correctIndex)
        is AnswerSpec.Text -> UserAnswer.Text(s.accepted.first())
        is AnswerSpec.Order -> UserAnswer.Order(s.steps)
    }

    private fun wrong(ch: Challenge): UserAnswer = when (val s = ch.answer) {
        is AnswerSpec.Choice -> UserAnswer.Choice((s.correctIndex + 1) % ch.options.size)
        is AnswerSpec.Text -> UserAnswer.Text("definitely wrong")
        is AnswerSpec.Order -> UserAnswer.Order(s.steps.reversed())
    }

    private suspend fun solve(id: String, context: AttemptContext = AttemptContext.PRACTICE) =
        repo.submit(c, c.byId.getValue(id), correct(c.byId.getValue(id)), context, 1000)

    @Test fun freshInstallHasEmptyProgress() = runBlocking {
        assertEquals(0, repo.observeTotalXp().first())
        assertTrue(repo.observeCompletedIds().first().isEmpty())
        assertEquals(0, repo.observeTotals().first().total)
        assertTrue(repo.observeAchievements().first().isEmpty())
        val row = repo.observeProgressRow().first()
        assertNotNull(row)
        assertEquals(1, row!!.level)
    }

    @Test fun correctAnswerAwardsXpExactlyOnce() = runBlocking {
        val ch = c.byId.getValue("py-001")
        val first = solve("py-001")
        assertTrue(first.correct)
        assertEquals(ch.xp, first.xpAwarded)
        assertFalse(first.alreadyEarned)
        assertEquals(listOf(Achievement.FIRST_MISSION), first.newAchievements)

        val again = solve("py-001")
        assertTrue(again.correct)
        assertEquals(0, again.xpAwarded)
        assertTrue(again.alreadyEarned)
        assertTrue(again.newAchievements.isEmpty())
        assertEquals(ch.xp, repo.observeTotalXp().first())
        assertEquals(2, repo.observeTotals().first().total)
    }

    @Test fun wrongAnswerGoesToRevisionAndCorrectRemovesIt() = runBlocking {
        val ch = c.byId.getValue("py-002")
        val r = repo.submit(c, ch, wrong(ch), AttemptContext.PRACTICE, 500)
        assertFalse(r.correct)
        assertEquals(0, r.xpAwarded)
        repo.submit(c, ch, wrong(ch), AttemptContext.PRACTICE, 500)
        val queue = repo.observeRevisionQueue().first()
        assertEquals(1, queue.size)
        assertEquals(2, queue.single().wrongCount)
        solve("py-002", AttemptContext.REVIEW)
        assertTrue(repo.observeRevisionQueue().first().isEmpty())
        val totals = repo.observeTotals().first()
        assertEquals(3, totals.total)
        assertEquals(1, totals.correct)
    }

    @Test fun dailyAssignmentsAreStableAndCompletable() = runBlocking {
        val first = repo.ensureDailyMissions(c, day)
        assertEquals(3, first.size)
        val second = repo.ensureDailyMissions(c, day)
        assertEquals(first.map { it.challengeId }, second.map { it.challengeId })

        val mission = first.first()
        val outcome = solve(mission.challengeId, AttemptContext.DAILY)
        assertTrue(outcome.completedDailyMission)
        val after = repo.observeMissions(day).first()
        assertNotNull(after.first { it.challengeId == mission.challengeId }.completedAtEpochMs)
        // Re-solving the same mission neither re-completes nor re-awards.
        val again = solve(mission.challengeId, AttemptContext.DAILY)
        assertFalse(again.completedDailyMission)
        assertEquals(0, again.xpAwarded)
    }

    @Test fun nextDayGetsNewMissionsAndOldDayIsUnchanged() = runBlocking {
        val today = repo.ensureDailyMissions(c, day).map { it.challengeId }
        val tomorrow = repo.ensureDailyMissions(c, day.plusDays(1)).map { it.challengeId }
        assertTrue(today.intersect(tomorrow.toSet()).isEmpty())
        assertEquals(today, repo.ensureDailyMissions(c, day).map { it.challengeId })
    }

    @Test fun masteryMilestoneAndUnlocksAreAwardedOnce() = runBlocking {
        val root = c.challengesForSkill("python_foundations")
        var bonus = 0
        root.forEach { bonus += repo.submit(c, it, correct(it), AttemptContext.PRACTICE, 1).masteryBonusXp }
        assertEquals(ProgressionConfig.MASTERY_MILESTONE_XP, bonus)
        val expected = root.sumOf { it.xp } + ProgressionConfig.MASTERY_MILESTONE_XP
        assertEquals(expected, repo.observeTotalXp().first())
        val again = solve(root.first().id)
        assertEquals(0, again.masteryBonusXp)
        assertTrue(repo.observeAchievements().first().any { it.id == Achievement.MASTERY_MILESTONE.name })
    }

    @Test fun levelUpIsReported() = runBlocking {
        var leveled = false
        for (ch in c.challengesForSkill("python_foundations")) {
            val o = repo.submit(c, ch, correct(ch), AttemptContext.PRACTICE, 1)
            if (o.leveledUp) leveled = true
        }
        assertTrue(leveled)
        assertTrue(repo.observeProgressRow().first()!!.level >= 2)
    }

    @Test fun weeklyBossAppearsWhenEligibleAndPaysOncePerWeek() = runBlocking {
        assertNull(repo.ensureWeeklyBoss(c, day))
        // Unlock data_structures by completing the root skill past the threshold.
        c.challengesForSkill("python_foundations").forEach { repo.submit(c, it, correct(it), AttemptContext.PRACTICE, 1) }
        val boss = repo.ensureWeeklyBoss(c, day)
        assertNotNull(boss)
        val first = repo.completeBoss(c, boss!!.challengeId, day)
        assertTrue(first.awarded)
        assertEquals(ProgressionConfig.BOSS_XP, first.xpAwarded)
        assertTrue(Achievement.BOSS_CHALLENGER in first.newAchievements)
        val second = repo.completeBoss(c, boss.challengeId, day)
        assertFalse(second.awarded)
        assertEquals(0, second.xpAwarded)
        assertTrue(repo.observeBossDefeated(boss.challengeId, day).first())
        assertEquals(1, repo.observeBossCount().first())
    }

    @Test fun streakSurvivesDayBoundaryAndLongestIsKept() = runBlocking {
        for (offset in 0L..4L) {
            time.setDate(day.plusDays(offset))
            val ch = c.challengesForSkill("python_foundations")[offset.toInt()]
            repo.submit(c, ch, correct(ch), AttemptContext.DAILY, 1)
        }
        val achievements = repo.observeAchievements().first().map { it.id }
        assertTrue(Achievement.FIVE_DAY_LEARNER.name in achievements)
        time.setDate(day.plusDays(8)) // missed days
        val ch = c.challengesForSkill("python_foundations")[5]
        repo.submit(c, ch, correct(ch), AttemptContext.PRACTICE, 1)
        val row = repo.observeProgressRow().first()!!
        assertEquals(5, row.longestStreak)
        assertTrue(Achievement.FIVE_DAY_LEARNER.name in repo.observeAchievements().first().map { it.id })
    }

    @Test fun timezoneChangeUsesTheNewLocalDate() = runBlocking {
        // 2026-10-25 20:00 UTC is already 2026-10-26 in Kolkata.
        time.zoneId = ZoneId.of("UTC")
        time.setDate(day, hour = 20)
        time.zoneId = ZoneId.of("Asia/Kolkata")
        assertEquals(day.plusDays(1), time.today())
        val ch = c.byId.getValue("py-001")
        repo.submit(c, ch, correct(ch), AttemptContext.PRACTICE, 1)
        assertTrue(day.plusDays(1) in repo.observeActiveDates().first())
    }

    @Test fun exportContainsRecordsAndResetClearsEverything() = runBlocking {
        solve("py-001")
        val json = repo.exportJson(UserSettings(displayName = "Asha"), "1.0.0", c.contentVersion)
        assertTrue(json.contains("\"py-001\""))
        assertTrue(json.contains("\"Asha\""))
        assertTrue(json.contains("skillverse-progress"))
        repo.resetAll(c)
        assertEquals(0, repo.observeTotalXp().first())
        assertTrue(repo.observeCompletedIds().first().isEmpty())
        assertNotNull(repo.observeProgressRow().first())
    }

    @Test fun categoryAccuracyJoinsWithChallengeRefs() = runBlocking {
        solve("py-001")
        val api = c.byId.getValue("api-001")
        repo.submit(c, api, wrong(api), AttemptContext.PRACTICE, 1)
        val stats = repo.observeCategoryAccuracy().first().associateBy { it.category.name }
        assertEquals(100, stats.getValue("PYTHON").accuracyPercent)
        assertEquals(0, stats.getValue("API_TESTING").accuracyPercent)
    }
}
