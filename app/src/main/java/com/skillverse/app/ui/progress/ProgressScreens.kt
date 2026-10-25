package com.skillverse.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.BackHeader
import com.skillverse.app.core.designsystem.EmptyState
import com.skillverse.app.core.designsystem.HexEmblem
import com.skillverse.app.core.designsystem.LoadingState
import com.skillverse.app.core.designsystem.Pill
import com.skillverse.app.core.designsystem.ProgressRing
import com.skillverse.app.core.designsystem.ScreenTitle
import com.skillverse.app.core.designsystem.SectionHeader
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.StatTile
import com.skillverse.app.core.designsystem.SvCard
import com.skillverse.app.core.designsystem.SvIcons
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.core.designsystem.XpBar
import com.skillverse.app.domain.analytics.AnalyticsCalculator
import com.skillverse.app.domain.analytics.DayActivity
import com.skillverse.app.domain.analytics.WeakTopic
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.model.Project
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.skills.SkillNodeStatus
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.ui.common.PlayerSnapshot
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.common.curriculumOrNull
import com.skillverse.app.ui.common.observePlayer
import com.skillverse.app.ui.navigation.Tab
import com.skillverse.app.ui.play.ChallengeRowCard
import com.skillverse.app.ui.play.rowsFor
import com.skillverse.app.ui.skills.nodeColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProjectStatus(val project: Project, val completed: Int, val total: Int) {
    val done: Boolean get() = total > 0 && completed == total
}

data class ProgressUiState(
    val player: PlayerSnapshot,
    val attempts: Int,
    val correctAttempts: Int,
    val accuracyPercent: Int,
    val skills: List<SkillNodeStatus>,
    val activity: List<DayActivity>,
    val weakTopics: List<WeakTopic>,
    val revisionChallenges: List<Challenge>,
    val weeklyGoalPercent: Int,
    val projects: List<ProjectStatus>,
)

