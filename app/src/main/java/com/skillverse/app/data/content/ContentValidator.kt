package com.skillverse.app.data.content

import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.Boss
import com.skillverse.app.domain.model.Category
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.model.Difficulty
import com.skillverse.app.domain.model.MatchMode
import com.skillverse.app.domain.model.Project
import com.skillverse.app.domain.model.SkillNode
import com.skillverse.app.domain.model.TestCase

/**
 * Converts raw content into domain models, rejecting malformed items with a diagnostic per problem.
 * Rejected items are left out; valid items are still usable, so one bad entry cannot break the app.
 */
object ContentValidator {
    const val SUPPORTED_SCHEMA_VERSION = 1
    private val ID_PATTERN = Regex("^[a-z]+-\\d{3}$")

    data class Result<T>(val value: T, val diagnostics: List<String>)

    fun validateSkills(dto: SkillTreeDto): Result<List<SkillNode>> {
        val problems = mutableListOf<String>()
        val ids = dto.nodes.map { it.id }
        val dupes = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        dupes.forEach { problems += "skills.json: duplicate skill id '$it'" }
        val nodes = dto.nodes.mapIndexedNotNull { index, n ->
            val unknown = n.prerequisites.filter { it !in ids }
            when {
                n.id.isBlank() || n.title.isBlank() -> { problems += "skills.json: node #$index is missing id or title"; null }
                unknown.isNotEmpty() -> { problems += "skills.json: '${n.id}' has unknown prerequisites $unknown"; null }
                n.id in dupes && ids.indexOf(n.id) != index -> null
                else -> SkillNode(n.id, n.title, n.glyph.ifBlank { n.title.take(2) }, n.description, n.prerequisites, index)
            }
        }
        if (dto.unlockThresholdPercent !in 1..100) problems += "skills.json: unlockThresholdPercent must be 1..100"
        return Result(nodes, problems)
    }

    fun validateChallenges(packName: String, dtos: List<ChallengeDto>, skillIds: Set<String>, seenIds: MutableSet<String>): Result<List<Challenge>> {
        val problems = mutableListOf<String>()
        val result = dtos.mapIndexedNotNull { index, dto ->
            val where = "$packName ${dto.id.ifBlank { "#$index" }}"
            val itemProblems = problemsFor(dto, skillIds)
            if (dto.id in seenIds) itemProblems += "duplicate id"
            if (itemProblems.isNotEmpty()) {
                itemProblems.forEach { problems += "$where: $it" }
                null
            } else {
                seenIds += dto.id
                toChallenge(dto)
            }
        }
        return Result(result, problems)
    }

    private fun problemsFor(dto: ChallengeDto, skillIds: Set<String>): MutableList<String> {
        val p = mutableListOf<String>()
        if (!ID_PATTERN.matches(dto.id)) p += "id '${dto.id}' must look like abc-001"
        if (dto.title.isBlank()) p += "missing title"
        if (dto.description.isBlank()) p += "missing description"
        if (dto.instructions.isBlank()) p += "missing instructions"
        if (dto.explanation.isBlank()) p += "missing explanation"
        if (dto.hints.isEmpty()) p += "needs at least one hint"
        if (dto.estimatedSeconds !in 30..900) p += "estimatedSeconds must be 30..900"
        if (enumOrNull<Category>(dto.category) == null) p += "unknown category '${dto.category}'"
        if (enumOrNull<Difficulty>(dto.difficulty) == null) p += "unknown difficulty '${dto.difficulty}'"
        if (dto.skillId !in skillIds) p += "unknown skillId '${dto.skillId}'"
        when (enumOrNull<ChallengeType>(dto.type)) {
            null -> p += "unknown type '${dto.type}'"
            ChallengeType.MULTIPLE_CHOICE, ChallengeType.IDENTIFY_BUG -> {
                if (dto.options.size < 2) p += "needs at least two options"
                if (dto.options.toSet().size != dto.options.size) p += "options must be unique"
                val ci = dto.correctIndex
                if (ci == null || ci !in dto.options.indices) p += "correctIndex out of range"
                if (dto.type == ChallengeType.IDENTIFY_BUG.name && dto.code.isNullOrBlank()) p += "IDENTIFY_BUG needs code"
            }
            ChallengeType.ARRANGE_STEPS -> {
                if (dto.steps.size < 3) p += "needs at least three steps"
                if (dto.steps.toSet().size != dto.steps.size) p += "steps must be unique"
            }
            ChallengeType.PREDICT_OUTPUT, ChallengeType.FILL_BLANK, ChallengeType.CODE_PUZZLE -> {
                if (dto.acceptedAnswers.isEmpty() || dto.acceptedAnswers.any { it.isBlank() }) p += "needs non-blank acceptedAnswers"
                if (enumOrNull<MatchMode>(dto.matchMode ?: "") == null) p += "unknown matchMode '${dto.matchMode}'"
                if (dto.type != ChallengeType.CODE_PUZZLE.name && dto.code.isNullOrBlank()) p += "${dto.type} needs code"
                if (dto.type == ChallengeType.FILL_BLANK.name && (dto.code ?: "").windowedCount(BLANK) != 1) {
                    p += "FILL_BLANK code must contain exactly one $BLANK"
                }
            }
        }
        return p
    }

