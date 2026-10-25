package com.skillverse.app.domain.model

import com.skillverse.app.domain.progression.ProgressionConfig

enum class Category(val label: String, val shortLabel: String) {
    PYTHON("Python coding", "Python"),
    LOGIC("Programming logic", "Logic"),
    DEBUGGING("Debugging", "Debugging"),
    TEST_AUTOMATION("Test automation", "Testing"),
    API_TESTING("API testing", "API"),
    LINUX("Linux & command line", "Linux"),
    GIT("Git & version control", "Git"),
    EMBEDDED("Embedded & firmware", "Embedded"),
    DEVOPS("DevOps & CI/CD", "CI/CD"),
}

/** How a challenge type is rewarded. Values live in [ProgressionConfig]. */
enum class RewardClass { QUIZ, CODING, DEBUGGING }

enum class ChallengeType(val label: String, val rewardClass: RewardClass) {
    MULTIPLE_CHOICE("Multiple choice", RewardClass.QUIZ),
    PREDICT_OUTPUT("Predict the output", RewardClass.CODING),
    FILL_BLANK("Fill in the blank", RewardClass.CODING),
    CODE_PUZZLE("Coding puzzle", RewardClass.CODING),
    IDENTIFY_BUG("Identify the bug", RewardClass.DEBUGGING),
    ARRANGE_STEPS("Arrange the steps", RewardClass.DEBUGGING),
}

enum class Difficulty(val label: String, val rank: Int) {
    EASY("Easy", 0),
    MEDIUM("Medium", 1),
    HARD("Hard", 2),
}

/** How a typed answer is compared with the accepted answers. Mirrors tools/validate_content.py. */
enum class MatchMode {
    /** Character-for-character. */
    EXACT,

    /** Line endings normalised, trailing spaces and surrounding blank lines removed. */
    TRIMMED,

    /** TRIMMED plus each line trimmed and runs of spaces/tabs collapsed to one space. */
    NORMALIZED,

    /** NORMALIZED and case-insensitive. */
    CASE_INSENSITIVE,

    /** All whitespace removed and double quotes treated as single quotes (for short code answers). */
    CODE,
}

sealed interface AnswerSpec {
    data class Choice(val correctIndex: Int) : AnswerSpec
    data class Text(val accepted: List<String>, val mode: MatchMode) : AnswerSpec
    data class Order(val steps: List<String>) : AnswerSpec
}

data class TestCase(val input: String, val expected: String)

data class Challenge(
    val id: String,
    val category: Category,
    val skillId: String,
    val type: ChallengeType,
    val difficulty: Difficulty,
    val estimatedSeconds: Int,
    val title: String,
    val description: String,
    val instructions: String,
    val code: String?,
    val examples: List<String>,
    val testCases: List<TestCase>,
    val options: List<String>,
    val answer: AnswerSpec,
    val hints: List<String>,
    val explanation: String,
    val source: String,
    val prerequisites: List<String>,
) {
    val xp: Int get() = ProgressionConfig.xpFor(type)
    val estimatedMinutes: Int get() = ((estimatedSeconds + 59) / 60).coerceAtLeast(1)

    /** Steps for ARRANGE_STEPS in the order shown to the user (never the solved order). */
    fun shuffledSteps(): List<String> {
        val steps = (answer as? AnswerSpec.Order)?.steps ?: return emptyList()
        return StableShuffle.shuffleAwayFromSolution(steps, id)
    }
}

data class SkillNode(
    val id: String,
    val title: String,
    val glyph: String,
    val description: String,
    val prerequisites: List<String>,
    val order: Int,
)

data class Boss(val id: String, val title: String, val description: String, val challengeIds: List<String>)

data class Project(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val challengeIds: List<String>,
)

data class Curriculum(
    val contentVersion: Int,
    val challenges: List<Challenge>,
    val skills: List<SkillNode>,
    val unlockThresholdPercent: Int,
    val bosses: List<Boss>,
    val projects: List<Project>,
    /** Problems found while loading (rejected items). Empty for a healthy bundle. */
    val diagnostics: List<String> = emptyList(),
) {
    val byId: Map<String, Challenge> = challenges.associateBy { it.id }
    private val bySkill: Map<String, List<Challenge>> = challenges.groupBy { it.skillId }
    val skillsById: Map<String, SkillNode> = skills.associateBy { it.id }

    fun challengesForSkill(skillId: String): List<Challenge> = bySkill[skillId].orEmpty()
    fun challengesForCategory(category: Category): List<Challenge> = challenges.filter { it.category == category }
}

/** Where an attempt happened. Stored as text in the attempts table. */
enum class AttemptContext { PRACTICE, DAILY, REVIEW, BOSS, TIMED }

/** Deterministic helpers used for content presentation and mission selection. */
object StableShuffle {
    /** 32-bit FNV-1a hash: stable across devices, JVMs and app restarts. */
    fun fnv1a(text: String): Int {
        var hash = 0x811C9DC5.toInt()
        for (ch in text) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        return hash
    }

    fun <T> shuffle(items: List<T>, seed: String): List<T> {
        val rnd = java.util.Random(fnv1a(seed).toLong())
        return items.shuffled(rnd)
    }

    /** Shuffles deterministically, guaranteeing the result differs from the input when possible. */
    fun <T> shuffleAwayFromSolution(items: List<T>, seed: String): List<T> {
        if (items.size < 2 || items.distinct().size < 2) return items
        var attempt = 0
        var result = shuffle(items, seed)
        while (result == items && attempt < 10) {
            attempt++
            result = shuffle(items, "$seed#$attempt")
        }
        return if (result == items) items.drop(1) + items.first() else result
    }
}
