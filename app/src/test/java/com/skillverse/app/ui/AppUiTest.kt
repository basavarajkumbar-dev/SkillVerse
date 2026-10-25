package com.skillverse.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skillverse.app.TestContent
import com.skillverse.app.data.settings.ThemeMode
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.missions.MissionSlot
import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.validation.UserAnswer
import com.skillverse.app.ui.navigation.SkillVerseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppUiTest {
    @get:Rule val rule = createComposeRule()
    private val c = TestContent.curriculum
    private var container: TestAppContainer? = null

    @After fun tearDown() { container?.db?.close() }

    private fun launch(settings: UserSettings = UserSettings(onboardingComplete = true, displayName = "Tester")): TestAppContainer {
        val tc = TestAppContainer(settings)
        container = tc
        rule.setContent { SkillVerseApp(tc) }
        return tc
    }

    private fun waitForTag(tag: String, timeout: Long = 10_000) =
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), timeout)

    private fun waitForText(text: String, timeout: Long = 10_000) =
        rule.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    @Test fun onboardingLeadsToMissionControl() {
        val tc = launch(UserSettings())
        waitForTag("onboarding_next")
        rule.onNodeWithTag("onboarding_next").performClick()
        waitForTag("onboarding_name")
        rule.onNodeWithTag("onboarding_name").performTextInput("Asha")
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_next").performClick()
        waitForText("Mission Control")
        rule.onNodeWithText("Asha").assertIsDisplayed()
        val saved = runBlocking { tc.settingsRepository.settings.first() }
        assertTrue(saved.onboardingComplete)
        assertEquals("Asha", saved.displayName)
    }

    @Test fun bottomNavigationReachesEveryTab() {
        launch()
        waitForText("Mission Control")
        rule.onNodeWithTag("tab_play").performClick()
        waitForText("Challenge Arena")
        rule.onNodeWithTag("tab_skills").performClick()
        waitForText("Career Skill Tree")
        rule.onNodeWithTag("tab_progress").performClick()
        waitForText("Learning Analytics")
        rule.onNodeWithTag("tab_profile").performClick()
        waitForTag("profile_name")
        rule.onNodeWithTag("tab_home").performClick()
        waitForText("Mission Control")
    }

    @Test fun completingADailyMissionAwardsXp() {
        val tc = launch()
        waitForText("Today's missions")
        val quiz = runBlocking { tc.progressRepository.ensureDailyMissions(c, tc.timeProvider.today()) }
            .first { it.slot == MissionSlot.QUIZ.name }
        val challenge = c.byId.getValue(quiz.challengeId)
        val correct = (challenge.answer as AnswerSpec.Choice).correctIndex
        waitForTag("mission_${challenge.id}")
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("mission_${challenge.id}"))
        rule.onNodeWithTag("mission_${challenge.id}").performClick()
        waitForTag("option_$correct")
        rule.onNodeWithTag("submit").assertIsNotEnabled()
        rule.onNodeWithTag("option_$correct").performClick()
        rule.onNodeWithTag("submit").assertIsEnabled()
        rule.onNodeWithTag("submit").performClick()
        waitForText("Correct!")
        waitForText("+${challenge.xp} XP earned")
        val xp = runBlocking { tc.progressRepository.observeTotalXp().first() }
        assertEquals(challenge.xp, xp)
        val missions = runBlocking { tc.progressRepository.observeMissions(tc.timeProvider.today()).first() }
        assertTrue(missions.first { it.challengeId == challenge.id }.completedAtEpochMs != null)
    }

    @Test fun incorrectAnswerShowsFeedbackAndRetryResets() {
        val tc = launch()
        waitForText("Today's missions")
        val quiz = runBlocking { tc.progressRepository.ensureDailyMissions(c, tc.timeProvider.today()) }
            .first { it.slot == MissionSlot.QUIZ.name }
        val challenge = c.byId.getValue(quiz.challengeId)
        val wrong = ((challenge.answer as AnswerSpec.Choice).correctIndex + 1) % challenge.options.size
        waitForTag("mission_${challenge.id}")
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("mission_${challenge.id}"))
        rule.onNodeWithTag("mission_${challenge.id}").performClick()
        waitForTag("option_$wrong")
        rule.onNodeWithTag("option_$wrong").performClick()
        rule.onNodeWithTag("submit").performClick()
        waitForText("Not quite")
        waitForTag("retry")
        rule.onNodeWithTag("retry").performClick()
        waitForTag("submit")
        rule.onNodeWithTag("submit").assertIsNotEnabled()
        val queue = runBlocking { tc.progressRepository.observeRevisionQueue().first() }
        assertEquals(listOf(challenge.id), queue.map { it.challengeId })
        assertEquals(0, runBlocking { tc.progressRepository.observeTotalXp().first() })
    }

    @Test fun reachingOneHundredXpShowsLevelUp() {
        val tc = TestAppContainer()
        container = tc
        runBlocking {
            tc.progressRepository.syncContent(c)
            for (id in listOf("py-002", "py-003", "py-008", "py-001")) { // 25 + 25 + 30 + 10 = 90 XP
                val ch = c.byId.getValue(id)
                val answer = when (val s = ch.answer) {
                    is AnswerSpec.Choice -> UserAnswer.Choice(s.correctIndex)
                    is AnswerSpec.Text -> UserAnswer.Text(s.accepted.first())
                    is AnswerSpec.Order -> UserAnswer.Order(s.steps)
                }
                tc.progressRepository.submit(c, ch, answer, AttemptContext.PRACTICE, 1)
            }
            assertEquals(90, tc.progressRepository.observeTotalXp().first())
        }
        rule.setContent { SkillVerseApp(tc) }
        waitForText("Mission Control")
        rule.onNodeWithTag("tab_play").performClick()
        waitForTag("category_PYTHON")
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("category_PYTHON"))
        rule.onNodeWithTag("category_PYTHON").performClick()
        waitForTag("category_list")
        rule.onNodeWithTag("category_list").performScrollToNode(hasTestTag("row_py-005"))
        rule.onNodeWithTag("row_py-005").performClick()
        val correct = (c.byId.getValue("py-005").answer as AnswerSpec.Choice).correctIndex
        waitForTag("option_$correct")
        rule.onNodeWithTag("option_$correct").performClick()
        rule.onNodeWithTag("submit").performClick()
        waitForTag("levelup_dialog")
        rule.onNodeWithText("You reached level 2.").assertIsDisplayed()
        rule.onNodeWithTag("levelup_ok").performClick()
        assertEquals(100, runBlocking { tc.progressRepository.observeTotalXp().first() })
    }

    @Test fun settingsChangesArePersisted() {
        val tc = launch()
        waitForText("Mission Control")
        rule.onNodeWithTag("tab_profile").performClick()
        waitForTag("profile_list")
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("theme_LIGHT"))
        rule.onNodeWithTag("theme_LIGHT").performClick()
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("motion_switch"))
        rule.onNodeWithTag("motion_switch").performClick()
        rule.waitUntil(5_000) { runBlocking { tc.settingsRepository.settings.first() }.reducedMotion }
        val s = runBlocking { tc.settingsRepository.settings.first() }
        assertEquals(ThemeMode.LIGHT, s.themeMode)
        assertTrue(s.reducedMotion)
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("daily_goal"))
        rule.onNodeWithTag("daily_goal").onChildren().filterToOne(hasContentDescription("Increase")).performClick()
        rule.waitUntil(5_000) { runBlocking { tc.settingsRepository.settings.first() }.dailyGoal == 4 }
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("edit_name"))
        rule.onNodeWithTag("edit_name").performClick()
        waitForTag("name_field")
        rule.onNodeWithTag("name_field").performTextReplacement("Asha Rao")
        rule.onNodeWithTag("save_name").performClick()
        rule.waitUntil(5_000) { runBlocking { tc.settingsRepository.settings.first() }.displayName == "Asha Rao" }
        waitForText("Asha Rao")
    }

    @Test fun freshInstallShowsEmptyStates() {
        launch()
        waitForText("Mission Control")
        rule.onNodeWithTag("home_list").performScrollToNode(hasText("No badges yet"))
        rule.onNodeWithText("No badges yet").assertIsDisplayed()
        rule.onNodeWithTag("tab_play").performClick()
        waitForTag("review_entry")
        rule.onNodeWithText("Nothing to review").assertIsDisplayed()
        rule.onNodeWithTag("review_entry").performClick()
        waitForTag("review_empty")
        rule.onNodeWithTag("review_empty").assertIsDisplayed()
    }

    @Test fun progressShowsZeroSafeAnalyticsOnFreshInstall() {
        launch()
        waitForText("Mission Control")
        rule.onNodeWithTag("tab_progress").performClick()
        waitForTag("stat_accuracy")
        rule.onNodeWithText("No attempts yet").assertExists()
        rule.onNodeWithTag("progress_list").performScrollToNode(hasTestTag("weak_empty"))
        rule.onNodeWithTag("weak_empty").assertIsDisplayed()
    }
}
