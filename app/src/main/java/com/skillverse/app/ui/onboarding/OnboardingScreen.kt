package com.skillverse.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.skillverse.app.core.designsystem.AvatarBadge
import com.skillverse.app.core.designsystem.Avatars
import com.skillverse.app.core.designsystem.HexEmblem
import com.skillverse.app.core.designsystem.Pill
import com.skillverse.app.core.designsystem.PrimaryButton
import com.skillverse.app.core.designsystem.SecondaryButton
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.SvIcons
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.data.settings.UserSettings

@Composable
fun OnboardingScreen(onFinish: (name: String, avatarId: Int, dailyGoal: Int) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableIntStateOf(3) }
    val reduced = SvTheme.reducedMotion

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "Step ${step + 1} of 3" }) {
                repeat(3) { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            AnimatedContent(
                targetState = step,
                transitionSpec = { if (reduced) fadeIn(initialAlpha = 1f) togetherWith fadeOut(targetAlpha = 1f) else fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (s) {
                        0 -> WelcomeStep()
                        1 -> IdentityStep(name, { name = it.take(UserSettings.MAX_NAME_LENGTH) }, avatar) { avatar = it }
                        else -> GoalStep(goal) { goal = it }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                if (step > 0) SecondaryButton("Back", onClick = { step-- }, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = if (step < 2) "Continue" else "Start my first mission",
                    onClick = { if (step < 2) step++ else onFinish(name.ifBlank { UserSettings.DEFAULT_NAME }, avatar, goal) },
                    modifier = Modifier.weight(if (step > 0) 2f else 1f).testTag("onboarding_next"),
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.Start) {
        HexEmblem("SV", size = 84.dp)
        Spacer(Modifier.height(Spacing.xl))
        Text("Welcome to SkillVerse", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Level up real automation skills in 10–20 minutes a day: Python, debugging, pytest, Redfish APIs, Linux, Git, CI/CD and firmware validation.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        Feature(SvIcons.Target, "Three daily missions", "A coding exercise, a quiz and a debugging scenario, matched to your level.")
        Feature(SvIcons.Tree, "A career skill tree", "Ten nodes from Python Foundations to embedded firmware validation.")
        Feature(SvIcons.Swords, "Weekly boss challenges", "Combine concepts in one run for bonus XP.")
        Feature(SvIcons.Bolt, "Works offline, no sign-up", "Your progress stays on this device.")
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IdentityStep(name: String, onName: (String) -> Unit, avatar: Int, onAvatar: (Int) -> Unit) {
    Text("Create your explorer", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(Spacing.sm))
    Text("No account needed. You can change this any time in Profile.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.xl))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarBadge(avatar, size = 72.dp)
        Spacer(Modifier.width(Spacing.lg))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Display name") },
            placeholder = { Text(UserSettings.DEFAULT_NAME) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag("onboarding_name"),
        )
    }
    Spacer(Modifier.height(Spacing.xl))
    Text("Choose an emblem", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(Spacing.md))
    AvatarPicker(selected = avatar, onSelect = onAvatar)
}

@Composable
fun AvatarPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxWidth().height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        userScrollEnabled = false,
    ) {
        items(Avatars.all, key = { it.id }) { a ->
            val isSelected = a.id == selected
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(a.id) }
                    .semantics { this.selected = isSelected; contentDescription = "Emblem ${a.name}" }
                    .padding(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HexEmblem(a.glyph, size = 48.dp, colors = a.colors)
                Text(a.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GoalStep(goal: Int, onGoal: (Int) -> Unit) {
    Text("Set a daily goal", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "How many challenges do you want to complete each day? Three daily missions take about 10–20 minutes.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xl))
    GoalStepper(goal, onGoal, UserSettings.DAILY_GOAL_RANGE, unit = "challenges per day")
    Spacer(Modifier.height(Spacing.lg))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(2 to "Light", 3 to "Steady", 5 to "Intense").forEach { (value, label) ->
            Pill(
                "$label · $value",
                if (goal == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { onGoal(value) },
            )
        }
    }
}

@Composable
fun GoalStepper(value: Int, onValue: (Int) -> Unit, range: IntRange, unit: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        FilledIconButton(
            onClick = { onValue((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = "Decrease" },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledIconButton(
            onClick = { onValue((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = "Increase" },
        ) { Icon(Icons.Filled.Add, contentDescription = null) }
    }
}
