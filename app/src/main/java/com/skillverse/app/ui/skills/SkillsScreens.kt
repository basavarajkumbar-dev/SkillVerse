package com.skillverse.app.ui.skills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.skillverse.app.core.designsystem.SvCard
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.skills.NodeState
import com.skillverse.app.domain.skills.SkillNodeStatus
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.ui.common.PlayerSnapshot
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.common.curriculumOrNull
import com.skillverse.app.ui.common.observePlayer
import com.skillverse.app.ui.play.ChallengeRowCard
import com.skillverse.app.ui.play.rowsFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SkillsUiState(val curriculum: Curriculum, val player: PlayerSnapshot, val nodes: List<SkillNodeStatus>)

@OptIn(ExperimentalCoroutinesApi::class)
class SkillsViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<SkillsUiState?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            if (c == null) flowOf(null)
            else observePlayer(container).map { p -> SkillsUiState(c, p, SkillTreeCalculator.compute(c, p.completedIds)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun nodeColor(state: NodeState): Color = when (state) {
    NodeState.LOCKED -> SvTheme.colors.locked
    NodeState.AVAILABLE -> MaterialTheme.colorScheme.secondary
    NodeState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    NodeState.COMPLETED -> SvTheme.colors.success
    NodeState.MASTERED -> SvTheme.colors.warning
}

@Composable
fun SkillsScreen(container: AppContainer, openSkill: (String) -> Unit) {
    val vm: SkillsViewModel = viewModel(factory = containerFactory { SkillsViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val ui = state ?: return LoadingState(label = "Loading skill tree")
    val mastered = ui.nodes.count { it.state == NodeState.MASTERED }
    val unlocked = ui.nodes.count { it.unlocked }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        modifier = Modifier.testTag("skills_list"),
    ) {
        item(key = "title") {
            ScreenTitle("Career Skill Tree", "$unlocked of ${ui.nodes.size} unlocked · $mastered mastered")
            Text(
                "A node unlocks when its prerequisite reaches ${ui.curriculum.unlockThresholdPercent}% mastery. Mastery is the share of a node's challenges you have completed correctly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
        }
        itemsIndexed(ui.nodes, key = { _, n -> n.node.id }) { index, status ->
            TreeNodeRow(status, isFirst = index == 0, isLast = index == ui.nodes.lastIndex, nextUnlocked = ui.nodes.getOrNull(index + 1)?.unlocked == true) {
                openSkill(status.node.id)
            }
        }
    }
}

@Composable
private fun TreeNodeRow(s: SkillNodeStatus, isFirst: Boolean, isLast: Boolean, nextUnlocked: Boolean, onClick: () -> Unit) {
    val color = nodeColor(s.state)
    val lineDone = SvTheme.colors.success
    val lineIdle = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // Connector rail with the node emblem.
        Box(Modifier.width(64.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 3.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                if (!isFirst) drawLine(if (s.unlocked) lineDone else lineIdle, Offset(x, 0f), Offset(x, 28.dp.toPx()), w, StrokeCap.Round, if (s.unlocked) null else dash)
                if (!isLast) drawLine(if (nextUnlocked) lineDone else lineIdle, Offset(x, 28.dp.toPx()), Offset(x, size.height), w, StrokeCap.Round, if (nextUnlocked) null else dash)
            }
            ProgressRing(
                fraction = s.masteryPercent / 100f,
                size = 56.dp,
                stroke = 4.dp,
                color = color,
                modifier = Modifier.padding(top = 0.dp),
            ) {
                HexEmblem(
                    s.node.glyph,
                    size = 40.dp,
                    enabled = s.unlocked,
                    colors = listOf(color, color.copy(alpha = 0.7f)),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        SvCard(
            onClick = onClick,
            onClickLabel = "Open ${s.node.title}",
            contentPadding = PaddingValues(Spacing.md),
            modifier = Modifier.weight(1f).padding(bottom = Spacing.md).testTag("skill_${s.node.id}")
                .semantics(mergeDescendants = true) { contentDescription = "${s.node.title}, ${s.state.label}, ${s.masteryPercent} percent mastery" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.node.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = if (s.unlocked) MaterialTheme.colorScheme.onSurface else SvTheme.colors.locked)
                Pill(s.state.label, color, icon = if (s.state == NodeState.LOCKED) Icons.Filled.Lock else null)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text("${s.masteryPercent}% mastery · ${s.completed}/${s.total} challenges", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.unlockHint?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text("To unlock: $it", style = MaterialTheme.typography.bodySmall, color = SvTheme.colors.warning)
            }
        }
    }
}

data class SkillDetailUiState(val status: SkillNodeStatus, val prerequisites: List<SkillNodeStatus>, val rows: List<com.skillverse.app.ui.play.ChallengeRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class SkillDetailViewModel(container: AppContainer, skillId: String) : ViewModel() {
    val state: StateFlow<SkillDetailUiState?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            if (c == null) flowOf(null)
            else observePlayer(container).map { p ->
                val all = SkillTreeCalculator.compute(c, p.completedIds)
                val status = all.firstOrNull { it.node.id == skillId } ?: return@map null
                SkillDetailUiState(
                    status = status,
                    prerequisites = all.filter { it.node.id in status.node.prerequisites },
                    rows = rowsFor(c, p, c.challengesForSkill(skillId).sortedBy { it.difficulty.rank }),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SkillDetailScreen(container: AppContainer, skillId: String, onBack: () -> Unit, openChallenge: (String) -> Unit) {
    val vm: SkillDetailViewModel = viewModel(key = "skill_$skillId", factory = containerFactory { SkillDetailViewModel(container, skillId) })
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val ui = state
        BackHeader(ui?.status?.node?.title ?: "Skill", onBack)
        if (ui == null) {
            LoadingState()
        } else {
            SkillDetailList(ui, openChallenge)
        }
    }
}

@Composable
private fun SkillDetailList(ui: SkillDetailUiState, openChallenge: (String) -> Unit) {
        val s = ui.status
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "header") {
                SvCard(brush = SvTheme.colors.heroBrush) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(s.masteryPercent / 100f, size = 72.dp, color = nodeColor(s.state)) {
                            Text("${s.masteryPercent}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        Column(Modifier.weight(1f)) {
                            Pill(s.state.label, nodeColor(s.state))
                            Spacer(Modifier.height(Spacing.xs))
                            Text(s.node.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item(key = "prereq") {
                SectionHeader("Prerequisites")
                if (ui.prerequisites.isEmpty()) {
                    Text("None. This is where every explorer starts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ui.prerequisites.forEach { p ->
                        Text("• ${p.node.title}: ${p.masteryPercent}% (${p.state.label})", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                s.unlockHint?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("How to unlock: $it", style = MaterialTheme.typography.bodyMedium, color = SvTheme.colors.warning)
                }
            }
            item(key = "challenges_header") { SectionHeader("Challenges (${ui.rows.size})") }
            if (ui.rows.isEmpty()) {
                item(key = "empty") { EmptyState("No challenges yet", "Content for this skill will arrive in a future update.") }
            }
            items(ui.rows, key = { it.challenge.id }) { row ->
                ChallengeRowCard(row) { if (s.unlocked || row.status.name == "COMPLETED") openChallenge(row.challenge.id) }
            }
        }
}
