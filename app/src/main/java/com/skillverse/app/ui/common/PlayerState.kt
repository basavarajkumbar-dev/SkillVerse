package com.skillverse.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skillverse.app.AppContainer
import com.skillverse.app.data.content.ContentLoadResult
import com.skillverse.app.data.db.AchievementEntity
import com.skillverse.app.data.db.RevisionEntity
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.progression.LevelCalculator
import com.skillverse.app.domain.progression.LevelProgress
import com.skillverse.app.domain.progression.StreakCalculator
import com.skillverse.app.domain.progression.StreakInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

/** Everything the dashboards need, derived from persisted records only. */
data class PlayerSnapshot(
    val today: LocalDate,
    val settings: UserSettings,
    val totalXp: Int,
    val level: LevelProgress,
    val completedIds: Set<String>,
    val attemptedIds: Set<String>,
    val activeDates: Set<LocalDate>,
    val streak: StreakInfo,
    val activeDaysThisWeek: Int,
    val achievements: List<AchievementEntity>,
    val revision: List<RevisionEntity>,
    val bossesDefeated: Int,
    val longestStreakRecorded: Int,
) {
    val unlockedAchievements: List<Achievement>
        get() = achievements.mapNotNull { e -> Achievement.entries.firstOrNull { it.name == e.id } }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun observePlayer(container: AppContainer): Flow<PlayerSnapshot> {
    val repo = container.progressRepository
    return container.timeProvider.observeToday().flatMapLatest { today ->
        val core = combine(
            repo.observeTotalXp(),
            repo.observeCompletedIds(),
            repo.observeAttemptedIds(),
            repo.observeActiveDates(),
            container.settingsRepository.settings,
        ) { xp, completed, attempted, dates, settings -> Core(xp, completed, attempted, dates, settings) }
        combine(
            core,
            repo.observeAchievements(),
            repo.observeRevisionQueue(),
            repo.observeBossCount(),
            repo.observeProgressRow(),
        ) { c, achievements, revision, bosses, row ->
            val streak = StreakCalculator.compute(c.dates, today)
            PlayerSnapshot(
                today = today,
                settings = c.settings,
                totalXp = c.xp,
                level = LevelCalculator.progress(c.xp),
                completedIds = c.completed,
                attemptedIds = c.attempted,
                activeDates = c.dates,
                streak = streak,
                activeDaysThisWeek = StreakCalculator.activeDaysThisWeek(c.dates, today),
                achievements = achievements,
                revision = revision,
                bossesDefeated = bosses,
                longestStreakRecorded = maxOf(row?.longestStreak ?: 0, streak.longest),
            )
        }
    }
}

private data class Core(
    val xp: Int,
    val completed: Set<String>,
    val attempted: Set<String>,
    val dates: Set<LocalDate>,
    val settings: UserSettings,
)

/** Loads the cached curriculum, or null if the bundle failed (the shell shows the error screen). */
suspend fun AppContainer.curriculumOrNull(): Curriculum? =
    (contentRepository.load() as? ContentLoadResult.Ready)?.curriculum

inline fun <reified VM : ViewModel> containerFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }
