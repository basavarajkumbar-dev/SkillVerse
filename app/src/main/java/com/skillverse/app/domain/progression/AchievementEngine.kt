package com.skillverse.app.domain.progression

import com.skillverse.app.domain.model.Category

enum class Achievement(val title: String, val requirement: String, val glyph: String) {
    FIRST_MISSION("First Mission", "Complete your first challenge correctly.", "1"),
    PYTHON_EXPLORER("Python Explorer", "Complete 10 Python challenges.", "Py"),
    DEBUG_DETECTIVE("Debug Detective", "Complete 10 debugging or logic challenges.", "Bug"),
    FIVE_DAY_LEARNER("Five-Day Learner", "Learn on 5 consecutive days.", "5d"),
    AUTOMATION_APPRENTICE("Automation Apprentice", "Complete 8 test-automation or API-testing challenges.", "Bot"),
    BOSS_CHALLENGER("Boss Challenger", "Defeat a weekly boss challenge.", "Boss"),
    MASTERY_MILESTONE("Mastery Milestone", "Reach 100% mastery in any skill.", "100"),
}

/** Facts derived from persisted records; the engine never trusts UI state. */
data class AchievementStats(
    val completedChallenges: Int,
    val completedByCategory: Map<Category, Int>,
    val longestStreak: Int,
    val bossesDefeated: Int,
    val masteredSkills: Int,
) {
    fun completedIn(vararg categories: Category): Int = categories.sumOf { completedByCategory[it] ?: 0 }
}

object AchievementEngine {
    const val PYTHON_EXPLORER_TARGET = 10
    const val DEBUG_DETECTIVE_TARGET = 10
    const val FIVE_DAY_TARGET = 5
    const val AUTOMATION_TARGET = 8

    fun isEarned(achievement: Achievement, s: AchievementStats): Boolean = when (achievement) {
        Achievement.FIRST_MISSION -> s.completedChallenges >= 1
        Achievement.PYTHON_EXPLORER -> s.completedIn(Category.PYTHON) >= PYTHON_EXPLORER_TARGET
        Achievement.DEBUG_DETECTIVE -> s.completedIn(Category.DEBUGGING, Category.LOGIC) >= DEBUG_DETECTIVE_TARGET
        Achievement.FIVE_DAY_LEARNER -> s.longestStreak >= FIVE_DAY_TARGET
        Achievement.AUTOMATION_APPRENTICE ->
            s.completedIn(Category.TEST_AUTOMATION, Category.API_TESTING) >= AUTOMATION_TARGET
        Achievement.BOSS_CHALLENGER -> s.bossesDefeated >= 1
        Achievement.MASTERY_MILESTONE -> s.masteredSkills >= 1
    }

    fun earned(stats: AchievementStats): Set<Achievement> = Achievement.entries.filter { isEarned(it, stats) }.toSet()

    /** Achievements to unlock now: earned but not yet stored. Already-stored ones are never removed. */
    fun newlyEarned(stats: AchievementStats, alreadyUnlocked: Set<String>): List<Achievement> =
        Achievement.entries.filter { it.name !in alreadyUnlocked && isEarned(it, stats) }
}
