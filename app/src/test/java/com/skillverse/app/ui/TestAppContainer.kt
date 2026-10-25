package com.skillverse.app.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.skillverse.app.AppContainer
import com.skillverse.app.TestContent
import com.skillverse.app.core.time.FakeTimeProvider
import com.skillverse.app.data.content.BundledContentRepository
import com.skillverse.app.data.content.ContentRepository
import com.skillverse.app.data.db.SkillVerseDatabase
import com.skillverse.app.data.progress.ProgressRepository
import com.skillverse.app.data.settings.InMemorySettingsRepository
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.validation.CodeExecutionService
import com.skillverse.app.domain.validation.LocalExerciseValidationService
import com.skillverse.app.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId

/** In-memory dependencies for UI tests: real content, real Room (in memory), fake clock, no WorkManager. */
class TestAppContainer(settings: UserSettings = UserSettings(onboardingComplete = true, displayName = "Tester")) : AppContainer {
    val db: SkillVerseDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SkillVerseDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    override val timeProvider = FakeTimeProvider(
        LocalDate.of(2026, 10, 25).atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant(),
        ZoneId.of("Asia/Kolkata"),
    )
    override val contentRepository: ContentRepository = BundledContentRepository(TestContent.source)
    override val settingsRepository = InMemorySettingsRepository(settings)
    override val codeExecutionService: CodeExecutionService = LocalExerciseValidationService()
    override val progressRepository = ProgressRepository(db, codeExecutionService, timeProvider)
    override val reminderScheduler: ReminderScheduler? = null
    override val appVersion: String = "test"
    override val databaseVersion: Int = SkillVerseDatabase.VERSION
}
