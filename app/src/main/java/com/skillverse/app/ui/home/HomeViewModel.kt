package com.skillverse.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillverse.app.AppContainer
import com.skillverse.app.domain.missions.MissionSlot
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.model.Boss
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.progression.Achievement
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.ui.common.PlayerSnapshot
import com.skillverse.app.ui.common.curriculumOrNull
import com.skillverse.app.ui.common.observePlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

data class MissionItem(val slot: MissionSlot, val challenge: Challenge, val completed: Boolean)

sealed interface ContinueTarget {
    data class Open(val challengeId: String, val context: AttemptContext, val label: String) : ContinueTarget
    data object AllCaughtUp : ContinueTarget
}

data class BossPreview(
    val boss: Boss?,
    val defeated: Boolean,
    val daysLeft: Int,
    /** Why the boss is not available yet; null when it is available. */
    val lockedReason: String?,
)

data class HomeUiState(
    val player: PlayerSnapshot,
    val missions: List<MissionItem>,
    val todayCorrect: Int,
    val continueTarget: ContinueTarget,
    val recentAchievements: List<Achievement>,
    val boss: BossPreview,
)

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val ui: HomeUiState) : HomeState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.progressRepository

    val state: StateFlow<HomeState> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { curriculum ->
            if (curriculum == null) flowOf<HomeState>(HomeState.Loading) else observeHome(curriculum)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)

    private fun observeHome(c: Curriculum): Flow<HomeState> = container.timeProvider.observeToday()
        .let { days ->
            days.flatMapLatest { today ->
                combine(
                    observePlayer(container).distinctUntilChanged(),
                    repo.observeMissions(today).onStart { repo.ensureDailyMissions(c, today) },
                    repo.observeBoss(today),
                    repo.observeDailyActivity(today),
                ) { player, assignments, bossRow, todayActivity -> buildState(c, today, player, assignments, bossRow, todayActivity) }
            }
        }

    private suspend fun buildState(
        c: Curriculum,
        today: LocalDate,
        player: PlayerSnapshot,
        assignments: List<com.skillverse.app.data.db.DailyAssignmentEntity>,
        bossRow: com.skillverse.app.data.db.DailyAssignmentEntity?,
        todayActivity: List<com.skillverse.app.domain.analytics.DayActivity>,
    ): HomeState {
                    // Keep the weekly boss assignment current as skills unlock (idempotent write).
                    val bossEntity = bossRow ?: repo.ensureWeeklyBoss(c, today)
                    val missions = assignments.sortedBy { MissionSlot.entries.indexOfFirst { s -> s.name == it.slot } }
                        .mapNotNull { a ->
                            val ch = c.byId[a.challengeId] ?: return@mapNotNull null
                            val slot = MissionSlot.entries.firstOrNull { it.name == a.slot } ?: return@mapNotNull null
                            MissionItem(slot, ch, a.completedAtEpochMs != null)
                        }
                    val boss = bossEntity?.let { e -> c.bosses.firstOrNull { it.id == e.challengeId } }
                    return HomeState.Ready(
                        HomeUiState(
                            player = player,
                            missions = missions,
                            todayCorrect = todayActivity.firstOrNull { it.date == today }?.correct ?: 0,
                            continueTarget = continueTarget(c, player, missions),
                            recentAchievements = player.unlockedAchievements.take(3),
                            boss = BossPreview(
                                boss = boss,
                                defeated = bossEntity?.completedAtEpochMs != null,
                                daysLeft = daysLeftInWeek(today),
                                lockedReason = if (boss != null) null else bossLockedReason(c, player),
                            ),
                        ),
                    )
    }

    private fun daysLeftInWeek(today: LocalDate): Int = DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 1

    private fun bossLockedReason(c: Curriculum, player: PlayerSnapshot): String {
        val first = c.bosses.firstOrNull() ?: return "No boss challenges are available in this build."
        val needed = first.challengeIds.mapNotNull { c.byId[it]?.skillId }.distinct()
        val unlocked = SkillTreeCalculator.unlockedSkillIds(c, player.completedIds)
        val missing = needed.filter { it !in unlocked }.mapNotNull { c.skillsById[it]?.title }
        return if (missing.isEmpty()) "A boss arrives at the start of next week."
        else "Unlock ${missing.joinToString(" and ")} in the skill tree to face your first boss."
    }

    private fun continueTarget(c: Curriculum, player: PlayerSnapshot, missions: List<MissionItem>): ContinueTarget {
        missions.firstOrNull { !it.completed }?.let {
            return ContinueTarget.Open(it.challenge.id, AttemptContext.DAILY, "Continue: ${it.challenge.title}")
        }
        player.revision.firstOrNull { it.challengeId in c.byId }?.let {
            return ContinueTarget.Open(it.challengeId, AttemptContext.REVIEW, "Review: ${c.byId.getValue(it.challengeId).title}")
        }
        val unlocked = SkillTreeCalculator.unlockedSkillIds(c, player.completedIds)
        val next = c.skills.sortedBy { it.order }
            .filter { it.id in unlocked }
            .flatMap { c.challengesForSkill(it.id) }
            .firstOrNull { it.id !in player.completedIds }
        return next?.let { ContinueTarget.Open(it.id, AttemptContext.PRACTICE, "Next up: ${it.title}") } ?: ContinueTarget.AllCaughtUp
    }
}
