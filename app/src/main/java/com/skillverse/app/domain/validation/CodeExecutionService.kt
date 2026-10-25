package com.skillverse.app.domain.validation

import com.skillverse.app.domain.model.Challenge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class ExecutionStatus {
    /** The answer satisfied the validation specification. */
    PASSED,

    /** The answer was checked and did not satisfy the specification. */
    FAILED,

    /** Checking took longer than the request's timeout. */
    TIMED_OUT,

    /** This service cannot handle the request (for example, remote execution is not configured). */
    UNSUPPORTED,

    /** An unexpected error occurred while checking. */
    ERROR,
}

data class ExecutionRequest(
    val challenge: Challenge,
    val answer: UserAnswer,
    val timeoutMillis: Long = 2_000,
)

data class ExecutionResult(
    val status: ExecutionStatus,
    /** Output shown to the user. For local validation this is the verdict, never program output. */
    val output: String,
    val errors: List<String>,
    val durationMillis: Long,
    /** True only if user code was actually executed. Always false for [LocalExerciseValidationService]. */
    val executedCode: Boolean,
    /** Human-readable name of the checking method, shown in the UI for transparency. */
    val method: String,
    val validation: ValidationResult?,
)

/**
 * Abstraction over how a submitted answer is evaluated.
 *
 * The shipped implementation, [LocalExerciseValidationService], validates answers deterministically
 * against the bundled specification and does not run code. A future isolated Python backend can
 * implement this interface; it must run server-side with process isolation, CPU/memory limits,
 * execution timeouts, no network access and no access to secrets or host files. Untrusted code is
 * never executed inside the app process.
 */
interface CodeExecutionService {
    val executesCode: Boolean
    suspend fun execute(request: ExecutionRequest): ExecutionResult
}

class LocalExerciseValidationService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CodeExecutionService {
    override val executesCode: Boolean = false

    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val start = System.nanoTime()
        return try {
            val validation = withTimeout(request.timeoutMillis) {
                withContext(dispatcher) { AnswerValidator.validate(request.challenge, request.answer) }
            }
            ExecutionResult(
                status = if (validation.correct) ExecutionStatus.PASSED else ExecutionStatus.FAILED,
                output = validation.verdict,
                errors = listOfNotNull(validation.feedback?.takeIf { !validation.correct }),
                durationMillis = (System.nanoTime() - start) / 1_000_000,
                executedCode = false,
                method = METHOD,
                validation = validation,
            )
        } catch (e: TimeoutCancellationException) {
            ExecutionResult(ExecutionStatus.TIMED_OUT, "Checking timed out", listOf("Validation exceeded ${request.timeoutMillis} ms."),
                (System.nanoTime() - start) / 1_000_000, false, METHOD, null)
        } catch (e: IllegalStateException) {
            ExecutionResult(ExecutionStatus.ERROR, "Could not check this answer", listOf("The challenge data is inconsistent."),
                (System.nanoTime() - start) / 1_000_000, false, METHOD, null)
        }
    }

    companion object {
        const val METHOD = "Answer check against the challenge's validation rules (no code is executed)"
    }
}

/** Placeholder for a future sandboxed backend; reports UNSUPPORTED so callers can fall back safely. */
class UnavailableRemoteExecutionService : CodeExecutionService {
    override val executesCode: Boolean = false
    override suspend fun execute(request: ExecutionRequest) = ExecutionResult(
        status = ExecutionStatus.UNSUPPORTED,
        output = "Remote execution is not configured",
        errors = listOf("This build has no isolated execution backend; answers are validated locally."),
        durationMillis = 0,
        executedCode = false,
        method = "Remote isolated execution (not configured)",
        validation = null,
    )
}
