package bp.goalsgames.blockbalance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import bp.goalsgames.blockbalance.stage.SpriteBank

/**
 * Loads an asset through [SpriteBank] and hands it to Compose. Sprites are
 * decoded once per process, so recomposition and screen changes are cheap.
 */
@Composable
fun rememberAssetImage(path: String, widthFraction: Float = 1f): ImageBitmap? {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val targetEdge = (windowInfo.containerSize.width.coerceAtLeast(320) * widthFraction).toInt()
    val state = produceState<ImageBitmap?>(initialValue = null, path, targetEdge) {
        val cached = SpriteBank.bitmap(path)
        if (cached != null) {
            value = cached.asImageBitmap()
            return@produceState
        }
        value = SpriteBank.ensure(context, path, targetEdge)?.asImageBitmap()
    }
    return state.value
}