    const val BLANK = "____"

    private fun String.windowedCount(token: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val i = indexOf(token, from)
            if (i < 0) return count
            count++
            from = i + token.length
        }
    }

    private fun toChallenge(dto: ChallengeDto): Challenge {
        val type = ChallengeType.valueOf(dto.type)
        val answer = when (type) {
            ChallengeType.MULTIPLE_CHOICE, ChallengeType.IDENTIFY_BUG -> AnswerSpec.Choice(dto.correctIndex!!)
            ChallengeType.ARRANGE_STEPS -> AnswerSpec.Order(dto.steps)
            else -> AnswerSpec.Text(dto.acceptedAnswers, MatchMode.valueOf(dto.matchMode!!))
        }
        return Challenge(
            id = dto.id,
            category = Category.valueOf(dto.category),
            skillId = dto.skillId,
            type = type,
            difficulty = Difficulty.valueOf(dto.difficulty),
            estimatedSeconds = dto.estimatedSeconds,
            title = dto.title,
            description = dto.description,
            instructions = dto.instructions,
            code = dto.code,
            examples = dto.examples,
            testCases = dto.testCases.map { TestCase(it.input, it.expected) },
            options = dto.options,
            answer = answer,
            hints = dto.hints,
            explanation = dto.explanation,
            source = dto.source,
            prerequisites = dto.prerequisites,
        )
    }

    fun validateBosses(dto: BossFileDto, challengeIds: Set<String>): Result<List<Boss>> {
        val problems = mutableListOf<String>()
        val bosses = dto.bosses.mapNotNull { b ->
            val missing = b.challengeIds.filter { it !in challengeIds }
            if (missing.isNotEmpty() || b.challengeIds.size < 3) {
                problems += "bosses.json ${b.id}: needs 3+ valid challenges (missing $missing)"
                null
            } else Boss(b.id, b.title, b.description, b.challengeIds)
        }
        return Result(bosses, problems)
    }

    fun validateProjects(dto: ProjectFileDto, challengeIds: Set<String>): Result<List<Project>> {
        val problems = mutableListOf<String>()
        val projects = dto.projects.mapNotNull { p ->
            val missing = p.challengeIds.filter { it !in challengeIds }
            if (missing.isNotEmpty() || p.challengeIds.isEmpty()) {
                problems += "projects.json ${p.id}: unknown challenges $missing"
                null
            } else Project(p.id, p.title, p.description, p.source, p.challengeIds)
        }
        return Result(projects, problems)
    }

    /** Drops challenge prerequisites that point to rejected or unknown items, reporting each one. */
    fun prerequisiteProblems(challenges: List<Challenge>): List<String> {
        val ids = challenges.map { it.id }.toSet()
        return challenges.flatMap { c -> c.prerequisites.filter { it !in ids }.map { "${c.id}: unknown prerequisite $it" } }
    }

    inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }
}
