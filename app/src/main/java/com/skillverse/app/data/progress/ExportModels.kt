package com.skillverse.app.data.progress

import kotlinx.serialization.Serializable

/** Shape of the user's data export. Contains learning records and preferences only. */
@Serializable
data class ProgressExport(
    val format: String = "skillverse-progress",
    val formatVersion: Int = 1,
    val exportedAt: String,
    val appVersion: String,
    val contentVersion: Int,
    val profile: ExportProfile,
    val totalXp: Int,
    val level: Int,
    val attempts: List<ExportAttempt>,
    val xpTransactions: List<ExportXp>,
    val achievements: List<ExportAchievement>,
    val revisionQueue: List<ExportRevision>,
    val dailyAssignments: List<ExportAssignment>,
)

@Serializable
data class ExportProfile(
    val displayName: String,
    val avatarId: Int,
    val dailyGoal: Int,
    val weeklyGoalDays: Int,
    val reminderEnabled: Boolean,
    val reminderTime: String,
    val theme: String,
)

@Serializable
data class ExportAttempt(val challengeId: String, val context: String, val correct: Boolean, val answer: String, val submittedAt: String, val localDate: String, val durationMs: Long)

@Serializable
data class ExportXp(val eventKey: String, val amount: Int, val reason: String, val createdAt: String)

@Serializable
data class ExportAchievement(val id: String, val unlockedAt: String)

@Serializable
data class ExportRevision(val challengeId: String, val wrongCount: Int, val lastWrongDate: String)

@Serializable
data class ExportAssignment(val date: String, val slot: String, val id: String, val completed: Boolean)
