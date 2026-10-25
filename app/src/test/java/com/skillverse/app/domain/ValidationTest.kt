package com.skillverse.app.domain

import com.skillverse.app.TestContent
import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.model.MatchMode
import com.skillverse.app.domain.validation.AnswerValidator
import com.skillverse.app.domain.validation.ExecutionRequest
import com.skillverse.app.domain.validation.ExecutionStatus
import com.skillverse.app.domain.validation.LocalExerciseValidationService
import com.skillverse.app.domain.validation.UnavailableRemoteExecutionService
import com.skillverse.app.domain.validation.UserAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerValidatorTest {
    private val c = TestContent.curriculum

    @Test fun normalizationModes() {
        assertEquals(" a\nb", AnswerValidator.normalize("\n a  \r\nb \n\n", MatchMode.TRIMMED))
        assertEquals("pytest --junitxml=x", AnswerValidator.normalize("  pytest    --junitxml=x  ", MatchMode.NORMALIZED))
        assertEquals("abc", AnswerValidator.normalize("  ABC ", MatchMode.CASE_INSENSITIVE))
        assertEquals("[l.strip()forlinlif'E'inl]", AnswerValidator.normalize("[l.strip() for l in l if \"E\" in l]", MatchMode.CODE))
        assertEquals(" x ", AnswerValidator.normalize(" x ", MatchMode.EXACT))
    }

    @Test fun multipleChoiceCorrectAndIncorrect() {
        val mc = c.byId.getValue("py-001")
        val correct = (mc.answer as AnswerSpec.Choice).correctIndex
        assertTrue(AnswerValidator.validate(mc, UserAnswer.Choice(correct)).correct)
        val wrong = AnswerValidator.validate(mc, UserAnswer.Choice((correct + 1) % mc.options.size))
        assertFalse(wrong.correct)
        assertTrue(wrong.feedback!!.isNotBlank())
    }

    @Test fun predictOutputToleratesTrailingWhitespaceButNotWrongContent() {
        val p = c.byId.getValue("py-028") // prints three lines
        assertTrue(AnswerValidator.validate(p, UserAnswer.Text("try  \r\ncleanup\nok\n\n")).correct)
        assertFalse(AnswerValidator.validate(p, UserAnswer.Text("try\nok\ncleanup")).correct)
        val lineHint = AnswerValidator.validate(p, UserAnswer.Text("try cleanup ok"))
        assertTrue(lineHint.feedback!!.contains("line"))
    }

    @Test fun codeAnswersIgnoreSpacingAndQuoteStyle() {
        val puzzle = c.byId.getValue("py-022")
        assertTrue(AnswerValidator.validate(puzzle, UserAnswer.Text("[line.strip() for line in lines if 'ERROR' in line]")).correct)
        assertTrue(AnswerValidator.validate(puzzle, UserAnswer.Text("[line.strip()for line in lines if \"ERROR\" in line]")).correct)
        assertFalse(AnswerValidator.validate(puzzle, UserAnswer.Text("[line for line in lines]")).correct)
    }

    @Test fun blankAndWrongTypedAnswersAreIncompleteNotCorrect() {
        val fill = c.byId.getValue("py-004")
        val blank = AnswerValidator.validate(fill, UserAnswer.Text("   "))
        assertFalse(blank.correct)
        assertEquals("Incomplete answer", blank.verdict)
        assertFalse(AnswerValidator.validate(fill, UserAnswer.Choice(0)).correct)
    }

    @Test fun arrangeRequiresExactOrderAndEveryStep() {
        val a = c.byId.getValue("ta-011")
        val steps = (a.answer as AnswerSpec.Order).steps
        assertTrue(AnswerValidator.validate(a, UserAnswer.Order(steps)).correct)
        val swapped = steps.toMutableList().apply { add(1, removeAt(2)) }
        val wrong = AnswerValidator.validate(a, UserAnswer.Order(swapped))
        assertFalse(wrong.correct)
        assertTrue(wrong.feedback!!.contains("position 2"))
        assertFalse(AnswerValidator.validate(a, UserAnswer.Order(steps.drop(1))).correct)
    }

    @Test fun shuffledStepsNeverStartSolved() {
        c.challenges.filter { it.type == ChallengeType.ARRANGE_STEPS }.forEach { ch ->
            val solved = (ch.answer as AnswerSpec.Order).steps
            val shown = ch.shuffledSteps()
            assertNotEquals("${ch.id} starts solved", solved, shown)
            assertEquals(solved.toSet(), shown.toSet())
            assertEquals(shown, ch.shuffledSteps()) // deterministic
        }
    }

    @Test fun timedOutIsIncorrect() {
        assertFalse(AnswerValidator.validate(c.byId.getValue("py-001"), UserAnswer.TimedOut).correct)
    }

    @Test fun everyBundledAnswerKeyValidatesAgainstItself() {
        for (ch in c.challenges) {
            val answer = when (val spec = ch.answer) {
                is AnswerSpec.Choice -> UserAnswer.Choice(spec.correctIndex)
                is AnswerSpec.Text -> UserAnswer.Text(spec.accepted.first())
                is AnswerSpec.Order -> UserAnswer.Order(spec.steps)
            }
            assertTrue("${ch.id} answer key does not validate", AnswerValidator.validate(ch, answer).correct)
        }
    }
}

class CodeExecutionServiceTest {
    private val c = TestContent.curriculum

    @Test fun localServiceReportsExplicitStatusAndNeverClaimsExecution() = runBlocking {
        val service = LocalExerciseValidationService()
        assertFalse(service.executesCode)
        val ch = c.byId.getValue("py-002")
        val pass = service.execute(ExecutionRequest(ch, UserAnswer.Text("True False")))
        assertEquals(ExecutionStatus.PASSED, pass.status)
        assertFalse(pass.executedCode)
        assertTrue(pass.method.contains("no code is executed"))
        val fail = service.execute(ExecutionRequest(ch, UserAnswer.Text("True True")))
        assertEquals(ExecutionStatus.FAILED, fail.status)
        assertTrue(fail.errors.isNotEmpty())
    }

    @Test fun remotePlaceholderIsUnsupported() = runBlocking {
        val r = UnavailableRemoteExecutionService().execute(ExecutionRequest(c.byId.getValue("py-002"), UserAnswer.Text("x")))
        assertEquals(ExecutionStatus.UNSUPPORTED, r.status)
        assertFalse(r.executedCode)
    }
}
