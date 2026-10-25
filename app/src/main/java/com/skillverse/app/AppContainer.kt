package com.skillverse.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.skillverse.app.core.time.SystemTimeProvider
import com.skillverse.app.core.time.TimeProvider
import com.skillverse.app.data.content.AssetContentSource
import com.skillverse.app.data.content.BundledContentRepository
import com.skillverse.app.data.content.ContentRepository
import com.skillverse.app.data.db.SkillVerseDatabase
import com.skillverse.app.data.progress.ProgressRepository
import com.skillverse.app.data.settings.DataStoreSettingsRepository
import com.skillverse.app.data.settings.SettingsRepository
import com.skillverse.app.domain.validation.CodeExecutionService
import com.skillverse.app.domain.validation.LocalExerciseValidationService
import com.skillverse.app.notifications.ReminderScheduler

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "skillverse_settings")

/** Manual dependency container. Tests build their own with in-memory implementations. */
interface AppContainer {
    val timeProvider: TimeProvider
    val contentRepository: ContentRepository
    val settingsRepository: SettingsRepository
    val progressRepository: ProgressRepository
    val codeExecutionService: CodeExecutionService
    val reminderScheduler: ReminderScheduler?
    val appVersion: String
    val databaseVersion: Int
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    override val timeProvider: TimeProvider = SystemTimeProvider
    override val contentRepository: ContentRepository = BundledContentRepository(AssetContentSource(appContext.assets))
    override val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext.settingsDataStore)
    override val codeExecutionService: CodeExecutionService = LocalExerciseValidationService()
    private val database: SkillVerseDatabase by lazy { SkillVerseDatabase.build(appContext) }
    override val progressRepository: ProgressRepository by lazy {
        ProgressRepository(database, codeExecutionService, timeProvider)
    }
    override val reminderScheduler: ReminderScheduler = ReminderScheduler(appContext)
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val databaseVersion: Int = SkillVerseDatabase.VERSION
}
