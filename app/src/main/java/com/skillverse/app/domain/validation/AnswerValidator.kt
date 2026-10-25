package com.skillverse.app.domain.validation

import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.MatchMode

sealed interface UserAnswer {
    data class Choice(val index: Int) : UserAnswer
    data class Text(val value: String) : UserAnswer
    data class Order(val steps: List<String>) : UserAnswer

    /** Timed mode expired before submission. */
    data object TimedOut : UserAnswer

    fun storageText(): String = when (this) {
        is Choice -> "choice:$index"
        is Text -> value.take(2000)
        is Order -> steps.joinToString(" | ").take(2000)
        TimedOut -> "<timed out>"
    }
}

data class ValidationResult(
    val correct: Boolean,
    /** Short verdict shown immediately after submission. */
    val verdict: String,
    /** Specific feedback about this answer (why it is wrong), without revealing the solution. */
    val feedback: String?,
    /** The solution, shown only on request after submission. */
    val solution: String,
)

/**
 * Deterministic answer checking against the content's validation specification.
 * This compares answers; it never executes user code.
 */
object AnswerValidator {

    fun normalize(text: String, mode: MatchMode): String = when (mode) {
        MatchMode.EXACT -> text
        MatchMode.TRIMMED -> trimmed(text)
        MatchMode.NORMALIZED -> collapsed(text)
        MatchMode.CASE_INSENSITIVE -> collapsed(text).lowercase()
        MatchMode.CODE -> text.replace(WHITESPACE, "").replace('"', '\'')
    }

    private val SPACES = Regex("[ \\t]+")
    private val WHITESPACE = Regex("\\s+")

    /** TRIMMED, then each line trimmed on both sides and inner runs of spaces/tabs collapsed. */
    private fun collapsed(text: String): String = trimmed(text).split('\n').joinToString("\n") { it.trim().replace(SPACES, " ") }

    private fun trimmed(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trimEnd() }
        val first = lines.indexOfFirst { it.isNotEmpty() }
        if (first == -1) return ""
        val last = lines.indexOfLast { it.isNotEmpty() }
        return lines.subList(first, last + 1).joinToString("\n")
    }

    fun solutionText(challenge: Challenge): String = when (val spec = challenge.answer) {
        is AnswerSpec.Choice -> challenge.options.getOrElse(spec.correctIndex) { "" }
        is AnswerSpec.Text -> spec.accepted.first()
        is AnswerSpec.Order -> spec.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    }

    fun validate(challenge: Challenge, answer: UserAnswer): ValidationResult {
        val solution = solutionText(challenge)
        if (answer is UserAnswer.TimedOut) {
            return ValidationResult(false, "Time's up", "The timer ran out before you submitted. Retry without the clock or try again.", solution)
        }
        return when (val spec = challenge.answer) {
            is AnswerSpec.Choice -> {
                val index = (answer as? UserAnswer.Choice)?.index
                    ?: return invalid("Choose one of the options.", solution)
                if (index == spec.correctIndex) ok(solution)
                else ValidationResult(false, "Not quite", "That option is not the right one. Take a hint or reveal the correct option, then retry.", solution)
            }
            is AnswerSpec.Text -> {
                val raw = (answer as? UserAnswer.Text)?.value ?: return invalid("Type an answer first.", solution)
                if (raw.isBlank()) return invalid("Type an answer first.", solution)
                val given = normalize(raw, spec.mode)
                if (spec.accepted.any { normalize(it, spec.mode) == given }) ok(solution)
                else ValidationResult(false, "Not quite", textFeedback(raw, spec), solution)
            }
            is AnswerSpec.Order -> {
                val steps = (answer as? UserAnswer.Order)?.steps ?: return invalid("Arrange the steps first.", solution)
                if (steps.size != spec.steps.size || steps.toSet() != spec.steps.toSet()) {
                    return invalid("Use every step exactly once.", solution)
                }
                if (steps == spec.steps) ok(solution) else {
                    val firstWrong = steps.indices.first { steps[it] != spec.steps[it] }
                    val correctPrefix = firstWrong
                    ValidationResult(
                        false, "Not quite",
                        if (correctPrefix == 0) "The first step is not in the right place."
                        else "The first $correctPrefix step${if (correctPrefix == 1) " is" else "s are"} in the right order; position ${firstWrong + 1} is not.",
                        solution,
                    )
                }
            }
        }
    }

    private fun textFeedback(raw: String, spec: AnswerSpec.Text): String {
        val expected = spec.accepted.first()
        val givenLines = normalize(raw, MatchMode.TRIMMED).lines().size
        val expectedLines = normalize(expected, MatchMode.TRIMMED).lines().size
        return when {
            spec.mode != MatchMode.CODE && givenLines != expectedLines ->
                "Your answer has $givenLines line${if (givenLines == 1) "" else "s"}; the expected answer has $expectedLines."
            spec.mode != MatchMode.CASE_INSENSITIVE && spec.mode != MatchMode.CODE &&
                normalize(raw, MatchMode.CASE_INSENSITIVE) == normalize(expected, MatchMode.CASE_INSENSITIVE) ->
                "Very close: check upper and lower case."
            else -> "That does not match the expected answer. Use a hint or read the explanation, then retry."
        }
    }

    private fun ok(solution: String) = ValidationResult(true, "Correct!", null, solution)
    private fun invalid(msg: String, solution: String) = ValidationResult(false, "Incomplete answer", msg, solution)
}