private const val ACTIVITY_DAYS = 14

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.progressRepository

    val state: StateFlow<ProgressUiState?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            if (c == null) flowOf(null)
            else container.timeProvider.observeToday().flatMapLatest { today ->
                combine(
                    observePlayer(container),
                    repo.observeTotals(),
                    repo.observeDailyActivity(today.minusDays((ACTIVITY_DAYS - 1).toLong())),
                    repo.observeCategoryAccuracy(),
                ) { p, totals, activity, categories -> build(c, p, totals.total, totals.correct, activity, categories) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(
        c: Curriculum,
        p: PlayerSnapshot,
        attempts: Int,
        correct: Int,
        activity: List<DayActivity>,
        categories: List<com.skillverse.app.domain.analytics.CategoryAccuracy>,
    ) = ProgressUiState(
        player = p,
        attempts = attempts,
        correctAttempts = correct,
        accuracyPercent = AnalyticsCalculator.percent(correct, attempts),
        skills = SkillTreeCalculator.compute(c, p.completedIds),
        activity = AnalyticsCalculator.activitySeries(activity, p.today, ACTIVITY_DAYS),
        weakTopics = AnalyticsCalculator.weakTopics(categories),
        revisionChallenges = p.revision.mapNotNull { c.byId[it.challengeId] }.take(5),
        weeklyGoalPercent = AnalyticsCalculator.weeklyGoalPercent(p.activeDaysThisWeek, p.settings.weeklyGoalDays),
        projects = c.projects.map { pr -> ProjectStatus(pr, pr.challengeIds.count { it in p.completedIds }, pr.challengeIds.size) },
    )
}

@Composable
fun ProgressScreen(container: AppContainer, openProject: (String) -> Unit, openReview: () -> Unit, openTab: (Tab) -> Unit) {
    val vm: ProgressViewModel = viewModel(factory = containerFactory { ProgressViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val ui = state ?: return LoadingState(label = "Loading analytics")
    val p = ui.player

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.testTag("progress_list"),
    ) {
        item(key = "title") { ScreenTitle("Learning Analytics", "Calculated from your saved attempts on this device") }
        item(key = "level") {
            SvCard(brush = SvTheme.colors.heroBrush) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HexEmblem("${p.level.level}", size = 56.dp)
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text("Level ${p.level.level}", style = MaterialTheme.typography.titleLarge)
                        Text("${p.totalXp} XP total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("progress_total_xp"))
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                XpBar(p.level.fraction, description = "Level progress")
                Text(
                    if (p.level.isMaxLevel) "Max level reached" else "${p.level.xpIntoLevel}/${p.level.xpForNextLevel} XP · ${p.level.xpRemaining} to level ${p.level.level + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile("Attempted", "${p.attemptedIds.size}", Modifier.weight(1f), icon = SvIcons.Target, supporting = "${ui.attempts} total submissions")
                    StatTile("Completed", "${p.completedIds.size}", Modifier.weight(1f), icon = Icons.Filled.CheckCircle, accent = SvTheme.colors.success, supporting = "distinct challenges")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        "Accuracy",
                        if (ui.attempts == 0) "—" else "${ui.accuracyPercent}%",
                        Modifier.weight(1f).testTag("stat_accuracy"),
                        icon = SvIcons.Bolt,
                        accent = MaterialTheme.colorScheme.secondary,
                        supporting = if (ui.attempts == 0) "No attempts yet" else "${ui.correctAttempts} of ${ui.attempts} correct",
                    )
                    StatTile("Best streak", "${p.longestStreakRecorded} d", Modifier.weight(1f), icon = SvIcons.Flame, accent = SvTheme.colors.streak, supporting = "current ${p.streak.current} d")
                }
            }
        }
        item(key = "weekly") {
            SvCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(ui.weeklyGoalPercent / 100f, size = 64.dp, color = MaterialTheme.colorScheme.secondary) {
                        Text("${ui.weeklyGoalPercent}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column {
                        Text("Weekly goal", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${p.activeDaysThisWeek} active day${if (p.activeDaysThisWeek == 1) "" else "s"} this week · goal ${p.settings.weeklyGoalDays} (Mon–Sun)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "activity") {
            SectionHeader("Activity, last $ACTIVITY_DAYS days")
            SvCard {
                if (ui.activity.all { it.attempts == 0 }) {
                    EmptyState("No activity yet", "Your daily attempts will appear here as a chart.", icon = SvIcons.Chart)
                } else {
                    ActivityChart(ui.activity)
                }
            }
        }
        item(key = "mastery_header") { SectionHeader("Skill mastery", action = "Tree", onAction = { openTab(Tab.SKILLS) }) }
        item(key = "mastery") {
            SvCard {
                ui.skills.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.node.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${s.masteryPercent}%", style = MaterialTheme.typography.labelLarge, color = nodeColor(s.state))
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    XpBar(s.masteryPercent / 100f, height = 6.dp, brush = androidx.compose.ui.graphics.SolidColor(nodeColor(s.state)), description = "${s.node.title} ${s.masteryPercent} percent")
                }
            }
        }
        item(key = "weak") {
            SectionHeader("Weak topics & revision")
            SvCard {
                if (ui.weakTopics.isEmpty() && ui.revisionChallenges.isEmpty()) {
                    EmptyState(
                        "No weak spots yet",
                        "A topic is flagged when you have at least ${AnalyticsCalculator.WEAK_MIN_ATTEMPTS} attempts and under ${AnalyticsCalculator.WEAK_THRESHOLD_PERCENT}% accuracy.",
                        icon = SvIcons.Target,
                        modifier = Modifier.testTag("weak_empty"),
                    )
                } else {
                    ui.weakTopics.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
                            Text(w.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Pill("${w.accuracyPercent}% of ${w.attempts}", SvTheme.colors.danger)
                        }
                    }
                    if (ui.revisionChallenges.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Recommended revisions", style = MaterialTheme.typography.titleSmall)
                        ui.revisionChallenges.forEach { ch ->
                            Text("• ${ch.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.TextButton(onClick = openReview) { Text("Open review queue") }
                    }
                }
            }
        }
        item(key = "projects_header") { SectionHeader("Projects") }
        items(ui.projects, key = { it.project.id }) { ps ->
            SvCard(onClick = { openProject(ps.project.id) }, onClickLabel = "Open project", modifier = Modifier.testTag("project_${ps.project.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(if (ps.total == 0) 0f else ps.completed.toFloat() / ps.total, size = 44.dp, stroke = 5.dp, color = if (ps.done) SvTheme.colors.success else MaterialTheme.colorScheme.primary) {
                        if (ps.done) Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = SvTheme.colors.success)
                        else Text("${ps.completed}/${ps.total}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(ps.project.title, style = MaterialTheme.typography.titleSmall)
                        Text(ps.project.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        item(key = "badges_header") {
            SectionHeader("Badges · ${p.unlockedAchievements.size}/${Achievement.entries.size}")
        }
        item(key = "badges") {
            SvCard {
                Achievement.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(vertical = Spacing.xs)) {
                        pair.forEach { a ->
                            val earned = a in p.unlockedAchievements
                            Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${a.title}, ${if (earned) "earned" else "locked"}: ${a.requirement}" }, verticalAlignment = Alignment.CenterVertically) {
                                HexEmblem(a.glyph, size = 40.dp, enabled = earned)
                                Spacer(Modifier.width(Spacing.sm))
                                Column {
                                    Text(a.title, style = MaterialTheme.typography.labelLarge, color = if (earned) MaterialTheme.colorScheme.onSurface else SvTheme.colors.locked)
                                    Text(a.requirement, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityChart(days: List<DayActivity>) {
    val max = (days.maxOfOrNull { it.attempts } ?: 0).coerceAtLeast(1)
    val attemptColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val correctColor = MaterialTheme.colorScheme.secondary
    val total = days.sumOf { it.attempts }
    val correct = days.sumOf { it.correct }
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = "Activity chart: $total attempts and $correct correct answers over ${days.size} days"
        },
    ) {
        val gap = 6.dp.toPx()
        val barW = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, d ->
            val x = i * (barW + gap)
            val hAll = size.height * d.attempts / max
            val hOk = size.height * d.correct / max
            if (d.attempts > 0) drawRoundRect(attemptColor, Offset(x, size.height - hAll), Size(barW, hAll), CornerRadius(6f, 6f))
            if (d.correct > 0) drawRoundRect(correctColor, Offset(x, size.height - hOk), Size(barW, hOk), CornerRadius(6f, 6f))
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    Row {
        Text(days.first().date.let { "${it.dayOfMonth}/${it.monthValue}" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.xs)) {
        Pill("Attempts", MaterialTheme.colorScheme.primary)
        Pill("Correct", MaterialTheme.colorScheme.secondary)
    }
}

data class ProjectUiState(val status: ProjectStatus, val rows: List<com.skillverse.app.ui.play.ChallengeRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectViewModel(container: AppContainer, projectId: String) : ViewModel() {
    val state: StateFlow<ProjectUiState?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            val project = c?.projects?.firstOrNull { it.id == projectId }
            if (c == null || project == null) flowOf(null)
            else observePlayer(container).map { p ->
                ProjectUiState(
                    ProjectStatus(project, project.challengeIds.count { it in p.completedIds }, project.challengeIds.size),
                    rowsFor(c, p, project.challengeIds.mapNotNull { c.byId[it] }),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ProjectScreen(container: AppContainer, projectId: String, onBack: () -> Unit, openChallenge: (String) -> Unit) {
    val vm: ProjectViewModel = viewModel(key = "project_$projectId", factory = containerFactory { ProjectViewModel(container, projectId) })
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val ui = state
        BackHeader(ui?.status?.project?.title ?: "Project", onBack)
        if (ui == null) {
            LoadingState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "intro") {
                    SvCard(brush = SvTheme.colors.heroBrush) {
                        Text(ui.status.project.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(Spacing.sm))
                        Text(ui.status.project.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.md))
                        XpBar(if (ui.status.total == 0) 0f else ui.status.completed.toFloat() / ui.status.total, description = "Project progress")
                        Text(
                            if (ui.status.done) "Project complete" else "${ui.status.completed} of ${ui.status.total} milestones complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (ui.status.done) SvTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
                items(ui.rows, key = { it.challenge.id }) { row ->
                    ChallengeRowCard(row) { openChallenge(row.challenge.id) }
                }
            }
        }
    }
}
