package com.skillverse.app.domain.progression

import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.model.RewardClass

/**
 * The single place where progression numbers are configured. See README "Progression system".
 *
 * XP is awarded once per event key (first correct completion of a challenge, one boss clear per
 * week, one mastery milestone per skill), so reopening or resubmitting can never duplicate rewards.
 */
object ProgressionConfig {
    const val QUIZ_XP = 10
    const val CODING_XP = 25
    const val DEBUGGING_XP = 30
    const val BOSS_XP = 75
    const val MASTERY_MILESTONE_XP = 100

    /** A skill node counts as completed (and unlocks the next) at this mastery, unless content overrides it. */
    const val DEFAULT_UNLOCK_THRESHOLD_PERCENT = 60

    /** Questions seen within this many days are avoided by the daily mission generator when possible. */
    const val REPEAT_AVOIDANCE_DAYS = 7L

    /** Target duration for the three daily missions combined. */
    const val DAILY_SESSION_MIN_SECONDS = 600
    const val DAILY_SESSION_MAX_SECONDS = 1200

    /** Number of recent attempts used to adapt mission difficulty. */
    const val DIFFICULTY_WINDOW = 20

    const val MAX_LEVEL = 99

    fun xpFor(type: ChallengeType): Int = when (type.rewardClass) {
        RewardClass.QUIZ -> QUIZ_XP
        RewardClass.CODING -> CODING_XP
        RewardClass.DEBUGGING -> DEBUGGING_XP
    }

    object EventKeys {
        fun challenge(challengeId: String) = "challenge:$challengeId"
        fun boss(weekStartIso: String, bossId: String) = "boss:$weekStartIso:$bossId"
        fun mastery(skillId: String) = "mastery:$skillId"
        const val BOSS_PREFIX = "boss:"
    }
}
