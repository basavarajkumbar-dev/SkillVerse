package com.skillverse.app.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.BackHeader
import com.skillverse.app.core.designsystem.DifficultyPill
import com.skillverse.app.core.designsystem.EmptyState
import com.skillverse.app.core.designsystem.LoadingState
import com.skillverse.app.core.designsystem.Pill
import com.skillverse.app.core.designsystem.ScreenTitle
import com.skillverse.app.core.designsystem.SectionHeader
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.SvCard
import com.skillverse.app.core.designsystem.SvIcons
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.core.designsystem.XpBar
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Category
import com.skillverse.app.ui.common.containerFactory

@Composable
fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.PYTHON -> SvIcons.Code
    Category.LOGIC -> SvIcons.Target
    Category.DEBUGGING -> SvIcons.Bug
    Category.TEST_AUTOMATION -> Icons.Filled.CheckCircle
    Category.API_TESTING -> SvIcons.Bolt
    Category.LINUX -> SvIcons.Chart
    Category.GIT -> SvIcons.Tree
    Category.EMBEDDED -> SvIcons.Lightbulb
    Category.DEVOPS -> SvIcons.Review
}

@Composable
fun categoryColor(c: Category): Color = when (c) {
    Category.PYTHON, Category.LOGIC -> MaterialTheme.colorScheme.primary
    Category.DEBUGGING -> SvTheme.colors.danger
    Category.TEST_AUTOMATION, Category.API_TESTING -> MaterialTheme.colorScheme.secondary
    Category.LINUX, Category.GIT -> SvTheme.colors.warning
    Category.EMBEDDED, Category.DEVOPS -> SvTheme.colors.success
}

