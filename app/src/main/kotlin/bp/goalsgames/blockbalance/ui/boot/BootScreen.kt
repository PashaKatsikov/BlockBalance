package bp.goalsgames.blockbalance.ui.boot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.stage.Art
import bp.goalsgames.blockbalance.stage.SpriteBank
import bp.goalsgames.blockbalance.ui.components.MeterBar
import bp.goalsgames.blockbalance.ui.rememberAssetImage
import bp.goalsgames.blockbalance.ui.theme.Yard
import kotlinx.coroutines.delay
import kotlin.math.min

private const val MINIMUM_VISIBLE_MILLIS = 2_400L
private const val TICK_MILLIS = 40L

/**
 * Decodes the art pack while a progress bar runs. The bar never finishes early:
 * a boot screen that blinks past is worse than one that holds for a moment.
 */
@Composable
fun BootScreen(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val landscape = windowInfo.containerSize.width > windowInfo.containerSize.height

    var progress by remember { mutableFloatStateOf(0f) }
    var assetsReady by remember { mutableStateOf(SpriteBank.loaded) }

    LaunchedEffect(Unit) {
        val width = windowInfo.containerSize.width.coerceAtLeast(720)
        val height = windowInfo.containerSize.height.coerceAtLeast(1_280)
        SpriteBank.preload(context, width, height)
        assetsReady = true
    }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val timed = elapsed.toFloat() / MINIMUM_VISIBLE_MILLIS
            progress = min(1f, timed)
            if (progress >= 1f && assetsReady) break
            delay(TICK_MILLIS)
        }
        onReady()
    }

    val eased by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(180),
        label = "boot-progress",
    )

    Box(modifier = modifier.background(Yard.night), contentAlignment = Alignment.Center) {
        val backdrop = rememberAssetImage(
            if (landscape) Art.LOADING_LANDSCAPE else Art.LOADING_PORTRAIT,
        )
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Yard.night.copy(alpha = 0.25f),
                            Yard.night.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val logo = rememberAssetImage(Art.LOGO, widthFraction = 0.9f)
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxWidth(if (landscape) 0.42f else 0.82f),
                    contentScale = ContentScale.FillWidth,
                )
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium.copy(color = Yard.gold),
                )
            }

            Box(modifier = Modifier.height(38.dp))

            MeterBar(
                ratio = eased,
                modifier = Modifier.fillMaxWidth(if (landscape) 0.34f else 0.62f),
                thickness = 12.dp,
            )

            Box(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.boot_status),
                style = MaterialTheme.typography.bodyMedium.copy(color = Yard.inkSoft),
            )
        }
    }
}
