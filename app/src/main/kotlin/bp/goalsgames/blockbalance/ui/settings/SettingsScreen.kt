package bp.goalsgames.blockbalance.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import bp.goalsgames.blockbalance.BuildConfig
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.legal.LegalPage
import bp.goalsgames.blockbalance.ui.ShellViewModel
import bp.goalsgames.blockbalance.ui.components.GhostButton
import bp.goalsgames.blockbalance.ui.components.ScreenHeader
import bp.goalsgames.blockbalance.ui.components.SolidButton
import bp.goalsgames.blockbalance.ui.components.YardPanel
import bp.goalsgames.blockbalance.ui.groupDigits
import bp.goalsgames.blockbalance.ui.theme.Yard

@Composable
fun SettingsScreen(
    profile: PlayerProfile,
    shell: ShellViewModel,
    onBack: () -> Unit,
    onLegal: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by remember { mutableStateOf(false) }

    Column(modifier = modifier.background(Yard.night)) {
        Box(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(title = stringResource(R.string.settings_title).uppercase(), onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TierCard(selected = profile.tier, onTier = shell::setTier) }
            item {
                ToggleRow(
                    title = stringResource(R.string.settings_haptics),
                    caption = "Short buzz on a landing, longer on a collapse",
                    checked = profile.hapticsOn,
                    onChange = shell::setHaptics,
                )
            }
            item {
                ToggleRow(
                    title = stringResource(R.string.settings_shuffle),
                    caption = "Random owned style for every block",
                    checked = profile.shuffleStyles,
                    onChange = shell::setShuffle,
                )
            }
            item { StatsCard(profile) }
            item {
                YardPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GhostButton(
                            label = stringResource(R.string.legal_privacy_title).uppercase(),
                            onClick = { onLegal(LegalPage.PRIVACY) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GhostButton(
                            label = stringResource(R.string.legal_support_title).uppercase(),
                            onClick = { onLegal(LegalPage.SUPPORT) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                YardPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_reset),
                            style = MaterialTheme.typography.titleMedium.copy(color = Yard.ember),
                        )
                        Text(
                            text = "Clears credits, ranks, styles and quests on this device.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                        )
                        SolidButton(
                            label = "RESET",
                            onClick = { confirmReset = true },
                            modifier = Modifier.fillMaxWidth(),
                            tint = Yard.ember,
                            height = 48.dp,
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Block Balance v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Yard.stroke),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            YardPanel(modifier = Modifier.fillMaxWidth(), corner = 22.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Reset everything?",
                        style = MaterialTheme.typography.titleLarge.copy(color = Yard.ink),
                    )
                    Text(
                        text = "Progress on this device cannot be recovered afterwards.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Yard.inkSoft),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            label = "CANCEL",
                            onClick = { confirmReset = false },
                            modifier = Modifier.weight(1f),
                        )
                        SolidButton(
                            label = "RESET",
                            onClick = {
                                shell.resetProgress()
                                confirmReset = false
                            },
                            modifier = Modifier.weight(1f),
                            tint = Yard.ember,
                            height = 44.dp,
                        )
                    }
                }
            }
        }
    }
}

/** Hoist pace and payout spread; the yard picks it up on the next run. */
@Composable
private fun TierCard(selected: RiskTier, onTier: (RiskTier) -> Unit) {
    YardPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_difficulty),
                style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
            )
            RiskTier.entries.forEach { tier ->
                val active = tier == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Yard.gold.copy(alpha = 0.16f) else Yard.shadow)
                        .clickable { onTier(tier) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tier.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (active) Yard.gold else Yard.ink,
                            ),
                        )
                        Text(
                            text = tier.blurb,
                            style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                        )
                    }
                    if (active) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.titleLarge.copy(color = Yard.gold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    caption: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    YardPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(color = Yard.ink),
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall.copy(color = Yard.inkSoft),
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun StatsCard(profile: PlayerProfile) {
    YardPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "PERSONAL BEST",
                style = MaterialTheme.typography.labelLarge.copy(color = Yard.gold),
            )
            StatLine("Highest stack", "${profile.bestHeight} floors")
            StatLine("Biggest payout", groupDigits(profile.bestPayout))
            StatLine("Longest bank streak", profile.bestStreak.toString())
            StatLine("Runs played", profile.runsPlayed.toString())
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = Yard.inkSoft),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(color = Yard.ink),
        )
    }
}
