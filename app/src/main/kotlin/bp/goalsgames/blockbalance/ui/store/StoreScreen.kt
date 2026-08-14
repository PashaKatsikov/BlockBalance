package bp.goalsgames.blockbalance.ui.store

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.meta.BlockStyle
import bp.goalsgames.blockbalance.meta.Catalogue
import bp.goalsgames.blockbalance.meta.SkyMood
import bp.goalsgames.blockbalance.ui.ShellViewModel
import bp.goalsgames.blockbalance.ui.components.ScreenHeader
import bp.goalsgames.blockbalance.ui.components.ValueChip
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.formatCredits
import bp.goalsgames.blockbalance.ui.groupDigits
import bp.goalsgames.blockbalance.ui.rememberAssetImage
import bp.goalsgames.blockbalance.ui.theme.Yard

@Composable
fun StoreScreen(
    profile: PlayerProfile,
    shell: ShellViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(Yard.night)) {
        Box(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(
                title = "STORE",
                onBack = onBack,
                trailing = {
                    ValueChip(label = Economy.CURRENCY_LABEL, value = formatCredits(profile.credits))
                },
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 4.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                YardPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.store_shuffle),
                                style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
                            )
                            Text(
                                text = "Picks a random owned style for every block",
                                style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                            )
                        }
                        Switch(
                            checked = profile.shuffleStyles,
                            onCheckedChange = shell::setShuffle,
                        )
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.store_blocks)) }

            items(Catalogue.blockStyles, key = { "style-${it.id}" }) { style ->
                BlockStyleRow(
                    style = style,
                    owned = profile.owns(style.id),
                    equipped = profile.activeStyle == style.id && !profile.shuffleStyles,
                    affordable = profile.credits >= style.price,
                    onClick = { shell.selectStyle(style.id) },
                )
            }

            item { SectionLabel(stringResource(R.string.store_skies)) }

            items(Catalogue.skyMoods, key = { "sky-${it.id}" }) { mood ->
                SkyMoodRow(
                    mood = mood,
                    owned = profile.ownsSky(mood.id),
                    equipped = profile.activeSky == mood.id,
                    rank = profile.rank.rank,
                    affordable = profile.credits >= mood.price,
                    onClick = { shell.selectSky(mood.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(color = Yard.gold),
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun BlockStyleRow(
    style: BlockStyle,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    val actionable = owned || affordable
    YardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = actionable, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sprite = rememberAssetImage(style.asset, widthFraction = 0.3f)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth(0.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Yard.shadow),
                contentAlignment = Alignment.Center,
            ) {
                if (sprite != null) {
                    Image(
                        bitmap = sprite,
                        contentDescription = style.title,
                        modifier = Modifier.padding(5.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.title,
                    style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
                )
                Text(
                    text = when {
                        equipped -> stringResource(R.string.store_equipped)
                        owned -> stringResource(R.string.store_owned)
                        style.giftRank > 0 -> "Free at rank ${style.giftRank}"
                        else -> "Locked"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (equipped) Yard.mint else Yard.inkSoft,
                    ),
                )
            }

            PriceTag(owned = owned, equipped = equipped, price = style.price, affordable = affordable)
        }
    }
}

@Composable
private fun SkyMoodRow(
    mood: SkyMood,
    owned: Boolean,
    equipped: Boolean,
    rank: Int,
    affordable: Boolean,
    onClick: () -> Unit,
) {
    val rankOk = rank >= mood.requiredRank
    val actionable = owned || (affordable && rankOk)
    YardPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = actionable, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth(0.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                blendPreview(mood.washTop, mood.washAlpha),
                                blendPreview(mood.washBottom, mood.washAlpha),
                            ),
                        ),
                    )
                    .border(1.dp, Yard.stroke, RoundedCornerShape(12.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mood.title,
                    style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
                )
                Text(
                    text = when {
                        equipped -> stringResource(R.string.store_equipped)
                        owned -> stringResource(R.string.store_owned)
                        !rankOk -> "Needs rank ${mood.requiredRank}"
                        else -> "Available"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (equipped) Yard.mint else Yard.inkSoft,
                    ),
                )
            }

            PriceTag(owned = owned, equipped = equipped, price = mood.price, affordable = affordable && rankOk)
        }
    }
}

@Composable
private fun PriceTag(owned: Boolean, equipped: Boolean, price: Long, affordable: Boolean) {
    val label = when {
        equipped -> "ON"
        owned -> "USE"
        price == 0L -> "FREE"
        else -> groupDigits(price)
    }
    val tint = when {
        equipped -> Yard.mint
        owned -> Yard.sky
        affordable -> Yard.gold
        else -> Yard.stroke
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Yard.shadow)
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(color = tint))
    }
}

/** Flattens a sky wash against the daylight backdrop for the store swatch. */
private fun blendPreview(wash: Int, alpha: Float): Color {
    val base = Color(0xFF6FBDF2)
    if (alpha <= 0.001f) return base
    val overlay = Color(wash)
    val mix = alpha.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1 - mix) + overlay.red * mix,
        green = base.green * (1 - mix) + overlay.green * mix,
        blue = base.blue * (1 - mix) + overlay.blue * mix,
        alpha = 1f,
    )
}
