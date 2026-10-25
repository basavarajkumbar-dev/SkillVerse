package com.skillverse.app.domain.missions

import com.skillverse.app.domain.model.Boss
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.model.Difficulty
import com.skillverse.app.domain.model.StableShuffle
import com.skillverse.app.domain.progression.ProgressionConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class MissionSlot(val label: String, val types: Set<ChallengeType>) {
    CODING("Coding exercise", setOf(ChallengeType.PREDICT_OUTPUT, ChallengeType.FILL_BLANK, ChallengeType.CODE_PUZZLE)),
    QUIZ("Technical quiz", setOf(ChallengeType.MULTIPLE_CHOICE)),
    DEBUG("Debugging scenario", setOf(ChallengeType.IDENTIFY_BUG, ChallengeType.ARRANGE_STEPS)),
}

data class MissionContext(
    val date: LocalDate,
    val unlockedSkillIds: Set<String>,
    val completedIds: Set<String>,
    /** Challenges attempted or assigned within the repeat-avoidance window. */
    val recentlySeenIds: Set<String>,
    /** Correct ratio over the recent attempt window; null when there is no history yet. */
    val recentAccuracy: Double?,
    val recentAttemptCount: Int,
)

data class PlannedMission(val slot: MissionSlot, val challenge: Challenge)

/**
 * Deterministic daily mission selection. For the same curriculum and context it always returns
 * the same missions; the result is also persisted, so reopening the app never changes a day's set.
 *
 * Ranking per slot (lower is better): not yet completed, not seen recently, closest to the target
 * difficulty, then a stable hash of (challenge id, date) for daily variety.
 * Among the top candidates of each slot, the combination whose total estimated time falls inside
 * [ProgressionConfig.DAILY_SESSION_MIN_SECONDS]..[ProgressionConfig.DAILY_SESSION_MAX_SECONDS] with the
 * best rank is chosen; if none fits, the best-ranked combination is used.
 */
object MissionGenerator {
    private const val TOP_CANDIDATES = 3

    fun targetDifficulty(recentAccuracy: Double?, attempts: Int): Difficulty = when {
        recentAccuracy == null || attempts < 5 -> Difficulty.EASY
        recentAccuracy >= 0.8 -> Difficulty.HARD
        recentAccuracy >= 0.55 -> Difficulty.MEDIUM
        else -> Difficulty.EASY
    }

    private data class Ranked(val challenge: Challenge, val score: Long)

    private fun rank(c: Challenge, ctx: MissionContext, target: Difficulty): Long {
        val completed = if (c.id in ctx.completedIds) 1L else 0L
        val recent = if (c.id in ctx.recentlySeenIds) 1L else 0L
        val diffGap = kotlin.math.abs(c.difficulty.rank - target.rank).toLong()
        val tiebreak = (StableShuffle.fnv1a("${c.id}:${ctx.date.toEpochDay()}").toLong() and 0xFFFFFFFFL)
        // Lexicographic ordering packed into one Long: completed > recent > difficulty gap > tiebreak.
        return (completed shl 40) or (recent shl 38) or (diffGap shl 34) or (tiebreak shr 2)
    }

    fun generate(curriculum: Curriculum, ctx: MissionContext): List<PlannedMission> {
        val pool = curriculum.challenges.filter { it.skillId in ctx.unlockedSkillIds }
        if (pool.isEmpty()) return emptyList()
        val target = targetDifficulty(ctx.recentAccuracy, ctx.recentAttemptCount)

        val perSlot: List<Pair<MissionSlot, List<Ranked>>> = MissionSlot.entries.map { slot ->
            val typed = pool.filter { it.type in slot.types }
            val candidates = typed.ifEmpty { pool }
            slot to candidates.map { Ranked(it, rank(it, ctx, target)) }.sortedBy { it.score }
        }

        var best: List<PlannedMission>? = null
        var bestScore = Long.MAX_VALUE
        var fallback: List<PlannedMission>? = null
        var fallbackDistance = Int.MAX_VALUE
        var fallbackScore = Long.MAX_VALUE

        fun consider(choice: List<Pair<MissionSlot, Ranked>>) {
            if (choice.map { it.second.challenge.id }.distinct().size != choice.size) return
            val score = choice.sumOf { it.second.score / 4 }
            val seconds = choice.sumOf { it.second.challenge.estimatedSeconds }
            val missions = choice.map { PlannedMission(it.first, it.second.challenge) }
            val distance = when {
                seconds < ProgressionConfig.DAILY_SESSION_MIN_SECONDS -> ProgressionConfig.DAILY_SESSION_MIN_SECONDS - seconds
                seconds > ProgressionConfig.DAILY_SESSION_MAX_SECONDS -> seconds - ProgressionConfig.DAILY_SESSION_MAX_SECONDS
                else -> 0
            }
            if (distance == 0 && score < bestScore) { best = missions; bestScore = score }
            // Otherwise prefer the combination closest to the target duration, then the best rank.
            if (distance < fallbackDistance || (distance == fallbackDistance && score < fallbackScore)) {
                fallback = missions; fallbackDistance = distance; fallbackScore = score
            }
        }

        val tops = perSlot.map { (slot, ranked) -> ranked.take(TOP_CANDIDATES).map { slot to it } }
        fun recurse(index: Int, acc: List<Pair<MissionSlot, Ranked>>) {
            if (index == tops.size) { consider(acc); return }
            for (pick in tops[index]) recurse(index + 1, acc + pick)
        }
        recurse(0, emptyList())

        best?.let { return it }
        fallback?.let { return it }
        // Fewer distinct challenges than slots: return what exists without duplicates.
        val seen = mutableSetOf<String>()
        return perSlot.mapNotNull { (slot, ranked) ->
            ranked.firstOrNull { seen.add(it.challenge.id) }?.let { PlannedMission(slot, it.challenge) }
        }
    }

    /** A boss is eligible when every challenge it uses belongs to an unlocked skill. */
    fun eligibleBosses(curriculum: Curriculum, unlockedSkillIds: Set<String>): List<Boss> =
        curriculum.bosses.filter { boss ->
            boss.challengeIds.all { id -> curriculum.byId[id]?.skillId in unlockedSkillIds }
        }

    /** Deterministically picks this week's boss from the eligible ones. */
    fun bossForWeek(curriculum: Curriculum, unlockedSkillIds: Set<String>, weekStart: LocalDate): Boss? {
        val eligible = eligibleBosses(curriculum, unlockedSkillIds)
        if (eligible.isEmpty()) return null
        val weekIndex = ChronoUnit.WEEKS.between(LocalDate.of(2024, 1, 1), weekStart).toInt()
        return eligible[Math.floorMod(weekIndex, eligible.size)]
    }
}
