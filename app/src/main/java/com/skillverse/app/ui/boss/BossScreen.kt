package com.skillverse.app.ui.boss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.BackHeader
import com.skillverse.app.core.designsystem.CodeBlock
import com.skillverse.app.core.designsystem.EmptyState
import com.skillverse.app.core.designsystem.HexEmblem
import com.skillverse.app.core.designsystem.LoadingState
import com.skillverse.app.core.designsystem.Pill
import com.skillverse.app.core.designsystem.PrimaryButton
import com.skillverse.app.core.designsystem.SecondaryButton
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.SvCard
import com.skillverse.app.core.designsystem.SvIcons
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.core.designsystem.XpBar
import com.skillverse.app.core.designsystem.rememberPopInScale
import com.skillverse.app.data.progress.BossOutcome
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Boss
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.progression.ProgressionConfig
import com.skillverse.app.ui.challenge.AnswerArea
import com.skillverse.app.ui.challenge.AnswerInput
import com.skillverse.app.ui.challenge.LevelUpDialog
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.common.curriculumOrNull
import com.skillverse.app.ui.common.rememberResultFeedback
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One boss run: answer every linked challenge in sequence; one miss ends the run (retry any time). */
sealed interface BossState {
    data object Loading : BossState
    data class Unavailable(val message: String) : BossState
    data class Intro(val boss: Boss, val parts: List<Challenge>, val defeatedThisWeek: Boolean) : BossState
    data class InProgress(
        val boss: Boss,
        val parts: List<Challenge>,
        val index: Int,
        val input: AnswerInput,
        val submitting: Boolean = false,
        val lastWrongFeedback: String? = null,
    ) : BossState
    data class Failed(val boss: Boss, val parts: List<Challenge>, val failedAt: Int, val feedback: String?) : BossState
    data class Victory(val boss: Boss, val parts: List<Challenge>, val outcome: BossOutcome) : BossState
}

class BossViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<BossState>(BossState.Loading)
    val state: StateFlow<BossState> = _state.asStateFlow()
    val settings: StateFlow<UserSettings> = container.settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    private var curriculum: Curriculum? = null
    private var partStartMs = 0L

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val c = container.curriculumOrNull()
        curriculum = c
        if (c == null) { _state.value = BossState.Unavailable("Content is unavailable."); return }
        val today = container.timeProvider.today()
        val row = container.progressRepository.ensureWeeklyBoss(c, today)
        val boss = row?.let { r -> c.bosses.firstOrNull { it.id == r.challengeId } }
        if (boss == null) {
            _state.value = BossState.Unavailable("No boss is available yet. Unlock the second skill in the tree to face your first boss.")
            return
        }
        val parts = boss.challengeIds.mapNotNull { c.byId[it] }
        val defeated = container.progressRepository.observeBossDefeated(boss.id, today).first()
        _state.value = BossState.Intro(boss, parts, defeated)
    }

    fun start() {
        val s = _state.value
        val (boss, parts) = when (s) {
            is BossState.Intro -> s.boss to s.parts
            is BossState.Failed -> s.boss to s.parts
            is BossState.Victory -> s.boss to s.parts
            else -> return
        }
        partStartMs = System.currentTimeMillis()
        _state.value = BossState.InProgress(boss, parts, 0, AnswerInput.initial(parts.first()))
    }

    fun onInput(input: AnswerInput) = _state.update { if (it is BossState.InProgress && !it.submitting) it.copy(input = input) else it }

    fun submit() {
        val s = _state.value as? BossState.InProgress ?: return
        val c = curriculum ?: return
        val challenge = s.parts[s.index]
        val answer = s.input.toUserAnswer(challenge.type) ?: return
        if (s.submitting) return
        _state.value = s.copy(submitting = true)
        viewModelScope.launch {
            val outcome = try {
                container.progressRepository.submit(c, challenge, answer, AttemptContext.BOSS, System.currentTimeMillis() - partStartMs)
            } catch (e: Exception) {
                _state.value = s.copy(submitting = false, lastWrongFeedback = "Your answer could not be saved. Try again.")
                return@launch
            }
            if (!outcome.correct) {
                _state.value = BossState.Failed(s.boss, s.parts, s.index, outcome.validation.feedback)
                return@launch
            }
            if (s.index == s.parts.lastIndex) {
                val result = container.progressRepository.completeBoss(c, s.boss.id, container.timeProvider.today())
                _state.value = BossState.Victory(s.boss, s.parts, result)
            } else {
                partStartMs = System.currentTimeMillis()
                _state.value = BossState.InProgress(s.boss, s.parts, s.index + 1, AnswerInput.initial(s.parts[s.index + 1]))
            }
        }
    }
}

