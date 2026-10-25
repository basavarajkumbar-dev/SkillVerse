package com.skillverse.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

data class UserSettings(
    val displayName: String = DEFAULT_NAME,
    val avatarId: Int = 0,
    /** Challenges per day the learner aims for. */
    val dailyGoal: Int = 3,
    /** Active days per week the learner aims for. */
    val weeklyGoalDays: Int = 5,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val timedMode: Boolean = false,
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val DEFAULT_NAME = "Explorer"
        const val MAX_NAME_LENGTH = 24
        val DAILY_GOAL_RANGE = 1..10
        val WEEKLY_GOAL_RANGE = 1..7
    }

    /** Clamps every value into its valid range; applied on every write and read. */
    fun sanitized(): UserSettings = copy(
        displayName = displayName.trim().take(MAX_NAME_LENGTH).ifBlank { DEFAULT_NAME },
        avatarId = avatarId.coerceIn(0, 7),
        dailyGoal = dailyGoal.coerceIn(DAILY_GOAL_RANGE),
        weeklyGoalDays = weeklyGoalDays.coerceIn(WEEKLY_GOAL_RANGE),
        reminderHour = reminderHour.coerceIn(0, 23),
        reminderMinute = reminderMinute.coerceIn(0, 59),
    )
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
    suspend fun reset()
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val name = stringPreferencesKey("display_name")
        val avatar = intPreferencesKey("avatar_id")
        val dailyGoal = intPreferencesKey("daily_goal")
        val weeklyGoal = intPreferencesKey("weekly_goal_days")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val theme = stringPreferencesKey("theme_mode")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val timedMode = booleanPreferencesKey("timed_mode")
        val onboarding = booleanPreferencesKey("onboarding_complete")
    }

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = UserSettings()
            UserSettings(
                displayName = p[Keys.name] ?: d.displayName,
                avatarId = p[Keys.avatar] ?: d.avatarId,
                dailyGoal = p[Keys.dailyGoal] ?: d.dailyGoal,
                weeklyGoalDays = p[Keys.weeklyGoal] ?: d.weeklyGoalDays,
                reminderEnabled = p[Keys.reminderEnabled] ?: d.reminderEnabled,
                reminderHour = p[Keys.reminderHour] ?: d.reminderHour,
                reminderMinute = p[Keys.reminderMinute] ?: d.reminderMinute,
                themeMode = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: d.themeMode,
                soundEnabled = p[Keys.sound] ?: d.soundEnabled,
                hapticsEnabled = p[Keys.haptics] ?: d.hapticsEnabled,
                reducedMotion = p[Keys.reducedMotion] ?: d.reducedMotion,
                timedMode = p[Keys.timedMode] ?: d.timedMode,
                onboardingComplete = p[Keys.onboarding] ?: d.onboardingComplete,
            ).sanitized()
        }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { p ->
            val current = UserSettings(
                displayName = p[Keys.name] ?: UserSettings.DEFAULT_NAME,
                avatarId = p[Keys.avatar] ?: 0,
                dailyGoal = p[Keys.dailyGoal] ?: 3,
                weeklyGoalDays = p[Keys.weeklyGoal] ?: 5,
                reminderEnabled = p[Keys.reminderEnabled] ?: false,
                reminderHour = p[Keys.reminderHour] ?: 19,
                reminderMinute = p[Keys.reminderMinute] ?: 0,
                themeMode = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.DARK,
                soundEnabled = p[Keys.sound] ?: true,
                hapticsEnabled = p[Keys.haptics] ?: true,
                reducedMotion = p[Keys.reducedMotion] ?: false,
                timedMode = p[Keys.timedMode] ?: false,
                onboardingComplete = p[Keys.onboarding] ?: false,
            )
            val next = transform(current).sanitized()
            p[Keys.name] = next.displayName
            p[Keys.avatar] = next.avatarId
            p[Keys.dailyGoal] = next.dailyGoal
            p[Keys.weeklyGoal] = next.weeklyGoalDays
            p[Keys.reminderEnabled] = next.reminderEnabled
            p[Keys.reminderHour] = next.reminderHour
            p[Keys.reminderMinute] = next.reminderMinute
            p[Keys.theme] = next.themeMode.name
            p[Keys.sound] = next.soundEnabled
            p[Keys.haptics] = next.hapticsEnabled
            p[Keys.reducedMotion] = next.reducedMotion
            p[Keys.timedMode] = next.timedMode
            p[Keys.onboarding] = next.onboardingComplete
        }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}

/** In-memory implementation for tests and previews. */
class InMemorySettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial.sanitized())
    override val settings: StateFlow<UserSettings> = state
    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update { transform(it).sanitized() }
    override suspend fun reset() { state.value = UserSettings() }
}
