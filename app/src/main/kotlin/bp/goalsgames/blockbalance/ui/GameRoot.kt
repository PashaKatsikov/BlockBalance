package bp.goalsgames.blockbalance.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import bp.goalsgames.blockbalance.ui.boot.BootScreen
import bp.goalsgames.blockbalance.ui.home.HomeScreen
import bp.goalsgames.blockbalance.ui.ladder.LadderScreen
import bp.goalsgames.blockbalance.ui.legal.LegalScreen
import bp.goalsgames.blockbalance.ui.play.PlayScreen
import bp.goalsgames.blockbalance.ui.play.PlayViewModel
import bp.goalsgames.blockbalance.ui.quests.QuestsScreen
import bp.goalsgames.blockbalance.ui.settings.SettingsScreen
import bp.goalsgames.blockbalance.ui.store.StoreScreen

/**
 * Holds the back stack and both view models. Screens receive plain callbacks so
 * none of them need to know how navigation is wired.
 */
@Composable
fun GameRoot(modifier: Modifier = Modifier) {
    val navigator = remember { Navigator() }
    val shell: ShellViewModel = viewModel()
    val play: PlayViewModel = viewModel()
    val profile by shell.profile.collectAsStateWithLifecycle()
    val destination = navigator.current

    LockOrientation(portrait = destination != Destination.Boot)

    BackHandler(enabled = destination != Destination.Home && destination != Destination.Boot) {
        navigator.back()
    }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "screen",
        ) { target ->
            when (target) {
                Destination.Boot -> BootScreen(
                    onReady = { navigator.reset(Destination.Home) },
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Home -> HomeScreen(
                    profile = profile,
                    shell = shell,
                    onPlay = { navigator.go(Destination.Play) },
                    onStore = { navigator.go(Destination.Store) },
                    onQuests = { navigator.go(Destination.Quests) },
                    onLadder = { navigator.go(Destination.Ladder) },
                    onSettings = { navigator.go(Destination.Settings) },
                    onLegal = { page -> navigator.go(Destination.Legal(page)) },
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Play -> PlayScreen(
                    viewModel = play,
                    onExit = { navigator.back() },
                    onSettings = { navigator.go(Destination.Settings) },
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Store -> StoreScreen(
                    profile = profile,
                    shell = shell,
                    onBack = { navigator.back() },
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Quests -> QuestsScreen(
                    profile = profile,
                    shell = shell,
                    onBack = { navigator.back() },
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Ladder -> LadderScreen(
                    profile = profile,
                    shell = shell,
                    onBack = { navigator.back() },
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Settings -> SettingsScreen(
                    profile = profile,
                    shell = shell,
                    onBack = { navigator.back() },
                    onLegal = { page -> navigator.go(Destination.Legal(page)) },
                    modifier = Modifier.fillMaxSize(),
                )

                is Destination.Legal -> LegalScreen(
                    page = target.page,
                    onBack = { navigator.back() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The yard is composed for a tall viewport, so gameplay and menus stay portrait
 * while the boot screen is free to use whatever the device is holding.
 */
@Composable
private fun LockOrientation(portrait: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(activity, portrait) {
        activity?.requestedOrientation = if (portrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