@Composable
fun BossScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: BossViewModel = viewModel(factory = containerFactory { BossViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val feedback = rememberResultFeedback(settings)
    Column(Modifier.fillMaxSize()) {
        BackHeader("Weekly boss", onBack)
        when (val s = state) {
            BossState.Loading -> LoadingState()
            is BossState.Unavailable -> EmptyState("Boss locked", s.message, icon = SvIcons.Swords, actionLabel = "Go back", onAction = onBack)
            is BossState.Intro -> BossIntro(s, vm::start)
            is BossState.InProgress -> BossPart(s, vm::onInput, vm::submit)
            is BossState.Failed -> {
                LaunchedEffect(s) { feedback.play(false) }
                BossFailed(s, vm::start, onBack)
            }
            is BossState.Victory -> {
                LaunchedEffect(s) { feedback.play(true) }
                BossVictory(s, onBack)
                var dismissed by remember(s) { mutableStateOf(false) }
                if (!dismissed && s.outcome.levelAfter > s.outcome.levelBefore) {
                    LevelUpDialog(s.outcome.levelAfter, s.outcome.newAchievements) { dismissed = true }
                }
            }
        }
    }
}

@Composable
private fun BossIntro(s: BossState.Intro, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SvCard(brush = SvTheme.colors.heroBrush) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HexEmblem("Boss", size = 64.dp, colors = listOf(SvTheme.colors.danger, MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(Spacing.lg))
                Column {
                    Text(s.boss.title, style = MaterialTheme.typography.headlineSmall)
                    Text("${s.parts.size} linked challenges · +${ProgressionConfig.BOSS_XP} XP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(s.boss.description, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Rules", style = MaterialTheme.typography.titleMedium)
        Text(
            "Answer every challenge in a row. One wrong answer ends the run, but you can retry as often as you like. " +
                "The boss bonus is awarded once per week; each challenge still awards its normal XP the first time you complete it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.parts.forEachIndexed { i, ch ->
            Text("${i + 1}. ${ch.title} · ${ch.category.shortLabel}", style = MaterialTheme.typography.bodyMedium)
        }
        if (s.defeatedThisWeek) Pill("Defeated this week: replay for practice", SvTheme.colors.success)
        PrimaryButton(if (s.defeatedThisWeek) "Replay boss" else "Start boss run", onStart, Modifier.fillMaxWidth().testTag("boss_start"), icon = SvIcons.Swords)
    }
}

@Composable
private fun BossPart(s: BossState.InProgress, onInput: (AnswerInput) -> Unit, onSubmit: () -> Unit) {
    val ch = s.parts[s.index]
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Part ${s.index + 1} of ${s.parts.size}", style = MaterialTheme.typography.labelLarge, color = SvTheme.colors.danger)
        XpBar(s.index.toFloat() / s.parts.size, brush = androidx.compose.ui.graphics.SolidColor(SvTheme.colors.danger), description = "Boss progress")
        Text(ch.title, style = MaterialTheme.typography.headlineSmall)
        Text(ch.description, style = MaterialTheme.typography.bodyLarge)
        ch.code?.takeIf { it.isNotBlank() }?.let { CodeBlock(it) }
        Text(ch.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnswerArea(ch, s.input, onInput, enabled = !s.submitting, reviewMode = false, showSolution = false, lastAnswerCorrect = null)
        s.lastWrongFeedback?.let { Text(it, color = SvTheme.colors.danger) }
        PrimaryButton(
            if (s.submitting) "Checking…" else "Submit",
            onSubmit,
            Modifier.fillMaxWidth().testTag("boss_submit"),
            enabled = !s.submitting && s.input.toUserAnswer(ch.type) != null,
        )
        if (ch.type in setOf(ChallengeType.PREDICT_OUTPUT, ChallengeType.FILL_BLANK, ChallengeType.CODE_PUZZLE)) {
            Text("Answers are checked against the challenge's rules. Code is not executed.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BossFailed(s: BossState.Failed, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EmptyState(
            "The boss survived",
            "You were stopped at part ${s.failedAt + 1}: ${s.parts[s.failedAt].title}. ${s.feedback ?: ""} The challenge was added to your review queue.",
            icon = SvIcons.Swords,
        )
        Text(
            "Study it in the review queue, then try again. Parts you answered correctly already earned their XP.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton("Retry boss", onRetry, Modifier.fillMaxWidth().testTag("boss_retry"))
        SecondaryButton("Leave", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BossVictory(s: BossState.Victory, onBack: () -> Unit) {
    val scale = rememberPopInScale(s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Spacer(Modifier.height(Spacing.xl))
        HexEmblem("WIN", size = 112.dp, colors = listOf(SvTheme.colors.warning, SvTheme.colors.danger), modifier = Modifier.scale(scale))
        Text("${s.boss.title} defeated!", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (s.outcome.awarded) "+${s.outcome.xpAwarded} XP boss bonus" else "Bonus already earned this week. Great practice run!",
            style = MaterialTheme.typography.titleMedium,
            color = SvTheme.colors.xp,
            modifier = Modifier.testTag("boss_result"),
        )
        s.outcome.newAchievements.forEach { Pill("Badge: ${it.title}", SvTheme.colors.warning) }
        PrimaryButton("Back to base", onBack, Modifier.fillMaxWidth())
    }
}
