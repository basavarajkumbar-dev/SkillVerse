package com.skillverse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.AvatarBadge
import com.skillverse.app.core.designsystem.DifficultyPill
import com.skillverse.app.core.designsystem.EmptyState
import com.skillverse.app.core.designsystem.HexEmblem
import com.skillverse.app.core.designsystem.LoadingState
import com.skillverse.app.core.designsystem.Pill
import com.skillverse.app.core.designsystem.PrimaryButton
import com.skillverse.app.core.designsystem.ProgressRing
import com.skillverse.app.core.designsystem.SectionHeader
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.StatTile
import com.skillverse.app.core.designsystem.SvCard
import com.skillverse.app.core.designsystem.SvIcons
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.core.designsystem.XpBar
import com.skillverse.app.domain.missions.MissionSlot
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.navigation.Tab

@Composable
fun HomeScreen(
    container: AppContainer,
    openChallenge: (String, AttemptContext) -> Unit,
    openBoss: () -> Unit,
    openReview: () -> Unit,
    openTab: (Tab) -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = containerFactory { HomeViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        HomeState.Loading -> LoadingState(label = "Loading Mission Control")
        is HomeState.Ready -> HomeContent(s.ui, openChallenge, openBoss, openReview, openTab)
    }
}

@Composable
private fun HomeContent(
    ui: HomeUiState,
    openChallenge: (String, AttemptContext) -> Unit,
    openBoss: () -> Unit,
    openReview: () -> Unit,
    openTab: (Tab) -> Unit,
) {
    val p = ui.player
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.testTag("home_list"),
    ) {
        item(key = "hero") { HeroCard(ui) }
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "Daily streak",
                    value = "${p.streak.current} day${if (p.streak.current == 1) "" else "s"}",
                    icon = SvIcons.Flame,
                    accent = SvTheme.colors.streak,
                    supporting = if (p.streak.activeToday) "Done for today" else if (p.streak.current > 0) "Learn today to extend it" else "Best: ${p.longestStreakRecorded}",
                    modifier = Modifier.weight(1f).testTag("stat_streak"),
                )
                StatTile(
                    label = "Weekly goal",
                    value = "${p.activeDaysThisWeek.coerceAtMost(p.settings.weeklyGoalDays)}/${p.settings.weeklyGoalDays} days",
                    icon = SvIcons.Target,
                    accent = MaterialTheme.colorScheme.secondary,
                    supporting = "Today ${ui.todayCorrect}/${p.settings.dailyGoal} challenges",
                    modifier = Modifier.weight(1f).testTag("stat_weekly"),
                )
            }
        }
        item(key = "missions_header") {
            val done = ui.missions.count { it.completed }
            SectionHeader("Today's missions")
            if (ui.missions.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XpBar(
                        fraction = if (ui.missions.isEmpty()) 0f else done.toFloat() / ui.missions.size,
                        modifier = Modifier.weight(1f),
                        height = 8.dp,
                        description = "$done of ${ui.missions.size} missions complete",
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text("$done/${ui.missions.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "About ${ui.missions.sumOf { it.challenge.estimatedSeconds } / 60} min · ${ui.missions.sumOf { it.challenge.xp }} XP available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        if (ui.missions.isEmpty()) {
            item(key = "missions_empty") {
                SvCard { EmptyState("No missions today", "Missions could not be generated from the unlocked skills. Open the Play tab to practise any challenge.") }
            }
        } else {
            items(ui.missions, key = { "mission_${it.slot.name}" }) { m ->
                MissionCard(m) { openChallenge(m.challenge.id, AttemptContext.DAILY) }
            }
        }
        item(key = "continue") {
            when (val t = ui.continueTarget) {
                is ContinueTarget.Open -> PrimaryButton(
                    text = t.label,
                    onClick = { openChallenge(t.challengeId, t.context) },
                    modifier = Modifier.fillMaxWidth().testTag("continue_learning"),
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                )
                ContinueTarget.AllCaughtUp -> SvCard {
                    EmptyState("All caught up", "Every unlocked challenge is complete. Revisit a skill or wait for tomorrow's missions.", icon = SvIcons.Trophy)
                }
            }
        }
        if (p.revision.isNotEmpty()) {
            item(key = "review") {
                SvCard(onClick = openReview, onClickLabel = "Open review queue") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(SvIcons.Review, contentDescription = null, tint = SvTheme.colors.warning)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text("Review queue", style = MaterialTheme.typography.titleSmall)
                            Text("${p.revision.size} challenge${if (p.revision.size == 1) "" else "s"} to revisit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
        item(key = "boss") { BossCard(ui.boss, openBoss) }
        item(key = "achievements_header") { SectionHeader("Recent achievements", action = "All", onAction = { openTab(Tab.PROGRESS) }) }
        item(key = "achievements") {
            if (ui.recentAchievements.isEmpty()) {
                SvCard { EmptyState("No badges yet", "Complete your first mission to earn ${Achievement.FIRST_MISSION.title}.", icon = SvIcons.Trophy) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ui.recentAchievements.forEach { a ->
                        SvCard(Modifier.weight(1f)) {
                            HexEmblem(a.glyph, size = 44.dp)
                            Spacer(Modifier.height(Spacing.sm))
                            Text(a.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(3 - ui.recentAchievements.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(ui: HomeUiState) {
    val p = ui.player
    SvCard(brush = SvTheme.colors.heroBrush, contentPadding = PaddingValues(Spacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(p.settings.avatarId, size = 60.dp, level = p.level.level)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text("Mission Control", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.settings.displayName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Level ${p.level.level}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SvIcons.Bolt, contentDescription = null, tint = SvTheme.colors.xp, modifier = Modifier.size(18.dp))
                    Text("${p.totalXp}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text("total XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        XpBar(
            fraction = p.level.fraction,
            height = 12.dp,
            description = "Level ${p.level.level}: ${p.level.xpIntoLevel} of ${p.level.xpForNextLevel} XP",
            modifier = Modifier.testTag("home_xp_bar"),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row {
            Text("${p.level.xpIntoLevel} / ${p.level.xpForNextLevel} XP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                if (p.level.isMaxLevel) "Max level" else "${p.level.xpRemaining} XP to level ${p.level.level + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissionCard(m: MissionItem, onClick: () -> Unit) {
    val icon = when (m.slot) {
        MissionSlot.CODING -> SvIcons.Code
        MissionSlot.QUIZ -> SvIcons.Lightbulb
        MissionSlot.DEBUG -> SvIcons.Bug
    }
    SvCard(onClick = onClick, onClickLabel = "Open mission", modifier = Modifier.testTag("mission_${m.challenge.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(
                    if (m.completed) SvTheme.colors.success.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (m.completed) Icons.Filled.Check else icon,
                    contentDescription = if (m.completed) "Completed" else null,
                    tint = if (m.completed) SvTheme.colors.success else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(m.slot.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(m.challenge.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    DifficultyPill(m.challenge.difficulty)
                    Text("${m.challenge.estimatedMinutes} min · ${m.challenge.xp} XP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BossCard(preview: BossPreview, openBoss: () -> Unit) {
    val boss = preview.boss
    SvCard(
        onClick = if (boss != null) openBoss else null,
        onClickLabel = "Open weekly boss",
        modifier = Modifier.testTag("boss_card").semantics { contentDescription = "Weekly boss" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = if (preview.defeated) 1f else 0f,
                size = 52.dp,
                color = SvTheme.colors.danger,
            ) {
                Icon(if (boss == null) Icons.Filled.Lock else SvIcons.Swords, contentDescription = null, tint = if (boss == null) SvTheme.colors.locked else SvTheme.colors.danger)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("WEEKLY BOSS", style = MaterialTheme.typography.labelSmall, color = SvTheme.colors.danger)
                Text(boss?.title ?: "Boss locked", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        boss == null -> preview.lockedReason ?: ""
                        preview.defeated -> "Defeated this week. A new boss arrives Monday."
                        else -> "${boss.challengeIds.size} linked challenges · +75 XP · ${preview.daysLeft} day${if (preview.daysLeft == 1) "" else "s"} left"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview.defeated) Pill("Cleared", SvTheme.colors.success)
        }
    }
}
