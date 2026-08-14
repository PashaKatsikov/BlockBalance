package bp.goalsgames.blockbalance.ui.quests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.meta.QuestCard
import bp.goalsgames.blockbalance.ui.ShellViewModel
import bp.goalsgames.blockbalance.ui.components.MeterBar
import bp.goalsgames.blockbalance.ui.components.ScreenHeader
import bp.goalsgames.blockbalance.ui.components.SolidButton
import bp.goalsgames.blockbalance.ui.components.ValueChip
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.formatCredits
import bp.goalsgames.blockbalance.ui.groupDigits
import bp.goalsgames.blockbalance.ui.theme.Yard

@Composable
fun QuestsScreen(
    profile: PlayerProfile,
    shell: ShellViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { shell.refreshCalendar() }

    val cards = shell.questCards(profile)

    Column(modifier = modifier.background(Yard.night)) {
        Box(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.quests_title).uppercase(),
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Fresh quests arrive every day at midnight.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(cards, key = { it.template.code }) { card ->
                QuestRow(card = card, onClaim = { shell.claimQuest(card.template.code) })
            }
        }
    }
}

@Composable
private fun QuestRow(card: QuestCard, onClaim: () -> Unit) {
    YardPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.template.title(),
                    style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${groupDigits(card.template.reward)}",
                    style = MaterialTheme.typography.titleMedium.copy(color = Yard.gold),
                )
            }

            MeterBar(ratio = card.ratio, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${card.progress.coerceAtMost(card.template.target)} / ${card.template.target}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                    modifier = Modifier.weight(1f),
                )
                when {
                    card.claimed -> Text(
                        text = "COLLECTED",
                        style = MaterialTheme.typography.labelMedium.copy(color = Yard.mint),
                    )

                    card.claimable -> SolidButton(
                        label = stringResource(R.string.quests_claim),
                        onClick = onClaim,
                        height = 42.dp,
                    )

                    else -> Text(
                        text = "IN PROGRESS",
                        style = MaterialTheme.typography.labelMedium.copy(color = Yard.stroke),
                    )
                }
            }
        }
    }
}
