package bp.goalsgames.blockbalance.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import bp.goalsgames.blockbalance.stage.StageScene
import bp.goalsgames.blockbalance.stage.StageSurfaceView

/**
 * Embeds the yard surface in the Compose tree and parks the render thread while
 * the screen is in the background.
 */
@Composable
fun StageHost(
    scene: StageScene,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val surface = remember { mutableStateOf<StageSurfaceView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            StageSurfaceView(context).also { view ->
                view.scene = scene
                view.onTap = onTap
                surface.value = view
            }
        },
        update = { view ->
            view.scene = scene
            view.onTap = onTap
            surface.value = view
        },
        onRelease = { view ->
            view.onTap = null
            view.scene = null
            surface.value = null
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> surface.value?.pauseRendering()
                Lifecycle.Event.ON_RESUME -> surface.value?.resumeRendering()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
