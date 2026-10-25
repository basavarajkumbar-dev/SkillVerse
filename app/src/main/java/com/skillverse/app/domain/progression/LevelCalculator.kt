package com.skillverse.app.domain.progression

data class LevelProgress(
    val level: Int,
    val totalXp: Int,
    /** XP earned since reaching [level]. */
    val xpIntoLevel: Int,
    /** XP needed to go from [level] to the next level. */
    val xpForNextLevel: Int,
) {
    val xpRemaining: Int get() = (xpForNextLevel - xpIntoLevel).coerceAtLeast(0)
    val fraction: Float get() = if (xpForNextLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f)
    val isMaxLevel: Boolean get() = level >= ProgressionConfig.MAX_LEVEL
}

/**
 * Level thresholds grow gradually: going from level n to n+1 costs 50n + 50 XP
 * (100, 150, 200, ...). The total needed to reach level L is 25 (L-1)(L+2).
 */
object LevelCalculator {
    fun xpToReachLevel(level: Int): Int {
        require(level >= 1) { "level must be >= 1" }
        return 25 * (level - 1) * (level + 2)
    }

    fun xpForStep(level: Int): Int = 50 * level + 50

    fun levelFor(totalXp: Int): Int {
        if (totalXp <= 0) return 1
        var level = 1
        while (level < ProgressionConfig.MAX_LEVEL && xpToReachLevel(level + 1) <= totalXp) level++
        return level
    }

    fun progress(totalXp: Int): LevelProgress {
        val xp = totalXp.coerceAtLeast(0)
        val level = levelFor(xp)
        val start = xpToReachLevel(level)
        return if (level >= ProgressionConfig.MAX_LEVEL) {
            LevelProgress(level, xp, xpIntoLevel = 1, xpForNextLevel = 1)
        } else {
            LevelProgress(level, xp, xpIntoLevel = xp - start, xpForNextLevel = xpForStep(level))
        }
    }
}
