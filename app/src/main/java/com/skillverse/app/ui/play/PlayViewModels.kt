package com.skillverse.app.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillverse.app.AppContainer
import com.skillverse.app.domain.model.Category
import com.skillverse.app.domain.model.Challenge
import com.skillverse.app.domain.model.Curriculum
import com.skillverse.app.domain.skills.SkillTreeCalculator
import com.skillverse.app.ui.common.PlayerSnapshot
import com.skillverse.app.ui.common.curriculumOrNull
import com.skillverse.app.ui.common.observePlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChallengeStatus { LOCKED, NEW, ATTEMPTED, COMPLETED }

data class ChallengeRow(val challenge: Challenge, val status: ChallengeStatus, val lockReason: String?)

data class CategorySummary(val category: Category, val total: Int, val completed: Int, val locked: Int)

data class PlayUiState(
    val player: PlayerSnapshot,
    val categories: List<CategorySummary>,
    val reviewCount: Int,
    val bossTitle: String?,
    val bossDefeated: Boolean,
    val timedMode: Boolean,
)

internal fun rowsFor(c: Curriculum, player: PlayerSnapshot, challenges: List<Challenge>): List<ChallengeRow> {
    val statuses = SkillTreeCalculator.compute(c, player.completedIds).associateBy { it.node.id }
    return challenges.map { ch ->
        val skill = statuses[ch.skillId]
        val status = when {
            ch.id in player.completedIds -> ChallengeStatus.COMPLETED
            skill != null && !skill.unlocked -> ChallengeStatus.LOCKED
            ch.id in player.attemptedIds -> ChallengeStatus.ATTEMPTED
            else -> ChallengeStatus.NEW
        }
        ChallengeRow(ch, status, if (status == ChallengeStatus.LOCKED) "${skill?.node?.title}: ${skill?.unlockHint}" else null)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<PlayUiState?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            if (c == null) flowOf(null) else observePlayer(container).flatMapLatest { player ->
                container.progressRepository.observeBoss(player.today).map { bossRow ->
                    val rows = rowsFor(c, player, c.challenges)
                    PlayUiState(
                        player = player,
                        categories = Category.entries.map { cat ->
                            val inCat = rows.filter { it.challenge.category == cat }
                            CategorySummary(cat, inCat.size, inCat.count { it.status == ChallengeStatus.COMPLETED }, inCat.count { it.status == ChallengeStatus.LOCKED })
                        }.filter { it.total > 0 },
                        reviewCount = player.revision.count { it.challengeId in c.byId },
                        bossTitle = bossRow?.let { b -> c.bosses.firstOrNull { it.id == b.challengeId }?.title },
                        bossDefeated = bossRow?.completedAtEpochMs != null,
                        timedMode = player.settings.timedMode,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTimedMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(timedMode = enabled) } }
    }
}

data class CategoryUiState(val category: Category, val rows: List<ChallengeRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModel(container: AppContainer, categoryName: String) : ViewModel() {
    private val category = Category.entries.firstOrNull { it.name == categoryName }

    val state: StateFlow<CategoryUiState?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            if (c == null || category == null) flowOf(null)
            else observePlayer(container).map { player ->
                val ordered = c.challengesForCategory(category).sortedWith(
                    compareBy<Challenge> { c.skillsById[it.skillId]?.order ?: 0 }.thenBy { it.difficulty.rank }.thenBy { it.id },
                )
                CategoryUiState(category, rowsFor(c, player, ordered))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isUnknownCategory: Boolean get() = category == null
}

data class ReviewItem(val challenge: Challenge, val wrongCount: Int, val lastWrongDate: String)

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<List<ReviewItem>?> = flow { emit(container.curriculumOrNull()) }
        .flatMapLatest { c ->
            if (c == null) flowOf(emptyList())
            else container.progressRepository.observeRevisionQueue().map { rows ->
                rows.mapNotNull { r -> c.byId[r.challengeId]?.let { ReviewItem(it, r.wrongCount, r.lastWrongDate) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
