package bp.goalsgames.blockbalance.ui.play

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.data.RankGain
import bp.goalsgames.blockbalance.ui.components.GhostButton
import bp.goalsgames.blockbalance.ui.components.GlyphButton
import bp.goalsgames.blockbalance.ui.components.HazardButton
import bp.goalsgames.blockbalance.ui.components.PlateButton
import bp.goalsgames.blockbalance.ui.components.SolidButton
import bp.goalsgames.blockbalance.ui.components.SteelButton
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.formatMultiplier
import bp.goalsgames.blockbalance.ui.groupDigits
import bp.goalsgames.blockbalance.ui.theme.Yard
import kotlinx.coroutines.delay

/**
 * The yard fills the window and the chrome sits on top of it: a balance strip
 * above, the run history down the right edge, and one deck of controls at the
 * bottom where the hoist is worked.
 */
@Composable
fun PlayScreen(
    viewModel: PlayViewModel,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onStageAttached() }
    GameplaySystemBars()

    Box(modifier = modifier.background(Yard.night)) {
        StageHost(
            scene = viewModel.scene,
            onTap = viewModel::onYardTap,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopStrip(
                credits = state.credits,
                onExit = onExit,
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                GlyphButton(
                    glyph = "•••",
                    onClick = onSettings,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
                )
                ResultsColumn(
                    steps = state.recentSteps,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                )
                HeadlineLayer(
                    headline = state.headline,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                )
            }

            AnimatedVisibility(
                visible = state.controlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                ControlDeck(
                    state = state,
                    onBuild = viewModel::onPrimaryAction,
                    onCashout = viewModel::onBank,
                    onStep = viewModel::onStakeStep,
                    onDouble = viewModel::onStakeDouble,
                    onAllIn = viewModel::onStakeAllIn,
                )
            }
        }

        LowFundsToast(
            visible = state.showLowFunds,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 190.dp),
        )

        state.bankPanel?.let { panel ->
            BankedOverlay(
                panel = panel,
                onCollect = viewModel::onCollect,
                onHome = {
                    viewModel.onCollect()
                    onExit()
                },
            )
        }

        state.rankUp?.let { gain ->
            RankUpOverlay(gain = gain, onDismiss = viewModel::onRankUpDismissed)
        }
    }
}

/** Gameplay owns the whole portrait window; menus restore the normal system bars. */
@Composable
private fun GameplaySystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Compact operator strip matching the proportions of the reference HUD. */
@Composable
private fun TopStrip(
    credits: Long,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Yard.night.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "☰", style = MaterialTheme.typography.labelMedium.copy(color = Yard.ink))
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "ID: BB000001",
                style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
            )
            Text(
                text = "${groupDigits(credits)} ${Economy.CURRENCY_LABEL}",
                style = MaterialTheme.typography.labelMedium.copy(color = Yard.ink),
            )
        }
    }
}

/** Newest step multiplier on top, six at most, the way the results tape reads. */
@Composable
private fun ResultsColumn(steps: List<Double>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = stringResource(R.string.play_results),
            style = MaterialTheme.typography.labelMedium.copy(
                color = Yard.ink,
                shadow = Shadow(color = Yard.shadow, blurRadius = 8f),
            ),
        )
        steps.forEach { step ->
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.verticalGradient(listOf(Yard.gold, Yard.goldDeep)))
                    .border(1.dp, Color(0x55000000), RoundedCornerShape(7.dp))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "x${formatMultiplier(step)}",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF3B2400)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The elastic multiplier pop; a miss shows the same slot in red. */
@Composable
private fun HeadlineLayer(headline: Headline?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Headline?>(null) }

    LaunchedEffect(headline?.id) {
        val current = headline ?: return@LaunchedEffect
        shown = current
        delay(1_300)
        shown = null
    }

    val visible = shown != null
    val pop by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "headline-pop",
    )
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(240),
        label = "headline-fade",
    )

    val current = shown ?: headline ?: return
    val tint = if (current.tone == HeadlineTone.GAIN) Yard.gold else Yard.ember

    Text(
        text = current.text,
        style = MaterialTheme.typography.displayLarge.copy(
            color = tint,
            shadow = Shadow(color = Yard.shadow, blurRadius = 22f),
            textAlign = TextAlign.Center,
        ),
        modifier = modifier.graphicsLayer {
            alpha = fade
            scaleX = pop
            scaleY = pop
        },
    )
}

