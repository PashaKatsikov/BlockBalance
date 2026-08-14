package bp.goalsgames.blockbalance.ui.ladder

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.meta.LadderMetric
import bp.goalsgames.blockbalance.meta.LadderRow
import bp.goalsgames.blockbalance.ui.ShellViewModel
import bp.goalsgames.blockbalance.ui.components.ScreenHeader
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.groupDigits
import bp.goalsgames.blockbalance.ui.theme.Yard

@Composable
fun LadderScreen(
    profile: PlayerProfile,
    shell: ShellViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var metric by remember { mutableStateOf(LadderMetric.HEIGHT) }

    LaunchedEffect(Unit) { shell.refreshCalendar() }

    val rows = shell.ladderRows(profile, metric)
    val daysLeft = shell.ladderDaysLeft()

    Column(modifier = modifier.background(Yard.night)) {
        Box(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.ladder_title).uppercase(),
                onBack = onBack,
                trailing = {
                    Text(
                        text = "${daysLeft}d left",
                        style = MaterialTheme.typography.labelMedium.copy(color = Yard.inkSoft),
                    )
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LadderMetric.entries.forEach { entry ->
                val active = entry == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) {
                                Brush.verticalGradient(listOf(Yard.gold, Yard.goldDeep))
                            } else {
                                Brush.verticalGradient(listOf(Yard.panel, Yard.panel))
                            },
                        )
                        .border(1.dp, if (active) Color.Transparent else Yard.stroke, RoundedCornerShape(12.dp))
                        .clickable { metric = entry }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(labelOf(entry)).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (active) Color(0xFF3B2400) else Yard.inkSoft,
                        ),
                    )
                }
            }
        }

        Text(
            text = "Rivals are generated on this device for the current week.",
            style = MaterialTheme.typography.bodySmall.copy(color = Yard.stroke),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { "${it.place}-${it.name}" }) { row ->
                LadderRowView(row = row)
            }
        }
    }
}

@StringRes
private fun labelOf(metric: LadderMetric): Int = when (metric) {
    LadderMetric.HEIGHT -> R.string.ladder_height
    LadderMetric.CREDITS -> R.string.ladder_credits
    LadderMetric.STREAK -> R.string.ladder_streak
}

@Composable
private fun LadderRowView(row: LadderRow) {
    val accent = if (row.isPlayer) Yard.gold else Yard.ink
    YardPanel(modifier = Modifier.fillMaxWidth(), corner = 14.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.place.toString(),
                style = MaterialTheme.typography.titleMedium.copy(color = accent),
                modifier = Modifier.width(34.dp),
            )
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium.copy(color = accent),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = groupDigits(row.score),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (row.isPlayer) Yard.gold else Yard.inkSoft,
                ),
            )
        }
    }
}
