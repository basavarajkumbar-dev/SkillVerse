package com.skillverse.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lightweight reference to a bundled challenge. Curriculum text stays in the versioned JSON
 * assets; this table lets SQL join user records with category/skill and marks retired content.
 */
@Entity(tableName = "challenge_refs")
data class ChallengeRefEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val category: String,
    val type: String,
    val difficulty: String,
    val contentVersion: Int,
    /** True when the current bundle no longer contains this challenge (history is kept). */
    val retired: Boolean = false,
)

@Entity(
    tableName = "attempts",
    indices = [Index("challengeId"), Index("localDate"), Index("correct")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: String,
    /** PRACTICE, DAILY, REVIEW, BOSS or TIMED. */
    val context: String,
    val correct: Boolean,
    /** What the user submitted (truncated); never contains personal data. */
    val answer: String,
    val submittedAtEpochMs: Long,
    /** Device-local calendar date (ISO yyyy-MM-dd) at submission time. */
    val localDate: String,
    val durationMs: Long,
)

/** Daily mission assignments, plus the weekly boss (slot BOSS, date = Monday of the week). */
@Entity(tableName = "daily_assignments", primaryKeys = ["date", "slot"], indices = [Index("challengeId")])
data class DailyAssignmentEntity(
    val date: String,
    val slot: String,
    /** Challenge id, or the boss id for the BOSS slot. */
    val challengeId: String,
    val assignedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
)

/** Single-row cache of headline progress, updated inside the submission transaction. */
@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val totalXp: Int = 0,
    val level: Int = 1,
    val longestStreak: Int = 0,
    val lastActiveDate: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "skill_mastery")
data class SkillMasteryEntity(
    @PrimaryKey val skillId: String,
    val completedCount: Int,
    val totalCount: Int,
    val masteryPercent: Int,
    val unlockedAtEpochMs: Long? = null,
    val masteredAtEpochMs: Long? = null,
)

/** Every XP award. The unique event key makes duplicate awards impossible at the database level. */
@Entity(tableName = "xp_transactions", indices = [Index(value = ["eventKey"], unique = true)])
data class XpTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventKey: String,
    val amount: Int,
    val reason: String,
    val createdAtEpochMs: Long,
    val localDate: String,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAtEpochMs: Long,
    val localDate: String,
)

@Entity(tableName = "revision_queue")
data class RevisionEntity(
    @PrimaryKey val challengeId: String,
    val addedAtEpochMs: Long,
    val wrongCount: Int,
    val lastWrongDate: String,
)

/** Query projections. */
data class AttemptTotals(
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "correct") val correct: Int,
)

data class CategoryCount(val category: String, val attempts: Int, val correct: Int)

data class DayCount(val localDate: String, val attempts: Int, val correct: Int)

data class CompletedByCategory(val category: String, val completed: Int)