/**
 * Before a run the deck offers the stake keys and one wide hoist key; once a
 * block is up it offers the same hoist key next to the cash out.
 */
@Composable
private fun ControlDeck(
    state: PlayUiState,
    onBuild: () -> Unit,
    onCashout: () -> Unit,
    onStep: (Long) -> Unit,
    onDouble: () -> Unit,
    onAllIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Yard.night.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (state.stakeEditable) {
            StakeBar(
                stake = state.stake,
                onStep = onStep,
                onDouble = onDouble,
                onAllIn = onAllIn,
            )
        }

        if (state.canBank) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SteelButton(
                    label = stringResource(R.string.play_cashout),
                    onClick = onCashout,
                    modifier = Modifier.weight(1f),
                    detail = "${groupDigits(state.potential)} ${Economy.CURRENCY_LABEL}",
                )
                HazardButton(
                    onClick = onBuild,
                    modifier = Modifier.weight(1f),
                    enabled = state.canRelease,
                )
            }
        } else {
            HazardButton(
                onClick = onBuild,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canRelease,
            )
        }
    }
}

/** All in, a stake stepper and the doubling key, as one row. */
@Composable
private fun StakeBar(
    stake: Long,
    onStep: (Long) -> Unit,
    onDouble: () -> Unit,
    onAllIn: () -> Unit,
) {
    val step = Economy.stepFor(stake)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SteelButton(
            label = stringResource(R.string.play_all_in),
            onClick = onAllIn,
            modifier = Modifier.weight(1f),
            height = 38.dp,
            labelStyle = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier
                .weight(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(Yard.shadow)
                .border(1.dp, Yard.stroke, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepKey(glyph = "−", onClick = { onStep(-step) })
            Text(
                text = groupDigits(stake),
                style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            StepKey(glyph = "+", onClick = { onStep(step) })
        }
        SteelButton(
            label = stringResource(R.string.play_double),
            onClick = onDouble,
            modifier = Modifier.weight(1f),
            height = 38.dp,
            labelStyle = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StepKey(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge.copy(color = Yard.ink),
        )
    }
}

@Composable
private fun LowFundsToast(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Yard.ember.copy(alpha = 0.94f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.play_low_funds),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BankedOverlay(
    panel: BankPanel,
    onCollect: () -> Unit,
    onHome: () -> Unit,
) {
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bank-pop",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Yard.shadow.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        YardPanel(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
            corner = 28.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.play_banked),
                    style = MaterialTheme.typography.displayMedium.copy(color = Yard.gold),
                )
                Text(
                    text = "FLOOR ${panel.floors} · x${formatMultiplier(panel.combo)}",
                    style = MaterialTheme.typography.titleMedium.copy(color = Yard.inkSoft),
                )
                Text(
                    text = "+${groupDigits(panel.payout)} ${Economy.CURRENCY_LABEL}",
                    style = MaterialTheme.typography.displayMedium.copy(color = Yard.mint),
                )
                Spacer(modifier = Modifier.size(6.dp))
                PlateButton(
                    label = stringResource(R.string.play_collect),
                    onClick = onCollect,
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostButton(
                    label = stringResource(R.string.play_home),
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RankUpOverlay(
    gain: RankGain,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Yard.shadow.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        YardPanel(modifier = Modifier.fillMaxWidth(0.8f), corner = 24.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "RANK ${gain.toRank}",
                    style = MaterialTheme.typography.displayMedium.copy(color = Yard.sky),
                )
                Text(
                    text = "+${groupDigits(gain.credits)} ${Economy.CURRENCY_LABEL}",
                    style = MaterialTheme.typography.titleLarge.copy(color = Yard.gold),
                )
                if (gain.unlockedStyles.isNotEmpty()) {
                    Text(
                        text = "New block style unlocked",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Yard.mint),
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                SolidButton(
                    label = "NICE",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    tint = Yard.sky,
                )
            }
        }
    }
}
