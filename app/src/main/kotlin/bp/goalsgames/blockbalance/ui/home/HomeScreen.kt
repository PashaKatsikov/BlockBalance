package bp.goalsgames.blockbalance.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.legal.LegalPage
import bp.goalsgames.blockbalance.stage.Art
import bp.goalsgames.blockbalance.ui.ShellViewModel
import bp.goalsgames.blockbalance.ui.bonus.BonusDialog
import bp.goalsgames.blockbalance.ui.components.GhostButton
import bp.goalsgames.blockbalance.ui.components.MenuTile
import bp.goalsgames.blockbalance.ui.components.MeterBar
import bp.goalsgames.blockbalance.ui.components.PlateButton
import bp.goalsgames.blockbalance.ui.components.ValueChip
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.formatCredits
import bp.goalsgames.blockbalance.ui.rememberAssetImage
import bp.goalsgames.blockbalance.ui.theme.Yard

@Composable
fun HomeScreen(
    profile: PlayerProfile,
    shell: ShellViewModel,
    onPlay: () -> Unit,
    onStore: () -> Unit,
    onQuests: () -> Unit,
    onLadder: () -> Unit,
    onSettings: () -> Unit,
    onLegal: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bonusOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { shell.refreshCalendar() }

    val bonus = shell.bonusState(profile)
    val questBadge = remember(profile) { shell.questCards(profile).count { it.claimable } }
    val rank = profile.rank

    val drift = rememberInfiniteTransition(label = "logo-drift")
    val lift by drift.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(3_200), repeatMode = RepeatMode.Reverse),
        label = "logo-lift",
    )

    Box(modifier = modifier.background(Yard.night)) {
        val backdrop = rememberAssetImage(Art.MENU_BACKDROP)
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
                        0f to Yard.night.copy(alpha = 0.18f),
                        0.45f to Yard.night.copy(alpha = 0.35f),
                        1f to Yard.night.copy(alpha = 0.92f),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ValueChip(label = Economy.CURRENCY_LABEL, value = formatCredits(profile.credits))
                Spacer(modifier = Modifier.weight(1f))
                GhostButton(label = "SETTINGS", onClick = onSettings)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val logo = rememberAssetImage(Art.LOGO, widthFraction = 0.9f)
                if (logo != null) {
                    Image(
                        bitmap = logo,
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .graphicsLayer { translationY = lift },
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium.copy(color = Yard.gold),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            YardPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                corner = 26.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "RANK ${rank.rank}",
                            style = MaterialTheme.typography.titleMedium.copy(color = Yard.gold),
                        )
                        MeterBar(ratio = rank.progress, modifier = Modifier.weight(1f))
                        Text(
                            text = if (rank.isMaxed) "MAX" else "${rank.xpInto}/${rank.xpForNext}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                        )
                    }

                    PlateButton(
                        label = stringResource(R.string.home_play),
                        onClick = onPlay,
                        modifier = Modifier.fillMaxWidth(),
                        height = 78.dp,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MenuTile(
                            title = stringResource(R.string.home_bonus),
                            caption = if (bonus.claimable) "Ready" else "Claimed",
                            badge = if (bonus.claimable) "!" else null,
                            onClick = { bonusOpen = true },
                            modifier = Modifier.weight(1f),
                            accent = Yard.gold,
                        )
                        MenuTile(
                            title = stringResource(R.string.home_quests),
                            caption = "3 daily",
                            badge = questBadge.takeIf { it > 0 }?.toString(),
                            onClick = onQuests,
                            modifier = Modifier.weight(1f),
                            accent = Yard.mint,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MenuTile(
                            title = stringResource(R.string.home_store),
                            caption = "${profile.ownedStyles.size}/4 styles",
                            badge = null,
                            onClick = onStore,
                            modifier = Modifier.weight(1f),
                        )
                        MenuTile(
                            title = stringResource(R.string.home_ladder),
                            caption = "${shell.ladderDaysLeft()}d left",
                            badge = null,
                            onClick = onLadder,
                            modifier = Modifier.weight(1f),
                            accent = Yard.ember,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GhostButton(
                            label = stringResource(R.string.home_privacy),
                            onClick = { onLegal(LegalPage.PRIVACY) },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            label = stringResource(R.string.home_support),
                            onClick = { onLegal(LegalPage.SUPPORT) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }

    if (bonusOpen) {
        BonusDialog(
            state = bonus,
            onClaim = {
                shell.claimBonus()
                bonusOpen = false
            },
            onDismiss = { bonusOpen = false },
        )
    }
}
