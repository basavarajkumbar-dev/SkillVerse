package com.skillverse.app.ui.profile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.AvatarBadge
import com.skillverse.app.core.designsystem.LoadingState
import com.skillverse.app.core.designsystem.ScreenTitle
import com.skillverse.app.core.designsystem.SecondaryButton
import com.skillverse.app.core.designsystem.SectionHeader
import com.skillverse.app.core.designsystem.Spacing
import com.skillverse.app.core.designsystem.SvCard
import com.skillverse.app.core.designsystem.SvTheme
import com.skillverse.app.data.settings.ThemeMode
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.notifications.ReminderNotifications
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.onboarding.AvatarPicker
import com.skillverse.app.ui.onboarding.GoalStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(container: AppContainer, onReset: () -> Unit) {
    val vm: ProfileViewModel = viewModel(factory = containerFactory { ProfileViewModel(container) })
    val player by vm.player.collectAsStateWithLifecycle()
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val p = player ?: return LoadingState(label = "Loading profile")
    val s = p.settings

    var editName by rememberSaveable { mutableStateOf(false) }
    var pickAvatar by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminder(true) else permissionDenied = true
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, context.contentResolver)
    }

    fun enableReminder() {
        if (ReminderNotifications.hasPermission(context)) {
            vm.setReminder(true)
            if (!ReminderNotifications.notificationsEnabled(context)) permissionDenied = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.testTag("profile_list"),
    ) {
        item(key = "title") { ScreenTitle("Profile") }
        item(key = "identity") {
            SvCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickAvatar = true }, modifier = Modifier.width(76.dp).height(76.dp).semantics { contentDescription = "Change emblem" }) {
                        AvatarBadge(s.avatarId, size = 64.dp, level = p.level.level)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(s.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("profile_name"))
                        Text("Level ${p.level.level} · ${p.totalXp} XP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!editName) {
                        IconButton(onClick = { editName = true }, modifier = Modifier.testTag("edit_name")) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit display name")
                        }
                    }
                }
                if (editName) {
                    Spacer(Modifier.height(Spacing.md))
                    NameEditor(s.displayName, onCancel = { editName = false }) { vm.setName(it); editName = false }
                }
            }
        }
        item(key = "goals") {
            SectionHeader("Goals")
            SvCard {
                Text("Daily learning goal", style = MaterialTheme.typography.titleSmall)
                GoalStepper(s.dailyGoal, vm::setDailyGoal, UserSettings.DAILY_GOAL_RANGE, "challenges per day", Modifier.testTag("daily_goal"))
                HorizontalDivider(Modifier.padding(vertical = Spacing.md))
                Text("Weekly goal", style = MaterialTheme.typography.titleSmall)
                GoalStepper(s.weeklyGoalDays, vm::setWeeklyGoal, UserSettings.WEEKLY_GOAL_RANGE, "active days per week")
            }
        }
        item(key = "reminders") {
            SectionHeader("Daily reminder")
            SvCard {
                SettingSwitch(
                    title = "Remind me to learn",
                    subtitle = if (s.reminderEnabled) "Around %02d:%02d each day".format(s.reminderHour, s.reminderMinute) else "Off",
                    checked = s.reminderEnabled,
                    onChange = { on -> if (on) enableReminder() else vm.setReminder(false) },
                    tag = "reminder_switch",
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                )
                TextButton(onClick = { pickTime = true }, modifier = Modifier.testTag("reminder_time")) {
                    Text("Change time (%02d:%02d)".format(s.reminderHour, s.reminderMinute))
                }
                Text(
                    "Reminders are delivered by Android's WorkManager, which may delay them by several minutes or more to save battery " +
                        "(Doze, battery saver and some manufacturers' power managers). They are not exact alarms. " +
                        "Reminders survive restarts; learning works fully with notifications off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (permissionDenied || (s.reminderEnabled && !ReminderNotifications.notificationsEnabled(context))) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Notifications are blocked for SkillVerse in system settings.", style = MaterialTheme.typography.bodySmall, color = SvTheme.colors.warning)
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        try { context.startActivity(intent) } catch (e: Exception) { vm.showMessage("Open Settings > Apps > SkillVerse > Notifications.") }
                    }) { Text("Open notification settings") }
                }
            }
        }
        item(key = "appearance") {
            SectionHeader("Appearance & feedback")
            SvCard {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            modifier = Modifier.testTag("theme_${mode.name}"),
                        ) { Text(mode.label) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.md))
                SettingSwitch("Sound effects", "System click on results", s.soundEnabled, vm::setSound, "sound_switch")
                SettingSwitch("Haptics", "Vibration feedback on results", s.hapticsEnabled, vm::setHaptics, "haptics_switch")
                SettingSwitch("Reduce motion", "Turns off progress and celebration animations", s.reducedMotion, vm::setReducedMotion, "motion_switch")
            }
        }
        item(key = "data") {
            SectionHeader("Your data")
            SvCard {
                Text(
                    "Privacy: SkillVerse needs no account and makes no network requests. Your progress, answers and settings are stored only on this device " +
                        "and are excluded from cloud backup. Export creates a JSON file wherever you choose; nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton("Export progress (JSON)", { exportLauncher.launch("skillverse-progress.json") }, Modifier.fillMaxWidth().testTag("export"), icon = Icons.Filled.Share)
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton("Reset all progress", { confirmReset = true }, Modifier.fillMaxWidth().testTag("reset"), icon = Icons.Filled.Delete)
            }
        }
        item(key = "about") {
            SectionHeader("About & diagnostics")
            SvCard {
                val d = diagnostics
                if (d == null) {
                    Text("Collecting…", style = MaterialTheme.typography.bodySmall)
                } else {
                    DiagRow("App version", d.appVersion)
                    DiagRow("Content version", d.contentVersion?.toString() ?: "unavailable")
                    DiagRow("Challenges loaded", d.challengeCount.toString())
                    DiagRow("Content problems", if (d.contentProblems.isEmpty()) "none" else d.contentProblems.size.toString())
                    d.contentProblems.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = SvTheme.colors.warning) }
                    DiagRow("Database schema", "v${d.databaseVersion}")
                    DiagRow("Answer checking", d.codeExecution)
                    DiagRow("Device", d.androidVersion)
                    DiagRow("Notifications", if (ReminderNotifications.notificationsEnabled(context)) "allowed" else "not allowed")
                }
            }
        }
    }

    if (pickAvatar) {
        AlertDialog(
            onDismissRequest = { pickAvatar = false },
            confirmButton = { TextButton(onClick = { pickAvatar = false }) { Text("Done") } },
            title = { Text("Choose an emblem") },
            text = { AvatarPicker(s.avatarId, vm::setAvatar) },
        )
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = s.reminderHour, initialMinute = s.reminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    if (s.reminderEnabled) vm.setReminder(true, state.hour, state.minute)
                    else vm.setReminder(false, state.hour, state.minute)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            title = { Text("Reminder time") },
            text = { TimePicker(state = state) },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.reset(onReset) }, modifier = Modifier.testTag("confirm_reset")) {
                    Text("Delete everything", color = SvTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            title = { Text("Reset all progress?") },
            text = { Text("This permanently deletes your XP, levels, streaks, achievements, attempts and settings on this device. Export first if you want a copy. This cannot be undone.") },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = vm::consumeMessage,
            confirmButton = { TextButton(onClick = vm::consumeMessage) { Text("OK") } },
            text = { Text(text) },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { icon(); Spacer(Modifier.width(Spacing.md)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NameEditor(current: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(UserSettings.MAX_NAME_LENGTH) },
            label = { Text("Display name") },
            singleLine = true,
            supportingText = { Text("${text.length}/${UserSettings.MAX_NAME_LENGTH}") },
            modifier = Modifier.fillMaxWidth().testTag("name_field"),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("save_name")) { Text("Save") }
        }
    }
}
