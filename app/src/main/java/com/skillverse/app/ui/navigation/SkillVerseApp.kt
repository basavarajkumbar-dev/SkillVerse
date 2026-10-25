package com.skillverse.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skillverse.app.AppContainer
import com.skillverse.app.core.designsystem.ErrorState
import com.skillverse.app.core.designsystem.LoadingState
import com.skillverse.app.core.designsystem.SkillVerseTheme
import com.skillverse.app.core.designsystem.SvIcons
import com.skillverse.app.data.settings.UserSettings
import com.skillverse.app.domain.model.AttemptContext
import com.skillverse.app.ui.AppUiState
import com.skillverse.app.ui.MainViewModel
import com.skillverse.app.ui.boss.BossScreen
import com.skillverse.app.ui.challenge.ChallengeScreen
import com.skillverse.app.ui.common.containerFactory
import com.skillverse.app.ui.home.HomeScreen
import com.skillverse.app.ui.onboarding.OnboardingScreen
import com.skillverse.app.ui.play.CategoryScreen
import com.skillverse.app.ui.play.PlayScreen
import com.skillverse.app.ui.play.ReviewScreen
import com.skillverse.app.ui.profile.ProfileScreen
import com.skillverse.app.ui.progress.ProgressScreen
import com.skillverse.app.ui.progress.ProjectScreen
import com.skillverse.app.ui.skills.SkillDetailScreen
import com.skillverse.app.ui.skills.SkillsScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    PLAY("play", "Play", Icons.Filled.PlayArrow),
    SKILLS("skills", "Skills", SvIcons.Tree),
    PROGRESS("progress", "Progress", SvIcons.Chart),
    PROFILE("profile", "Profile", Icons.Filled.Person),
}

object Routes {
    const val CHALLENGE = "challenge/{id}?ctx={ctx}"
    const val CATEGORY = "category/{category}"
    const val SKILL = "skill/{id}"
    const val PROJECT = "project/{id}"
    const val BOSS = "boss"
    const val REVIEW = "review"

    fun challenge(id: String, ctx: AttemptContext = AttemptContext.PRACTICE) = "challenge/$id?ctx=${ctx.name}"
    fun category(name: String) = "category/$name"
    fun skill(id: String) = "skill/$id"
    fun project(id: String) = "project/$id"
}

/** Root composable. Takes the container explicitly so UI tests can inject in-memory dependencies. */
@Composable
fun SkillVerseApp(container: AppContainer) {
    val main: MainViewModel = viewModel(factory = containerFactory { MainViewModel(container) })
    val settings by main.settings.collectAsStateWithLifecycle()
    val state by main.state.collectAsStateWithLifecycle()
    val s = settings ?: UserSettings()
    SkillVerseTheme(themeMode = s.themeMode, reducedMotion = s.reducedMotion) {
        Box(Modifier.fillMaxSize()) {
            when (val st = state) {
                AppUiState.Loading -> Scaffold { p -> LoadingState(Modifier.padding(p), label = "Loading SkillVerse") }
                is AppUiState.Failed -> Scaffold { p ->
                    ErrorState("Something went wrong", st.message, Modifier.padding(p), onRetry = main::start)
                }
                is AppUiState.Ready ->
                    if (!st.onboardingComplete) {
                        OnboardingScreen(onFinish = main::completeOnboarding)
                    } else {
                        MainShell(container, onReset = main::onReset)
                    }
            }
        }
    }
}

@Composable
private fun MainShell(container: AppContainer, onReset: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    Scaffold(
        bottomBar = {
            if (showBar) BottomBar(nav, route)
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    container,
                    openChallenge = { id, ctx -> nav.navigate(Routes.challenge(id, ctx)) },
                    openBoss = { nav.navigate(Routes.BOSS) },
                    openReview = { nav.navigate(Routes.REVIEW) },
                    openTab = { tab -> nav.switchTab(tab) },
                )
            }
            composable(Tab.PLAY.route) {
                PlayScreen(
                    container,
                    openCategory = { nav.navigate(Routes.category(it.name)) },
                    openBoss = { nav.navigate(Routes.BOSS) },
                    openReview = { nav.navigate(Routes.REVIEW) },
                    openChallenge = { id, ctx -> nav.navigate(Routes.challenge(id, ctx)) },
                )
            }
            composable(Tab.SKILLS.route) {
                SkillsScreen(container, openSkill = { nav.navigate(Routes.skill(it)) })
            }
            composable(Tab.PROGRESS.route) {
                ProgressScreen(
                    container,
                    openProject = { nav.navigate(Routes.project(it)) },
                    openReview = { nav.navigate(Routes.REVIEW) },
                    openTab = { nav.switchTab(it) },
                )
            }
            composable(Tab.PROFILE.route) {
                ProfileScreen(container, onReset = onReset)
            }
            composable(
                Routes.CHALLENGE,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("ctx") { type = NavType.StringType; defaultValue = AttemptContext.PRACTICE.name },
                ),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val ctx = AttemptContext.entries.firstOrNull { it.name == entry.arguments?.getString("ctx") } ?: AttemptContext.PRACTICE
                ChallengeScreen(
                    container, challengeId = id, context = ctx,
                    onBack = { nav.popBackStack() },
                    openChallenge = { next, nextCtx -> nav.navigate(Routes.challenge(next, nextCtx)) { popUpTo(Routes.CHALLENGE) { inclusive = true } } },
                )
            }
            composable(Routes.CATEGORY, arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
                CategoryScreen(
                    container,
                    categoryName = entry.arguments?.getString("category").orEmpty(),
                    onBack = { nav.popBackStack() },
                    openChallenge = { id, ctx -> nav.navigate(Routes.challenge(id, ctx)) },
                )
            }
            composable(Routes.SKILL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                SkillDetailScreen(
                    container,
                    skillId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    openChallenge = { nav.navigate(Routes.challenge(it)) },
                )
            }
            composable(Routes.PROJECT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                ProjectScreen(
                    container,
                    projectId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    openChallenge = { nav.navigate(Routes.challenge(it)) },
                )
            }
            composable(Routes.BOSS) { BossScreen(container, onBack = { nav.popBackStack() }) }
            composable(Routes.REVIEW) {
                ReviewScreen(container, onBack = { nav.popBackStack() }, openChallenge = { nav.navigate(Routes.challenge(it, AttemptContext.REVIEW)) })
            }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = { if (route != tab.route) nav.switchTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("tab_${tab.route}"),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ),
            )
        }
    }
}
