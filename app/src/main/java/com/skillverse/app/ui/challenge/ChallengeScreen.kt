package com.skillverse.app.ui.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.BackHeader
import com.skillverse.app.core.designsystem.CodeBlock
import com.skillverse.app.core.designsystem.CodeTextStyle
import com.skillverse.app.core.designsystem.DifficultyPill
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
import com.skillverse.app.data.progress.SubmissionOutcome
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.ChallengeType
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.validation.LocalExerciseValidationService
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.common.rememberResultFeedback

@Composable
fun ChallengeScreen(
    container: AppContainer,
    challengeId: String,
    context: AttemptContext,
    onBack: () -> Unit,
    openChallenge: (String, AttemptContext) -> Unit,
) {
    val vm: ChallengeViewModel = viewModel(
        key = "challenge_${challengeId}_${context.name}",
        factory = containerFactory { ChallengeViewModel(container, challengeId, context) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val feedback = rememberResultFeedback(settings)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            ChallengeScreenState.Loading -> {
                BackHeader("Challenge", onBack)
                LoadingState(label = "Loading challenge")
            }
            is ChallengeScreenState.NotFound -> {
                BackHeader("Challenge", onBack)
                EmptyState("Challenge not found", "This challenge is not part of the installed content (id ${s.id}).", actionLabel = "Go back", onAction = onBack)
            }
            is ChallengeScreenState.Locked -> {
                BackHeader(s.challenge.title, onBack)
                EmptyState("Locked", s.reason, icon = Icons.Filled.Lock, actionLabel = "Go back", onAction = onBack)
            }
            is ChallengeScreenState.Ready -> {
                val ui = s.ui
                LaunchedEffect(ui.outcome) { ui.outcome?.let { feedback.play(it.correct) } }
                BackHeader(ui.challenge.category.shortLabel + " · " + ui.challenge.type.label, onBack) {
                    if (ui.timerRemainingSeconds != null && ui.phase != Phase.RESULT) TimerChip(ui.timerRemainingSeconds)
                }
                ChallengeBody(
                    ui = ui,
                    onInput = vm::onInput,
                    onHint = vm::showNextHint,
                    onSubmit = vm::submit,
                    onRetry = vm::retry,
                    onReveal = vm::revealSolution,
                    onNext = ui.nextChallengeId?.let { id -> { openChallenge(id, vm.nextContext) } },
                    onDone = onBack,
                )
                ui.outcome?.takeIf { it.leveledUp }?.let { outcome ->
                    var dismissed by remember(outcome) { mutableStateOf(false) }
                    if (!dismissed) LevelUpDialog(outcome.levelAfter, outcome.newAchievements) { dismissed = true }
                }
            }
        }
    }
}

@Composable
private fun TimerChip(seconds: Int) {
    val color = if (seconds <= 15) SvTheme.colors.danger else MaterialTheme.colorScheme.secondary
    Pill(
        "%d:%02d".format(seconds / 60, seconds % 60),
        color,
        icon = SvIcons.Timer,
        modifier = Modifier.padding(end = Spacing.md).testTag("timer"),
    )
}

