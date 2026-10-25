package com.skillverse.app.ui.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillverse.app.AppContainer
import com.skillverse.app.data.progress.SubmissionOutcome
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.domain.validation.UserAnswer
import com.skillverse.app.ui.common.curriculumOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the learner has entered so far, independent of challenge type. */
data class AnswerInput(
    val choice: Int? = null,
    val text: String = "",
    val order: List<String> = emptyList(),
) {
    fun toUserAnswer(type: ChallengeType): UserAnswer? = when (type) {
        ChallengeType.MULTIPLE_CHOICE, ChallengeType.IDENTIFY_BUG -> choice?.let { UserAnswer.Choice(it) }
        ChallengeType.ARRANGE_STEPS -> UserAnswer.Order(order)
        else -> text.takeIf { it.isNotBlank() }?.let { UserAnswer.Text(it) }
    }

    companion object {
        fun initial(challenge: Challenge) = AnswerInput(order = challenge.shuffledSteps())
    }
}

enum class Phase { ANSWERING, SUBMITTING, RESULT }

data class ChallengeUiState(
    val challenge: Challenge,
    val context: AttemptContext,
    val input: AnswerInput,
    val phase: Phase = Phase.ANSWERING,
    val hintsShown: Int = 0,
    val outcome: SubmissionOutcome? = null,
    val solutionRevealed: Boolean = false,
    val completedBefore: Boolean = false,
    val attemptsThisSession: Int = 0,
    val timerTotalSeconds: Int? = null,
    val timerRemainingSeconds: Int? = null,
    val errorMessage: String? = null,
    val nextChallengeId: String? = null,
) {
    val canSubmit: Boolean get() = phase == Phase.ANSWERING && input.toUserAnswer(challenge.type) != null
}

sealed interface ChallengeScreenState {
    data object Loading : ChallengeScreenState
    data class NotFound(val id: String) : ChallengeScreenState
    data class Locked(val challenge: Challenge, val reason: String) : ChallengeScreenState
    data class Ready(val ui: ChallengeUiState) : ChallengeScreenState
}

