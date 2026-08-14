package bp.goalsgames.blockbalance.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.stage.Art
import bp.goalsgames.blockbalance.ui.rememberAssetImage
import bp.goalsgames.blockbalance.ui.theme.Yard
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme

/** Frosted panel used for every grouped block of controls. */
@Composable
fun YardPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.verticalGradient(
                    listOf(Yard.panelHigh.copy(alpha = 0.94f), Yard.panel.copy(alpha = 0.96f)),
                ),
            )
            .border(BorderStroke(1.dp, Yard.stroke.copy(alpha = 0.85f)), RoundedCornerShape(corner)),
    ) {
        content()
    }
}

/**
 * The signature action button: the hazard plate from the art pack with the label
 * drawn on top, so the same plate can say anything.
 */
@Composable
fun PlateButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 74.dp,
    labelStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "plate-press",
    )
    val plate = rememberAssetImage(Art.PLATE, widthFraction = 0.9f)

    Box(
        modifier = modifier
            .height(height)
            .scale(squeeze)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(Yard.gold, Yard.goldDeep)),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (plate != null) {
            Image(
                bitmap = plate,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        Text(
            text = label,
            style = labelStyle.copy(
                color = Color(0xFF3B2400),
                shadow = Shadow(color = Color(0x66FFFFFF), blurRadius = 6f),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

/**
 * The hoist action: the hazard plate from the art pack, which already carries the
 * BUILD wording. The gradient underneath only shows while the art is decoding.
 */
@Composable
fun HazardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "hazard-press",
    )
    val plate = rememberAssetImage(Art.BUILD_PLATE, widthFraction = 0.9f)

    Box(
        modifier = modifier
            .height(height)
            .scale(squeeze)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (plate != null) {
            Image(
                bitmap = plate,
                contentDescription = stringResource(R.string.play_build),
                modifier = Modifier.fillMaxSize(),
                // Preserve the metal texture and lettering instead of stretching
                // the source differently for full-width and half-width layouts.
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Yard.gold, Yard.goldDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.play_build),
                    style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFF3B2400)),
                )
            }
        }
    }
}

/** Blue action button with an optional second line, used for cash out and stakes. */
@Composable
fun SteelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "steel-press",
    )
    Box(
        modifier = modifier
            .height(height)
            .scale(squeeze)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Yard.steel, Yard.steelDeep)))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(text = label, style = labelStyle.copy(color = Color.White), maxLines = 1)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Round chrome button, the settings dot that floats over the yard. */
@Composable
fun GlyphButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 32.dp,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(RoundedCornerShape(diameter / 2))
            .background(Brush.verticalGradient(listOf(Yard.steel, Yard.steelDeep)))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(diameter / 2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelLarge.copy(color = Color.White),
        )
    }
}

/** Flat coloured button for secondary actions. */
@Composable
fun SolidButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Yard.mint,
    height: Dp = 60.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(90),
        label = "solid-press",
    )
    Box(
        modifier = modifier
            .height(height)
            .scale(squeeze)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.72f))))
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f)), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF07281C)),
        )
    }
}

/** Outlined pill for chrome-level actions. */
@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Yard.panel.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, Yard.stroke), RoundedCornerShape(14.dp))
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(color = Yard.ink))
    }
}

/** Rounded readout used for the credit balance and rank. */
@Composable
fun ValueChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Yard.gold,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Yard.shadow)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.4f)), RoundedCornerShape(30.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = Yard.inkSoft),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(color = accent),
        )
    }
}

/** Thin progress meter for experience and quest progress. */
@Composable
fun MeterBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    track: Color = Yard.shadow,
    fill: Brush = Brush.horizontalGradient(listOf(Yard.sky, Yard.mint)),
    thickness: Dp = 10.dp,
) {
    val clamped = ratio.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(thickness)
            .clip(RoundedCornerShape(thickness))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .clip(RoundedCornerShape(thickness))
                .background(fill),
        )
    }
}

/** Square menu tile with an optional unread badge. */
@Composable
fun MenuTile(
    title: String,
    caption: String,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Yard.sky,
) {
    Box(modifier = modifier) {
        YardPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            corner = 18.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(color = accent),
                    maxLines = 1,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                    maxLines = 1,
                )
            }
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Yard.ember),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

/** Header used by every secondary screen. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GhostButton(label = stringResource(R.string.common_back).uppercase(), onClick = onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(color = Yard.ink),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailing()
    }
}