@Composable
private fun ChallengeBody(
    ui: ChallengeUiState,
    onInput: (AnswerInput) -> Unit,
    onHint: () -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onReveal: () -> Unit,
    onNext: (() -> Unit)?,
    onDone: () -> Unit,
) {
    val c = ui.challenge
    val scroll = rememberScrollState()
    val outcome = ui.outcome
    LaunchedEffect(ui.phase) { if (ui.phase == Phase.RESULT) scroll.animateScrollTo(scroll.maxValue) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(scroll).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(c.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            DifficultyPill(c.difficulty)
            Pill("${c.estimatedMinutes} min", MaterialTheme.colorScheme.secondary, icon = SvIcons.Timer)
            Pill("${c.xp} XP", SvTheme.colors.xp, icon = SvIcons.Bolt)
            if (ui.completedBefore) Pill("Completed", SvTheme.colors.success, icon = Icons.Filled.CheckCircle)
        }
        Text(c.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)

        val shownCode = c.code?.takeIf { it.isNotBlank() && c.type != ChallengeType.CODE_PUZZLE }
        if (shownCode != null) CodeBlock(shownCode)

        if (c.type == ChallengeType.CODE_PUZZLE) {
            c.code?.let { starter -> CodeBlock(starter) }
            if (c.testCases.isNotEmpty()) TestCases(c)
        }
        c.examples.forEach { ex -> Text("Example: $ex", style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        SvCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
            Text("INSTRUCTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xs))
            Text(c.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (c.type in setOf(ChallengeType.PREDICT_OUTPUT, ChallengeType.FILL_BLANK, ChallengeType.CODE_PUZZLE)) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "Answers are checked against the challenge's rules. Your code is not executed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnswerArea(
            challenge = c,
            input = ui.input,
            onInput = onInput,
            enabled = ui.phase == Phase.ANSWERING,
            reviewMode = ui.phase == Phase.RESULT,
            showSolution = ui.solutionRevealed || (outcome?.correct == true),
            lastAnswerCorrect = outcome?.correct,
        )

        Hints(c, ui.hintsShown, onHint, enabled = ui.phase != Phase.SUBMITTING)

        ui.errorMessage?.let {
            Text(it, color = SvTheme.colors.danger, style = MaterialTheme.typography.bodyMedium)
        }

        if (ui.phase != Phase.RESULT) {
            PrimaryButton(
                text = if (ui.phase == Phase.SUBMITTING) "Checking…" else "Submit answer",
                onClick = onSubmit,
                enabled = ui.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag("submit"),
            )
        }

        AnimatedVisibility(visible = outcome != null && ui.phase == Phase.RESULT) {
            if (outcome != null) ResultPanel(c, outcome, ui.solutionRevealed, onRetry, onReveal, onNext, onDone)
        }
    }
}

@Composable
private fun TestCases(c: Challenge) {
    SvCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
        Text("TEST CASES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        c.testCases.forEachIndexed { i, t ->
            Spacer(Modifier.height(Spacing.sm))
            Text("Case ${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Input: ${t.input}", style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurface)
            Text("Expected: ${t.expected}", style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Hints(c: Challenge, shown: Int, onHint: () -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        c.hints.take(shown).forEachIndexed { i, hint ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(SvTheme.colors.warning.copy(alpha = 0.10f)).padding(Spacing.md),
            ) {
                Icon(SvIcons.Lightbulb, contentDescription = null, tint = SvTheme.colors.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Hint ${i + 1}: $hint", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (shown < c.hints.size) {
            TextButton(onClick = onHint, enabled = enabled, modifier = Modifier.testTag("hint")) {
                Icon(SvIcons.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(if (shown == 0) "Show a hint" else "Show another hint (${c.hints.size - shown} left)")
            }
        }
    }
}

@Composable
private fun ResultPanel(
    c: Challenge,
    outcome: SubmissionOutcome,
    solutionRevealed: Boolean,
    onRetry: () -> Unit,
    onReveal: () -> Unit,
    onNext: (() -> Unit)?,
    onDone: () -> Unit,
) {
    val good = outcome.correct
    val accent = if (good) SvTheme.colors.success else SvTheme.colors.danger
    val scale = rememberPopInScale(outcome)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("result_panel")) {
        SvCard(Modifier.scale(scale).semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (good) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(outcome.validation.verdict, style = MaterialTheme.typography.titleLarge, color = accent, modifier = Modifier.testTag("verdict"))
                    when {
                        good && outcome.xpAwarded > 0 -> Text("+${outcome.xpAwarded} XP earned", style = MaterialTheme.typography.titleSmall, color = SvTheme.colors.xp)
                        good && outcome.alreadyEarned -> Text("XP for this challenge was already earned. Practice still counts toward accuracy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> outcome.validation.feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    if (outcome.masteryBonusXp > 0) Text("+${outcome.masteryBonusXp} XP mastery milestone!", style = MaterialTheme.typography.titleSmall, color = SvTheme.colors.xp)
                    if (outcome.completedDailyMission) Text("Daily mission complete", style = MaterialTheme.typography.labelLarge, color = SvTheme.colors.success)
                }
            }
            if (good && outcome.totalXpAfter > 0) {
                Spacer(Modifier.height(Spacing.md))
                val progress = com.skillverse.app.domain.progression.LevelCalculator.progress(outcome.totalXpAfter)
                XpBar(progress.fraction, description = "Level ${progress.level} progress")
                Text(
                    "Level ${progress.level} · ${progress.xpRemaining} XP to next level",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        if (!good && !solutionRevealed && c.type != ChallengeType.MULTIPLE_CHOICE && c.type != ChallengeType.IDENTIFY_BUG) {
            TextButton(onClick = onReveal, modifier = Modifier.testTag("reveal")) { Text("Reveal the answer") }
        }
        if (!good && !solutionRevealed && (c.type == ChallengeType.MULTIPLE_CHOICE || c.type == ChallengeType.IDENTIFY_BUG)) {
            TextButton(onClick = onReveal, modifier = Modifier.testTag("reveal")) { Text("Show the correct option") }
        }
        if (solutionRevealed && !good && c.type !in setOf(ChallengeType.MULTIPLE_CHOICE, ChallengeType.IDENTIFY_BUG)) {
            SvCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
                Text("EXPECTED ANSWER", style = MaterialTheme.typography.labelSmall, color = SvTheme.colors.success)
                Spacer(Modifier.height(Spacing.xs))
                Text(outcome.validation.solution, style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (good || solutionRevealed) {
            SvCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md), modifier = Modifier.testTag("explanation")) {
                Text("WHY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Spacing.xs))
                Text(c.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Source: ${c.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                "Tip: take a hint and retry, or reveal the answer to read the full explanation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (outcome.newAchievements.isNotEmpty() && !outcome.leveledUp) AchievementsUnlocked(outcome.newAchievements)

        Text(LocalExerciseValidationService.METHOD, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SecondaryButton(if (good) "Practise again" else "Retry", onRetry, Modifier.weight(1f).testTag("retry"), icon = Icons.Filled.Refresh)
            if (onNext != null) {
                PrimaryButton("Next", onNext, Modifier.weight(1f).testTag("next"), icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            } else {
                PrimaryButton("Done", onDone, Modifier.weight(1f).testTag("done"))
            }
        }
    }
}

@Composable
private fun AchievementsUnlocked(list: List<Achievement>) {
    SvCard(brush = SvTheme.colors.heroBrush) {
        Text("ACHIEVEMENT UNLOCKED", style = MaterialTheme.typography.labelSmall, color = SvTheme.colors.warning)
        list.forEach { a ->
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HexEmblem(a.glyph, size = 40.dp)
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(a.title, style = MaterialTheme.typography.titleSmall)
                    Text(a.requirement, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LevelUpDialog(level: Int, achievements: List<Achievement>, onDismiss: () -> Unit) {
    val scale = rememberPopInScale(level)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("levelup_ok")) { Text("Keep going") } },
        title = null,
        text = {
            Column(Modifier.fillMaxWidth().testTag("levelup_dialog"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.scale(scale)) { HexEmblem("$level", size = 96.dp) }
                Spacer(Modifier.height(Spacing.lg))
                Text("Level up!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("You reached level $level.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                achievements.forEach { a ->
                    Spacer(Modifier.height(Spacing.sm))
                    Pill("Badge: ${a.title}", SvTheme.colors.warning)
                }
            }
        },
    )
}
