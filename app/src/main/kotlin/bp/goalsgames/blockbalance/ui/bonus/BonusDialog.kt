package bp.goalsgames.blockbalance.ui.bonus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.meta.BonusCalendar
import bp.goalsgames.blockbalance.meta.BonusState
import bp.goalsgames.blockbalance.ui.components.GhostButton
import bp.goalsgames.blockbalance.ui.components.PlateButton
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.groupDigits
import bp.goalsgames.blockbalance.ui.theme.Yard

/** Seven day reward ladder; today's slot is highlighted. */
@Composable
fun BonusDialog(
    state: BonusState,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        YardPanel(modifier = Modifier.fillMaxWidth(), corner = 26.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bonus_title).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(color = Yard.gold),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (1..4).forEach { slot ->
                            DaySlot(
                                slot = slot,
                                reward = BonusCalendar.rewardForSlot(slot),
                                isTarget = slot == state.slot,
                                claimed = slot < state.slot || (!state.claimable && slot == state.slot),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (5..7).forEach { slot ->
                            DaySlot(
                                slot = slot,
                                reward = BonusCalendar.rewardForSlot(slot),
                                isTarget = slot == state.slot,
                                claimed = slot < state.slot || (!state.claimable && slot == state.slot),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Box(modifier = Modifier.weight(1f))
                    }
                }

                if (state.claimable) {
                    PlateButton(
                        label = "${stringResource(R.string.bonus_claim)}  +${groupDigits(state.reward)}",
                        onClick = onClaim,
                        modifier = Modifier.fillMaxWidth(),
                        height = 66.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.bonus_locked),
                        style = MaterialTheme.typography.bodyMedium.copy(color = Yard.inkSoft),
                    )
                }

                GhostButton(
                    label = stringResource(R.string.common_close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DaySlot(
    slot: Int,
    reward: Long,
    isTarget: Boolean,
    claimed: Boolean,
    modifier: Modifier = Modifier,
) {
    val border = when {
        isTarget -> Yard.gold
        claimed -> Yard.mint.copy(alpha = 0.6f)
        else -> Yard.stroke
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isTarget) {
                    Brush.verticalGradient(listOf(Yard.gold.copy(alpha = 0.22f), Yard.shadow))
                } else {
                    Brush.verticalGradient(listOf(Yard.shadow, Yard.shadow))
                },
            )
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "D$slot",
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isTarget) Yard.gold else Yard.inkSoft,
            ),
        )
        Text(
            text = reward.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (claimed) Yard.mint else Color.White,
            ),
        )
        Box(modifier = Modifier.height(1.dp))
    }
}
