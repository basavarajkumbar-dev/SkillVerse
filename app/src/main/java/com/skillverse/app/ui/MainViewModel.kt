package com.skillverse.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillverse.app.AppContainer
import com.skillverse.app.data.content.ContentLoadResult
import com.skillverse.app.data.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val onboardingComplete: Boolean) : AppUiState
    data class Failed(val message: String) : AppUiState
}

/** App-level startup: loads and validates content, initialises the database and restores reminders. */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    val settings: StateFlow<UserSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { start() }

    fun start() {
        _state.value = AppUiState.Loading
        viewModelScope.launch {
            when (val result = container.contentRepository.load()) {
                is ContentLoadResult.Failed -> _state.value = AppUiState.Failed(result.message)
                is ContentLoadResult.Ready -> {
                    try {
                        container.progressRepository.syncContent(result.curriculum)
                    } catch (e: Exception) {
                        _state.value = AppUiState.Failed("Your progress database could not be opened. Restart the app; if this continues, use Reset in Profile.")
                        return@launch
                    }
                    val s = container.settingsRepository.settings.first()
                    restoreReminder(s)
                    _state.value = AppUiState.Ready(s.onboardingComplete)
                }
            }
        }
    }

    /** Re-arms the single unique reminder so its delay reflects the current time zone. */
    private fun restoreReminder(s: UserSettings) {
        val scheduler = container.reminderScheduler ?: return
        if (s.reminderEnabled) scheduler.schedule(s.reminderHour, s.reminderMinute) else scheduler.cancel()
    }

    fun completeOnboarding(name: String, avatarId: Int, dailyGoal: Int) {
        viewModelScope.launch {
            container.settingsRepository.update {
                it.copy(displayName = name, avatarId = avatarId, dailyGoal = dailyGoal, onboardingComplete = true)
            }
            _state.value = AppUiState.Ready(onboardingComplete = true)
        }
    }

    /** Called after a full reset so onboarding runs again. */
    fun onReset() {
        _state.value = AppUiState.Ready(onboardingComplete = false)
    }
}
