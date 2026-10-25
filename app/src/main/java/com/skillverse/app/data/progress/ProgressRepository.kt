package com.skillverse.app.data.progress

import androidx.room.withTransaction
import com.skillverse.app.core.time.TimeProvider
import com.skillverse.app.data.db.AchievementEntity
import com.skillverse.app.data.db.AttemptEntity
import com.skillverse.app.data.db.ChallengeRefEntity
import com.skillverse.app.data.db.DailyAssignmentEntity
import com.skillverse.app.data.db.RevisionEntity
import com.skillverse.app.data.db.SkillMasteryEntity
import com.skillverse.app.data.db.SkillVerseDatabase
import com.skillverse.app.data.db.UserProgressEntity
import com.skillverse.app.data.db.XpTransactionEntity
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.analytics.CategoryAccuracy
import com.skillverse.app.domain.analytics.DayActivity
import com.skillverse.app.domain.missions.MissionContext
import com.skillverse.app.domain.missions.MissionGenerator
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Category
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.progression.AchievementEngine
import com.skillverse.app.domain.progression.AchievementStats
import com.skillverse.app.domain.progression.LevelCalculator
import com.skillverse.app.domain.progression.ProgressionConfig
import com.skillverse.app.domain.progression.StreakCalculator
import com.skillverse.app.domain.skills.NodeState
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.domain.validation.CodeExecutionService
import com.skillverse.app.domain.validation.ExecutionRequest
import com.skillverse.app.domain.validation.ExecutionResult
import com.skillverse.app.domain.validation.UserAnswer
import com.skillverse.app.domain.validation.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class SubmissionOutcome(
    val validation: ValidationResult,
    val execution: ExecutionResult,
    /** XP granted for this challenge now (0 if wrong or already earned before). */
    val xpAwarded: Int,
    /** True when the answer was correct but the XP had been earned on an earlier attempt. */
    val alreadyEarned: Boolean,
    val masteryBonusXp: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val totalXpAfter: Int,
    val newAchievements: List<Achievement>,
    val completedDailyMission: Boolean,
) {
    val correct: Boolean get() = validation.correct
    val leveledUp: Boolean get() = levelAfter > levelBefore
}

data class BossOutcome(
    val awarded: Boolean,
    val xpAwarded: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val newAchievements: List<Achievement>,
)

/**
 * All reads and writes of learner progress. Every reward path runs inside a Room transaction and
 * relies on unique XP event keys, so progress stays consistent and duplicate awards are impossible.
 */