@Composable
fun PlayScreen(
    container: AppContainer,
    openCategory: (Category) -> Unit,
    openBoss: () -> Unit,
    openReview: () -> Unit,
    openChallenge: (String, AttemptContext) -> Unit,
) {
    val vm: PlayViewModel = viewModel(factory = containerFactory { PlayViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val ui = state ?: return LoadingState(label = "Loading Challenge Arena")

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.testTag("play_list"),
    ) {
        item(key = "title") { ScreenTitle("Challenge Arena", "${ui.player.completedIds.size} challenges completed") }
        item(key = "modes") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SvCard(Modifier.weight(1f), onClick = if (ui.bossTitle != null) openBoss else null, onClickLabel = "Open weekly boss") {
                    Icon(SvIcons.Swords, contentDescription = null, tint = SvTheme.colors.danger)
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Weekly boss", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            ui.bossTitle == null -> "Locked"
                            ui.bossDefeated -> "Cleared"
                            else -> ui.bossTitle
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                SvCard(Modifier.weight(1f).testTag("review_entry"), onClick = openReview, onClickLabel = "Open review queue") {
                    Icon(SvIcons.Review, contentDescription = null, tint = SvTheme.colors.warning)
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Review queue", style = MaterialTheme.typography.titleSmall)
                    Text(if (ui.reviewCount == 0) "Nothing to review" else "${ui.reviewCount} to revisit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "timed") {
            SvCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SvIcons.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Timed mode", style = MaterialTheme.typography.titleSmall)
                        Text("Practice challenges run against the clock. Time running out counts as a miss.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = ui.timedMode,
                        onCheckedChange = vm::setTimedMode,
                        modifier = Modifier.testTag("timed_switch").semantics { contentDescription = "Timed mode" },
                    )
                }
            }
        }
        item(key = "cat_header") { SectionHeader("Categories") }
        items(ui.categories, key = { it.category.name }) { summary ->
            CategoryCard(summary) { openCategory(summary.category) }
        }
    }
}

@Composable
private fun CategoryCard(s: CategorySummary, onClick: () -> Unit) {
    val color = categoryColor(s.category)
    SvCard(onClick = onClick, onClickLabel = "Open ${s.category.label}", modifier = Modifier.testTag("category_${s.category.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(s.category), contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(s.category.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${s.completed}/${s.total} completed" + if (s.locked > 0) " · ${s.locked} locked" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                XpBar(if (s.total == 0) 0f else s.completed.toFloat() / s.total, height = 6.dp, brush = androidx.compose.ui.graphics.SolidColor(color))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CategoryScreen(container: AppContainer, categoryName: String, onBack: () -> Unit, openChallenge: (String, AttemptContext) -> Unit) {
    val vm: CategoryViewModel = viewModel(key = "category_$categoryName", factory = containerFactory { CategoryViewModel(container, categoryName) })
    val state by vm.state.collectAsStateWithLifecycle()
    var lockedInfo by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        val ui = state
        BackHeader(ui?.category?.label ?: "Category", onBack)
        if (ui == null) {
            if (vm.isUnknownCategory) EmptyState("Unknown category", "This category does not exist.", actionLabel = "Go back", onAction = onBack)
            else LoadingState()
        } else if (ui.rows.isEmpty()) {
            EmptyState("No challenges yet", "This category has no challenges in the installed content.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.testTag("category_list"),
            ) {
                items(ui.rows, key = { it.challenge.id }) { row ->
                    ChallengeRowCard(row) {
                        if (row.status == ChallengeStatus.LOCKED) lockedInfo = row.lockReason
                        else openChallenge(row.challenge.id, AttemptContext.PRACTICE)
                    }
                }
            }
        }
    }
    lockedInfo?.let { reason ->
        AlertDialog(
            onDismissRequest = { lockedInfo = null },
            confirmButton = { TextButton(onClick = { lockedInfo = null }) { Text("OK") } },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text("Locked challenge") },
            text = { Text(reason) },
        )
    }
}

@Composable
fun ChallengeRowCard(row: ChallengeRow, onClick: () -> Unit) {
    val c = row.challenge
    val (statusLabel, statusColor) = when (row.status) {
        ChallengeStatus.COMPLETED -> "Completed" to SvTheme.colors.success
        ChallengeStatus.ATTEMPTED -> "Attempted" to SvTheme.colors.warning
        ChallengeStatus.NEW -> "New" to MaterialTheme.colorScheme.secondary
        ChallengeStatus.LOCKED -> "Locked" to SvTheme.colors.locked
    }
    SvCard(
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.md),
        modifier = Modifier.testTag("row_${c.id}").semantics { stateDescription = statusLabel },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, color = if (row.status == ChallengeStatus.LOCKED) SvTheme.colors.locked else MaterialTheme.colorScheme.onSurface)
                Text(c.type.label + " · ${c.estimatedMinutes} min · ${c.xp} XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DifficultyPill(c.difficulty)
                    Pill(statusLabel, statusColor, icon = if (row.status == ChallengeStatus.LOCKED) Icons.Filled.Lock else null)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReviewScreen(container: AppContainer, onBack: () -> Unit, openChallenge: (String) -> Unit) {
    val vm: ReviewViewModel = viewModel(factory = containerFactory { ReviewViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackHeader("Review queue", onBack)
        val items = state
        if (items == null) {
            LoadingState()
        } else if (items.isEmpty()) {
            EmptyState(
                "Nothing to review",
                "Challenges you answer incorrectly land here so you can revisit them. Clear one by answering it correctly.",
                icon = SvIcons.Review,
                modifier = Modifier.testTag("review_empty"),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "intro") {
                    Text(
                        "Most-missed first. Answer correctly to clear an item; XP is only granted the first time a challenge is completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                    )
                }
                items(items, key = { it.challenge.id }) { item ->
                    SvCard(onClick = { openChallenge(item.challenge.id) }, contentPadding = PaddingValues(Spacing.md), modifier = Modifier.testTag("review_${item.challenge.id}")) {
                        Text(item.challenge.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${item.challenge.category.label} · missed ${item.wrongCount}× · last on ${item.lastWrongDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