class ChallengeViewModel(
    private val container: AppContainer,
    private val challengeId: String,
    private val requestedContext: AttemptContext,
) : ViewModel() {
    private val _state = MutableStateFlow<ChallengeScreenState>(ChallengeScreenState.Loading)
    val state: StateFlow<ChallengeScreenState> = _state.asStateFlow()

    val settings: StateFlow<UserSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private var curriculum: Curriculum? = null
    private var startedAtMs = 0L
    private var timerJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val c = container.curriculumOrNull()
        curriculum = c
        val challenge = c?.byId?.get(challengeId)
        if (c == null || challenge == null) {
            _state.value = ChallengeScreenState.NotFound(challengeId)
            return
        }
        val completed = container.progressRepository.observeCompletedIds().first()
        val skills = SkillTreeCalculator.compute(c, completed)
        val status = skills.firstOrNull { it.node.id == challenge.skillId }
        val bypassLock = requestedContext == AttemptContext.REVIEW || requestedContext == AttemptContext.DAILY || challenge.id in completed
        if (status != null && !status.unlocked && !bypassLock) {
            _state.value = ChallengeScreenState.Locked(challenge, status.unlockHint ?: "Complete the previous skill first.")
            return
        }
        val timed = requestedContext == AttemptContext.TIMED ||
            (requestedContext == AttemptContext.PRACTICE && container.settingsRepository.settings.first().timedMode)
        val context = if (timed) AttemptContext.TIMED else requestedContext
        _state.value = ChallengeScreenState.Ready(
            ChallengeUiState(
                challenge = challenge,
                context = context,
                input = AnswerInput.initial(challenge),
                completedBefore = challenge.id in completed,
                timerTotalSeconds = if (timed) challenge.estimatedSeconds else null,
                timerRemainingSeconds = if (timed) challenge.estimatedSeconds else null,
            ),
        )
        startAttemptClock()
    }

    private fun startAttemptClock() {
        startedAtMs = System.currentTimeMillis()
        timerJob?.cancel()
        val ui = (state.value as? ChallengeScreenState.Ready)?.ui ?: return
        if (ui.timerTotalSeconds == null) return
        timerJob = viewModelScope.launch {
            var remaining = ui.timerTotalSeconds
            while (isActive && remaining > 0) {
                delay(1_000)
                remaining--
                updateUi { if (it.phase == Phase.ANSWERING) it.copy(timerRemainingSeconds = remaining) else it }
            }
            val current = (state.value as? ChallengeScreenState.Ready)?.ui
            if (current != null && current.phase == Phase.ANSWERING) submitAnswer(UserAnswer.TimedOut)
        }
    }

    private inline fun updateUi(transform: (ChallengeUiState) -> ChallengeUiState) {
        _state.update { s -> if (s is ChallengeScreenState.Ready) s.copy(ui = transform(s.ui)) else s }
    }

    fun onInput(input: AnswerInput) = updateUi { if (it.phase == Phase.ANSWERING) it.copy(input = input) else it }

    fun showNextHint() = updateUi { it.copy(hintsShown = (it.hintsShown + 1).coerceAtMost(it.challenge.hints.size)) }

    fun revealSolution() = updateUi { if (it.phase == Phase.RESULT) it.copy(solutionRevealed = true) else it }

    fun submit() {
        val ui = (state.value as? ChallengeScreenState.Ready)?.ui ?: return
        val answer = ui.input.toUserAnswer(ui.challenge.type) ?: return
        submitAnswer(answer)
    }

    private fun submitAnswer(answer: UserAnswer) {
        val ui = (state.value as? ChallengeScreenState.Ready)?.ui ?: return
        val c = curriculum ?: return
        if (ui.phase != Phase.ANSWERING) return // guards double taps and timer races
        timerJob?.cancel()
        updateUi { it.copy(phase = Phase.SUBMITTING, errorMessage = null) }
        viewModelScope.launch {
            try {
                val outcome = container.progressRepository.submit(
                    c, ui.challenge, answer, ui.context, System.currentTimeMillis() - startedAtMs,
                )
                val next = if (outcome.correct) nextChallenge(c, ui.challenge) else null
                updateUi {
                    it.copy(
                        phase = Phase.RESULT,
                        outcome = outcome,
                        attemptsThisSession = it.attemptsThisSession + 1,
                        completedBefore = it.completedBefore || outcome.correct,
                        nextChallengeId = next,
                    )
                }
            } catch (e: Exception) {
                updateUi { it.copy(phase = Phase.ANSWERING, errorMessage = "Your answer could not be saved. Please try again.") }
            }
        }
    }

    /** Resets the input for another attempt; the challenge can be retried any number of times. */
    fun retry() {
        updateUi {
            it.copy(
                input = AnswerInput.initial(it.challenge),
                phase = Phase.ANSWERING,
                outcome = null,
                solutionRevealed = false,
                hintsShown = 0,
                timerRemainingSeconds = it.timerTotalSeconds,
            )
        }
        startAttemptClock()
    }

    private suspend fun nextChallenge(c: Curriculum, current: Challenge): String? {
        if (requestedContext == AttemptContext.DAILY) {
            val today = container.timeProvider.today()
            return container.progressRepository.observeMissions(today).first()
                .firstOrNull { it.completedAtEpochMs == null && it.challengeId != current.id }?.challengeId
        }
        if (requestedContext == AttemptContext.REVIEW) {
            return container.progressRepository.observeRevisionQueue().first()
                .firstOrNull { it.challengeId != current.id && it.challengeId in c.byId }?.challengeId
        }
        val completed = container.progressRepository.observeCompletedIds().first()
        return c.challengesForSkill(current.skillId).firstOrNull { it.id !in completed && it.id != current.id }?.id
    }

    val nextContext: AttemptContext get() = when (requestedContext) {
        AttemptContext.DAILY, AttemptContext.REVIEW -> requestedContext
        else -> AttemptContext.PRACTICE
    }
}
