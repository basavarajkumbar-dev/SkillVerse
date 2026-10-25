package com.skillverse.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skillverse.app.TestContent
import com.skillverse.app.data.settings.ThemeMode
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.model.AnswerSpec
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.domain.validation.UserAnswer
import com.skillverse.app.ui.navigation.SkillVerseApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens with seeded, real progress and writes PNGs to app/build/screenshots
 * for visual review (published by CI). Assertions are minimal; this is a review aid.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val c = TestContent.curriculum
    private var container: TestAppContainer? = null
    private val outDir = File("build/screenshots").apply { mkdirs() }

    @After fun tearDown() { container?.db?.close() }

    private fun seeded(theme: ThemeMode): TestAppContainer {
        val tc = TestAppContainer(UserSettings(onboardingComplete = true, displayName = "Basavaraj", avatarId = 0, themeMode = theme))
        container = tc
        runBlocking {
            tc.progressRepository.syncContent(c)
            val day = tc.timeProvider.today()
            // Six days of real activity, then today's missions partly done.
            val ids = c.challengesForSkill("python_foundations").map { it.id } + c.challengesForSkill("data_structures").map { it.id }.take(4)
            ids.forEachIndexed { i, id ->
                tc.timeProvider.setDate(day.minusDays((5 - i / 3).coerceAtLeast(0).toLong()))
                val ch = c.byId.getValue(id)
                val answer = if (i % 5 == 4) wrong(ch) else right(ch)
                tc.progressRepository.submit(c, ch, answer, AttemptContext.PRACTICE, 1000)
            }
            tc.timeProvider.setDate(day, hour = 10)
            val missions = tc.progressRepository.ensureDailyMissions(c, day)
            missions.firstOrNull()?.let { m -> c.byId[m.challengeId]?.let { tc.progressRepository.submit(c, it, right(it), AttemptContext.DAILY, 1000) } }
            tc.progressRepository.ensureWeeklyBoss(c, day)
        }
        return tc
    }

    private fun right(ch: com.skillverse.app.domain.model.Challenge): UserAnswer = when (val s = ch.answer) {
        is AnswerSpec.Choice -> UserAnswer.Choice(s.correctIndex)
        is AnswerSpec.Text -> UserAnswer.Text(s.accepted.first())
        is AnswerSpec.Order -> UserAnswer.Order(s.steps)
    }

    private fun wrong(ch: com.skillverse.app.domain.model.Challenge): UserAnswer = when (val s = ch.answer) {
        is AnswerSpec.Choice -> UserAnswer.Choice((s.correctIndex + 1) % ch.options.size)
        is AnswerSpec.Text -> UserAnswer.Text("nope")
        is AnswerSpec.Order -> UserAnswer.Order(s.steps.reversed())
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitTag(tag: String) = rule.waitUntilAtLeastOneExists(hasTestTag(tag), 10_000)

    @Test fun darkThemeTour() {
        val tc = seeded(ThemeMode.DARK)
        rule.setContent { SkillVerseApp(tc) }
        rule.waitUntilAtLeastOneExists(hasText("Mission Control"), 10_000)
        shot("01_home_dark")
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("boss_card"))
        shot("02_home_scrolled_dark")
        rule.onNodeWithTag("tab_play").performClick(); waitTag("play_list"); shot("03_play_dark")
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("category_PYTHON"))
        rule.onNodeWithTag("category_PYTHON").performClick(); waitTag("category_list"); shot("04_category_dark")
        rule.onNodeWithTag("row_py-007").performClick(); waitTag("submit"); shot("05_challenge_puzzle_dark")
    }

    @Test fun darkThemeTabs() {
        val tc = seeded(ThemeMode.DARK)
        rule.setContent { SkillVerseApp(tc) }
        rule.waitUntilAtLeastOneExists(hasText("Mission Control"), 10_000)
        rule.onNodeWithTag("tab_skills").performClick(); waitTag("skills_list"); shot("06_skills_dark")
        rule.onNodeWithTag("tab_progress").performClick(); waitTag("progress_list"); shot("07_progress_dark")
        rule.onNodeWithTag("progress_list").performScrollToNode(hasTestTag("project_proj-log-parser")); shot("08_progress_scrolled_dark")
        rule.onNodeWithTag("tab_profile").performClick(); waitTag("profile_list"); shot("09_profile_dark")
    }

    @Test fun challengeResultAndLightTheme() {
        val tc = seeded(ThemeMode.LIGHT)
        rule.setContent { SkillVerseApp(tc) }
        rule.waitUntilAtLeastOneExists(hasText("Mission Control"), 10_000)
        shot("10_home_light")
        rule.onNodeWithTag("tab_play").performClick(); waitTag("play_list")
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("category_DEBUGGING"))
        rule.onNodeWithTag("category_DEBUGGING").performClick(); waitTag("category_list")
        rule.onNodeWithTag("category_list").performScrollToNode(hasTestTag("row_dbg-001"))
        rule.onNodeWithTag("row_dbg-001").performClick()
        val ch = c.byId.getValue("dbg-001")
        val wrongIdx = ((ch.answer as AnswerSpec.Choice).correctIndex + 1) % ch.options.size
        waitTag("option_$wrongIdx")
        rule.onNodeWithTag("option_$wrongIdx").performClick()
        rule.onNodeWithTag("submit").performClick()
        waitTag("result_panel")
        shot("11_challenge_wrong_light")
        rule.onNodeWithTag("retry").performScrollTo().performClick(); waitTag("submit")
        val right = (ch.answer as AnswerSpec.Choice).correctIndex
        rule.onNodeWithTag("option_$right").performScrollTo().performClick()
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        waitTag("explanation")
        shot("12_challenge_correct_light")
    }

    @Test fun onboardingAndArrange() {
        val tc = TestAppContainer(UserSettings())
        container = tc
        rule.setContent { SkillVerseApp(tc) }
        waitTag("onboarding_next")
        shot("13_onboarding_dark")
    }
}
