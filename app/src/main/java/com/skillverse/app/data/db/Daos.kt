package com.skillverse.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeRefDao {
    @Upsert
    suspend fun upsertAll(refs: List<ChallengeRefEntity>)

    @Query("UPDATE challenge_refs SET retired = 1 WHERE id NOT IN (:currentIds)")
    suspend fun retireMissing(currentIds: List<String>)

    @Query("SELECT COUNT(*) FROM challenge_refs WHERE retired = 0")
    suspend fun activeCount(): Int
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT DISTINCT challengeId FROM attempts WHERE correct = 1")
    suspend fun completedIds(): List<String>

    @Query("SELECT DISTINCT challengeId FROM attempts WHERE correct = 1")
    fun observeCompletedIds(): Flow<List<String>>

    @Query("SELECT DISTINCT challengeId FROM attempts")
    fun observeAttemptedIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(correct), 0) AS correct FROM attempts")
    fun observeTotals(): Flow<AttemptTotals>

    @Query("SELECT DISTINCT localDate FROM attempts WHERE correct = 1")
    suspend fun activeDates(): List<String>

    @Query("SELECT DISTINCT localDate FROM attempts WHERE correct = 1")
    fun observeActiveDates(): Flow<List<String>>

    @Query(
        "SELECT localDate, COUNT(*) AS attempts, COALESCE(SUM(correct), 0) AS correct FROM attempts " +
            "WHERE localDate >= :sinceDate GROUP BY localDate ORDER BY localDate",
    )
    fun observeDailyCounts(sinceDate: String): Flow<List<DayCount>>

    @Query(
        "SELECT r.category AS category, COUNT(*) AS attempts, COALESCE(SUM(a.correct), 0) AS correct " +
            "FROM attempts a JOIN challenge_refs r ON a.challengeId = r.id GROUP BY r.category",
    )
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query(
        "SELECT r.category AS category, COUNT(DISTINCT a.challengeId) AS completed " +
            "FROM attempts a JOIN challenge_refs r ON a.challengeId = r.id WHERE a.correct = 1 GROUP BY r.category",
    )
    suspend fun completedByCategory(): List<CompletedByCategory>

    @Query("SELECT correct FROM attempts ORDER BY submittedAtEpochMs DESC, id DESC LIMIT :limit")
    suspend fun recentResults(limit: Int): List<Boolean>

    @Query("SELECT DISTINCT challengeId FROM attempts WHERE localDate >= :sinceDate")
    suspend fun attemptedSince(sinceDate: String): List<String>

    @Query("SELECT * FROM attempts ORDER BY submittedAtEpochMs")
    suspend fun all(): List<AttemptEntity>
}

@Dao
interface AssignmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(assignments: List<DailyAssignmentEntity>): List<Long>

    @Query("SELECT * FROM daily_assignments WHERE date = :date AND slot != 'BOSS' ORDER BY assignedAtEpochMs, slot")
    suspend fun missionsFor(date: String): List<DailyAssignmentEntity>

    @Query("SELECT * FROM daily_assignments WHERE date = :date AND slot != 'BOSS'")
    fun observeMissionsFor(date: String): Flow<List<DailyAssignmentEntity>>

    @Query("SELECT * FROM daily_assignments WHERE date = :weekStart AND slot = 'BOSS' LIMIT 1")
    suspend fun bossFor(weekStart: String): DailyAssignmentEntity?

    @Query("SELECT * FROM daily_assignments WHERE date = :weekStart AND slot = 'BOSS' LIMIT 1")
    fun observeBossFor(weekStart: String): Flow<DailyAssignmentEntity?>

    @Query(
        "UPDATE daily_assignments SET completedAtEpochMs = :completedAt " +
            "WHERE date = :date AND challengeId = :challengeId AND slot != 'BOSS' AND completedAtEpochMs IS NULL",
    )
    suspend fun markMissionCompleted(date: String, challengeId: String, completedAt: Long): Int

    @Query("UPDATE daily_assignments SET completedAtEpochMs = :completedAt WHERE date = :weekStart AND slot = 'BOSS' AND completedAtEpochMs IS NULL")
    suspend fun markBossCompleted(weekStart: String, completedAt: Long): Int

    @Query("SELECT DISTINCT challengeId FROM daily_assignments WHERE date >= :sinceDate AND slot != 'BOSS'")
    suspend fun assignedSince(sinceDate: String): List<String>

    @Query("SELECT * FROM daily_assignments ORDER BY date")
    suspend fun all(): List<DailyAssignmentEntity>
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 1")
    suspend fun get(): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE id = 1")
    fun observe(): Flow<UserProgressEntity?>

    @Upsert
    suspend fun upsert(progress: UserProgressEntity)
}

@Dao
interface SkillMasteryDao {
    @Upsert
    suspend fun upsertAll(rows: List<SkillMasteryEntity>)

    @Query("SELECT * FROM skill_mastery")
    suspend fun all(): List<SkillMasteryEntity>
}

@Dao
interface XpDao {
    /** Returns -1 when the event key already exists, i.e. the reward was already granted. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: XpTransactionEntity): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions")
    suspend fun total(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM xp_transactions WHERE eventKey LIKE 'boss:%'")
    suspend fun bossCount(): Int

    @Query("SELECT COUNT(*) FROM xp_transactions WHERE eventKey LIKE 'boss:%'")
    fun observeBossCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM xp_transactions WHERE eventKey = :key)")
    suspend fun exists(key: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM xp_transactions WHERE eventKey = :key)")
    fun observeExists(key: String): Flow<Boolean>

    @Query("SELECT * FROM xp_transactions ORDER BY createdAtEpochMs")
    suspend fun all(): List<XpTransactionEntity>
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: AchievementEntity): Long

    @Query("SELECT id FROM achievements")
    suspend fun unlockedIds(): List<String>

    @Query("SELECT * FROM achievements ORDER BY unlockedAtEpochMs DESC")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements ORDER BY unlockedAtEpochMs")
    suspend fun all(): List<AchievementEntity>
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM revision_queue WHERE challengeId = :id")
    suspend fun get(id: String): RevisionEntity?

    @Upsert
    suspend fun upsert(row: RevisionEntity)

    @Query("DELETE FROM revision_queue WHERE challengeId = :id")
    suspend fun remove(id: String): Int

    @Query("SELECT * FROM revision_queue ORDER BY wrongCount DESC, addedAtEpochMs ASC")
    fun observeAll(): Flow<List<RevisionEntity>>

    @Query("SELECT * FROM revision_queue ORDER BY addedAtEpochMs")
    suspend fun all(): List<RevisionEntity>
}
