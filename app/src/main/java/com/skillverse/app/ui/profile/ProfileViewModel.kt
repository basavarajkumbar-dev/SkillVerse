package com.skillverse.app.ui.profile

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillverse.app.AppContainer
import com.skillverse.app.data.content.ContentLoadResult
import com.skillverse.app.data.settings.ThemeMode
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.ui.common.PlayerSnapshot
import com.skillverse.app.ui.common.curriculumOrNull
import com.skillverse.app.ui.common.observePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class Diagnostics(
    val appVersion: String,
    val contentVersion: Int?,
    val challengeCount: Int,
    val contentProblems: List<String>,
    val databaseVersion: Int,
    val androidVersion: String,
    val codeExecution: String,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    val player: StateFlow<PlayerSnapshot?> = observePlayer(container).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _diagnostics = MutableStateFlow<Diagnostics?>(null)
    val diagnostics: StateFlow<Diagnostics?> = _diagnostics.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val result = container.contentRepository.load()
            val curriculum = (result as? ContentLoadResult.Ready)?.curriculum
            _diagnostics.value = Diagnostics(
                appVersion = container.appVersion,
                contentVersion = curriculum?.contentVersion,
                challengeCount = curriculum?.challenges?.size ?: 0,
                contentProblems = curriculum?.diagnostics ?: (result as? ContentLoadResult.Failed)?.diagnostics.orEmpty(),
                databaseVersion = container.databaseVersion,
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                codeExecution = if (container.codeExecutionService.executesCode) "Isolated execution backend" else "Local answer validation (no code execution)",
            )
        }
    }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun setName(name: String) = update { it.copy(displayName = name) }
    fun setAvatar(id: Int) = update { it.copy(avatarId = id) }
    fun setDailyGoal(v: Int) = update { it.copy(dailyGoal = v) }
    fun setWeeklyGoal(v: Int) = update { it.copy(weeklyGoalDays = v) }
    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setSound(on: Boolean) = update { it.copy(soundEnabled = on) }
    fun setHaptics(on: Boolean) = update { it.copy(hapticsEnabled = on) }
    fun setReducedMotion(on: Boolean) = update { it.copy(reducedMotion = on) }

    /** Turns reminders on or off. Call with enabled=true only after notification permission is granted. */
    fun setReminder(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(reminderEnabled = enabled, reminderHour = hour ?: it.reminderHour, reminderMinute = minute ?: it.reminderMinute)
            }
            val s = container.settingsRepository.settings.first()
            val scheduler = container.reminderScheduler
            if (s.reminderEnabled) scheduler?.schedule(s.reminderHour, s.reminderMinute) else scheduler?.cancel()
        }
    }

    fun export(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val c = container.curriculumOrNull()
                val json = container.progressRepository.exportJson(container.settingsRepository.settings.first(), container.appVersion, c?.contentVersion ?: 0)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: throw IOException("No output stream")
                }
                _message.value = "Progress exported."
            } catch (e: Exception) {
                _message.value = "Export failed. Choose a different location and try again."
            }
        }
    }

    fun reset(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.reminderScheduler?.cancel()
                container.progressRepository.resetAll(container.curriculumOrNull())
                container.settingsRepository.reset()
                onDone()
            } catch (e: Exception) {
                _message.value = "Reset failed. Please try again."
            }
        }
    }

    fun consumeMessage() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }
}
