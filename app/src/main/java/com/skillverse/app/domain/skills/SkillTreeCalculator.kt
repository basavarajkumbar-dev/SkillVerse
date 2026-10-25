package com.skillverse.app.domain.skills

import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.model.SkillNode

enum class NodeState(val label: String) {
    LOCKED("Locked"),
    AVAILABLE("Available"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    MASTERED("Mastered"),
}

data class SkillNodeStatus(
    val node: SkillNode,
    val state: NodeState,
    val completed: Int,
    val total: Int,
    val masteryPercent: Int,
    val unlocked: Boolean,
    /** Human explanation of how to unlock, or null when unlocked. */
    val unlockHint: String?,
)

object SkillTreeCalculator {
    fun masteryPercent(completed: Int, total: Int): Int =
        if (total <= 0) 0 else ((completed * 100) / total).coerceIn(0, 100)

    fun compute(curriculum: Curriculum, completedIds: Set<String>): List<SkillNodeStatus> {
        val threshold = curriculum.unlockThresholdPercent
        val mastery = curriculum.skills.associate { node ->
            val items = curriculum.challengesForSkill(node.id)
            val done = items.count { it.id in completedIds }
            node.id to Triple(done, items.size, masteryPercent(done, items.size))
        }
        return curriculum.skills.sortedBy { it.order }.map { node ->
            val (done, total, percent) = mastery.getValue(node.id)
            val unmet = node.prerequisites.filter { (mastery[it]?.third ?: 0) < threshold }
            val unlocked = unmet.isEmpty()
            val state = when {
                !unlocked -> NodeState.LOCKED
                total > 0 && done == total -> NodeState.MASTERED
                percent >= threshold -> NodeState.COMPLETED
                done > 0 -> NodeState.IN_PROGRESS
                else -> NodeState.AVAILABLE
            }
            val hint = if (unlocked) null else unmet.joinToString(separator = " and ") { prereqId ->
                val prereq = curriculum.skillsById[prereqId]
                val (pDone, pTotal, pPercent) = mastery[prereqId] ?: Triple(0, 0, 0)
                val needed = ((threshold * pTotal) + 99) / 100
                val remaining = (needed - pDone).coerceAtLeast(0)
                "Reach $threshold% in ${prereq?.title ?: prereqId} (now $pPercent%, " +
                    "$remaining more correct challenge${if (remaining == 1) "" else "s"})"
            }
            SkillNodeStatus(node, state, done, total, percent, unlocked, hint)
        }
    }

    fun unlockedSkillIds(curriculum: Curriculum, completedIds: Set<String>): Set<String> =
        compute(curriculum, completedIds).filter { it.unlocked }.map { it.node.id }.toSet()
}