class ProgressRepository(
    private val db: SkillVerseDatabase,
    private val executionService: CodeExecutionService,
    private val time: TimeProvider,
) {
    private val attempts = db.attemptDao()
    private val assignments = db.assignmentDao()
    private val xp = db.xpDao()
    private val achievementsDao = db.achievementDao()
    private val revision = db.revisionDao()
    private val progress = db.progressDao()
    private val mastery = db.skillMasteryDao()
    private val refs = db.challengeRefDao()
    private val writeLock = Mutex()

    // ---------------------------------------------------------------- startup

    /** Registers bundled challenges, ensures the progress row exists and refreshes mastery. Idempotent. */
    suspend fun syncContent(curriculum: Curriculum) = writeLock.withLock {
        db.withTransaction {
            refs.upsertAll(curriculum.challenges.map {
                ChallengeRefEntity(it.id, it.skillId, it.category.name, it.type.name, it.difficulty.name, curriculum.contentVersion, retired = false)
            })
            refs.retireMissing(curriculum.challenges.map { it.id })
            val nowMs = time.now().toEpochMilli()
            if (progress.get() == null) {
                progress.upsert(UserProgressEntity(createdAtEpochMs = nowMs, updatedAtEpochMs = nowMs))
            }
            refreshMastery(curriculum, attempts.completedIds().toSet(), nowMs)
            refreshProgressRow(nowMs, null)
        }
    }

    // ---------------------------------------------------------------- daily missions & boss

    /** Returns today's stored missions, generating and storing them once if absent. */
    suspend fun ensureDailyMissions(curriculum: Curriculum, date: LocalDate): List<DailyAssignmentEntity> = writeLock.withLock {
        db.withTransaction {
            val key = date.toString()
            val existing = assignments.missionsFor(key)
            if (existing.isNotEmpty()) return@withTransaction existing
            val completed = attempts.completedIds().toSet()
            val since = date.minusDays(ProgressionConfig.REPEAT_AVOIDANCE_DAYS).toString()
            val recent = (attempts.attemptedSince(since) + assignments.assignedSince(since)).toSet()
            val results = attempts.recentResults(ProgressionConfig.DIFFICULTY_WINDOW)
            val ctx = MissionContext(
                date = date,
                unlockedSkillIds = SkillTreeCalculator.unlockedSkillIds(curriculum, completed),
                completedIds = completed,
                recentlySeenIds = recent,
                recentAccuracy = if (results.isEmpty()) null else results.count { it }.toDouble() / results.size,
                recentAttemptCount = results.size,
            )
            val nowMs = time.now().toEpochMilli()
            val planned = MissionGenerator.generate(curriculum, ctx)
            assignments.insertAll(planned.mapIndexed { i, m ->
                DailyAssignmentEntity(key, m.slot.name, m.challenge.id, nowMs + i, null)
            })
            assignments.missionsFor(key)
        }
    }

    fun observeMissions(date: LocalDate): Flow<List<DailyAssignmentEntity>> = assignments.observeMissionsFor(date.toString())

    /** Stores this week's boss once it becomes eligible, so it stays stable for the rest of the week. */
    suspend fun ensureWeeklyBoss(curriculum: Curriculum, today: LocalDate): DailyAssignmentEntity? = writeLock.withLock {
        val weekStart = StreakCalculator.weekStart(today).toString()
        db.withTransaction {
            assignments.bossFor(weekStart)?.let { stored ->
                if (curriculum.bosses.any { it.id == stored.challengeId }) return@withTransaction stored
            }
            val unlocked = SkillTreeCalculator.unlockedSkillIds(curriculum, attempts.completedIds().toSet())
            val boss = MissionGenerator.bossForWeek(curriculum, unlocked, StreakCalculator.weekStart(today))
                ?: return@withTransaction null
            assignments.insertAll(listOf(DailyAssignmentEntity(weekStart, BOSS_SLOT, boss.id, time.now().toEpochMilli(), null)))
            assignments.bossFor(weekStart)
        }
    }

    fun observeBoss(today: LocalDate): Flow<DailyAssignmentEntity?> =
        assignments.observeBossFor(StreakCalculator.weekStart(today).toString())

    // ---------------------------------------------------------------- submissions

    suspend fun submit(
        curriculum: Curriculum,
        challenge: Challenge,
        answer: UserAnswer,
        context: AttemptContext,
        durationMs: Long,
    ): SubmissionOutcome {
        val execution = executionService.execute(ExecutionRequest(challenge, answer))
        val validation = execution.validation ?: ValidationResult(
            correct = false,
            verdict = execution.output,
            feedback = execution.errors.firstOrNull(),
            solution = "",
        )
        return writeLock.withLock {
            db.withTransaction {
                val now = time.now()
                val nowMs = now.toEpochMilli()
                val today = time.today().toString()
                val levelBefore = LevelCalculator.levelFor(xp.total())

                attempts.insert(
                    AttemptEntity(
                        challengeId = challenge.id,
                        context = context.name,
                        correct = validation.correct,
                        answer = answer.storageText(),
                        submittedAtEpochMs = nowMs,
                        localDate = today,
                        durationMs = durationMs.coerceAtLeast(0),
                    ),
                )

                var xpAwarded = 0
                var alreadyEarned = false
                var completedMission = false
                if (validation.correct) {
                    val inserted = xp.insert(
                        XpTransactionEntity(
                            eventKey = ProgressionConfig.EventKeys.challenge(challenge.id),
                            amount = challenge.xp,
                            reason = "Completed ${challenge.title}",
                            createdAtEpochMs = nowMs,
                            localDate = today,
                        ),
                    )
                    if (inserted == -1L) alreadyEarned = true else xpAwarded = challenge.xp
                    revision.remove(challenge.id)
                    completedMission = assignments.markMissionCompleted(today, challenge.id, nowMs) > 0
                } else {
                    val existing = revision.get(challenge.id)
                    revision.upsert(
                        RevisionEntity(
                            challengeId = challenge.id,
                            addedAtEpochMs = existing?.addedAtEpochMs ?: nowMs,
                            wrongCount = (existing?.wrongCount ?: 0) + 1,
                            lastWrongDate = today,
                        ),
                    )
                }

                val completedIds = attempts.completedIds().toSet()
                val masteryBonus = refreshMastery(curriculum, completedIds, nowMs)
                val newAchievements = unlockAchievements(curriculum, completedIds, nowMs, today)
                val totalAfter = refreshProgressRow(nowMs, today.takeIf { validation.correct })
                SubmissionOutcome(
                    validation = validation,
                    execution = execution,
                    xpAwarded = xpAwarded,
                    alreadyEarned = alreadyEarned,
                    masteryBonusXp = masteryBonus,
                    levelBefore = levelBefore,
                    levelAfter = LevelCalculator.levelFor(totalAfter),
                    totalXpAfter = totalAfter,
                    newAchievements = newAchievements,
                    completedDailyMission = completedMission,
                )
            }
        }
    }

    /** Grants the weekly boss bonus once per boss per week. Call only after every part was answered correctly. */
    suspend fun completeBoss(curriculum: Curriculum, bossId: String, today: LocalDate): BossOutcome = writeLock.withLock {
        db.withTransaction {
            val nowMs = time.now().toEpochMilli()
            val weekStart = StreakCalculator.weekStart(today).toString()
            val levelBefore = LevelCalculator.levelFor(xp.total())
            val inserted = xp.insert(
                XpTransactionEntity(
                    eventKey = ProgressionConfig.EventKeys.boss(weekStart, bossId),
                    amount = ProgressionConfig.BOSS_XP,
                    reason = "Defeated boss $bossId",
                    createdAtEpochMs = nowMs,
                    localDate = today.toString(),
                ),
            )
            assignments.markBossCompleted(weekStart, nowMs)
            val completedIds = attempts.completedIds().toSet()
            val newAchievements = unlockAchievements(curriculum, completedIds, nowMs, today.toString())
            val totalAfter = refreshProgressRow(nowMs, null)
            BossOutcome(
                awarded = inserted != -1L,
                xpAwarded = if (inserted != -1L) ProgressionConfig.BOSS_XP else 0,
                levelBefore = levelBefore,
                levelAfter = LevelCalculator.levelFor(totalAfter),
                newAchievements = newAchievements,
            )
        }
    }

    fun observeBossDefeated(bossId: String, today: LocalDate): Flow<Boolean> =
        xp.observeExists(ProgressionConfig.EventKeys.boss(StreakCalculator.weekStart(today).toString(), bossId))

    // ---------------------------------------------------------------- internal helpers (call inside a transaction)

    /** Updates mastery rows and grants one-time mastery milestone XP. Returns the bonus granted now. */
    private suspend fun refreshMastery(curriculum: Curriculum, completedIds: Set<String>, nowMs: Long): Int {
        val previous = mastery.all().associateBy { it.skillId }
        val statuses = SkillTreeCalculator.compute(curriculum, completedIds)
        var bonus = 0
        val rows = statuses.map { s ->
            val old = previous[s.node.id]
            val masteredNow = s.state == NodeState.MASTERED
            if (masteredNow) {
                val inserted = xp.insert(
                    XpTransactionEntity(
                        eventKey = ProgressionConfig.EventKeys.mastery(s.node.id),
                        amount = ProgressionConfig.MASTERY_MILESTONE_XP,
                        reason = "Mastered ${s.node.title}",
                        createdAtEpochMs = nowMs,
                        localDate = time.today().toString(),
                    ),
                )
                if (inserted != -1L) bonus += ProgressionConfig.MASTERY_MILESTONE_XP
            }
            SkillMasteryEntity(
                skillId = s.node.id,
                completedCount = s.completed,
                totalCount = s.total,
                masteryPercent = s.masteryPercent,
                unlockedAtEpochMs = old?.unlockedAtEpochMs ?: if (s.unlocked) nowMs else null,
                masteredAtEpochMs = old?.masteredAtEpochMs ?: if (masteredNow) nowMs else null,
            )
        }
        mastery.upsertAll(rows)
        return bonus
    }

    private suspend fun unlockAchievements(curriculum: Curriculum, completedIds: Set<String>, nowMs: Long, today: String): List<Achievement> {
        val byCategory = completedIds.mapNotNull { curriculum.byId[it]?.category }.groupingBy { it }.eachCount()
        val longest = maxOf(
            StreakCalculator.compute(parseDates(attempts.activeDates()), time.today()).longest,
            progress.get()?.longestStreak ?: 0,
        )
        val stats = AchievementStats(
            completedChallenges = completedIds.count { it in curriculum.byId },
            completedByCategory = byCategory,
            longestStreak = longest,
            bossesDefeated = xp.bossCount(),
            masteredSkills = mastery.all().count { it.totalCount > 0 && it.completedCount >= it.totalCount },
        )
        val fresh = AchievementEngine.newlyEarned(stats, achievementsDao.unlockedIds().toSet())
        return fresh.filter { achievementsDao.insert(AchievementEntity(it.name, nowMs, today)) != -1L }
    }

    /** Recomputes cached totals; the longest streak never decreases. Returns total XP. */
    private suspend fun refreshProgressRow(nowMs: Long, activeDate: String?): Int {
        val total = xp.total()
        val current = progress.get() ?: UserProgressEntity(createdAtEpochMs = nowMs, updatedAtEpochMs = nowMs)
        val longest = StreakCalculator.compute(parseDates(attempts.activeDates()), time.today()).longest
        progress.upsert(
            current.copy(
                totalXp = total,
                level = LevelCalculator.levelFor(total),
                longestStreak = maxOf(current.longestStreak, longest),
                lastActiveDate = activeDate ?: current.lastActiveDate,
                updatedAtEpochMs = nowMs,
            ),
        )
        return total
    }

    // ---------------------------------------------------------------- observation

    fun observeTotalXp(): Flow<Int> = xp.observeTotal()
    fun observeCompletedIds(): Flow<Set<String>> = attempts.observeCompletedIds().map { it.toSet() }
    fun observeAttemptedIds(): Flow<Set<String>> = attempts.observeAttemptedIds().map { it.toSet() }
    fun observeTotals() = attempts.observeTotals()
    fun observeActiveDates(): Flow<Set<LocalDate>> = attempts.observeActiveDates().map { parseDates(it).toSet() }
    fun observeAchievements() = achievementsDao.observeAll()
    fun observeRevisionQueue() = revision.observeAll()
    fun observeBossCount() = xp.observeBossCount()
    fun observeProgressRow() = progress.observe()

    fun observeDailyActivity(since: LocalDate): Flow<List<DayActivity>> =
        attempts.observeDailyCounts(since.toString()).map { rows ->
            rows.mapNotNull { r -> parseDate(r.localDate)?.let { DayActivity(it, r.attempts, r.correct) } }
        }

    fun observeCategoryAccuracy(): Flow<List<CategoryAccuracy>> = attempts.observeCategoryCounts().map { rows ->
        rows.mapNotNull { r ->
            Category.entries.firstOrNull { it.name == r.category }?.let { CategoryAccuracy(it, r.attempts, r.correct) }
        }
    }

    // ---------------------------------------------------------------- export & reset

    suspend fun exportJson(settings: UserSettings, appVersion: String, contentVersion: Int): String {
        val total = xp.total()
        val export = ProgressExport(
            exportedAt = time.now().toString(),
            appVersion = appVersion,
            contentVersion = contentVersion,
            profile = ExportProfile(
                displayName = settings.displayName,
                avatarId = settings.avatarId,
                dailyGoal = settings.dailyGoal,
                weeklyGoalDays = settings.weeklyGoalDays,
                reminderEnabled = settings.reminderEnabled,
                reminderTime = "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                theme = settings.themeMode.name,
            ),
            totalXp = total,
            level = LevelCalculator.levelFor(total),
            attempts = attempts.all().map {
                ExportAttempt(it.challengeId, it.context, it.correct, it.answer, Instant.ofEpochMilli(it.submittedAtEpochMs).toString(), it.localDate, it.durationMs)
            },
            xpTransactions = xp.all().map { ExportXp(it.eventKey, it.amount, it.reason, Instant.ofEpochMilli(it.createdAtEpochMs).toString()) },
            achievements = achievementsDao.all().map { ExportAchievement(it.id, Instant.ofEpochMilli(it.unlockedAtEpochMs).toString()) },
            revisionQueue = revision.all().map { ExportRevision(it.challengeId, it.wrongCount, it.lastWrongDate) },
            dailyAssignments = assignments.all().map { ExportAssignment(it.date, it.slot, it.challengeId, it.completedAtEpochMs != null) },
        )
        return prettyJson.encodeToString(export)
    }

    /** Deletes every learner record. Bundled content is untouched. Must not be called on the main thread. */
    suspend fun resetAll(curriculum: Curriculum?) {
        writeLock.withLock { withContext(Dispatchers.IO) { db.clearAllTables() } }
        if (curriculum != null) syncContent(curriculum)
    }

    companion object {
        const val BOSS_SLOT = "BOSS"
        private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

        fun parseDate(text: String): LocalDate? = try { LocalDate.parse(text) } catch (e: DateTimeParseException) { null }
        fun parseDates(texts: List<String>): List<LocalDate> = texts.mapNotNull { parseDate(it) }
    }
}
