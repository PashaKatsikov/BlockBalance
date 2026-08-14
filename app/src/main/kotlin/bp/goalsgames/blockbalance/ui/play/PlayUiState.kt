package bp.goalsgames.blockbalance.ui.play

import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.core.RoundPhase
import bp.goalsgames.blockbalance.data.RankGain
import bp.goalsgames.blockbalance.meta.RankState

enum class HeadlineTone { GAIN, LOSS }

/** The big multiplier that pops in the middle of the yard. */
data class Headline(
    val id: Long,
    val text: String,
    val tone: HeadlineTone,
)

/** Payload of the banked-run panel. */
data class BankPanel(
    val payout: Long,
    val floors: Int,
    val combo: Double,
)

data class PlayUiState(
    val credits: Long = Economy.STARTING_CREDITS,
    val rank: RankState = RankState(1, 0, 60),
    val phase: RoundPhase = RoundPhase.READY,
    val tier: RiskTier = RiskTier.STEADY,
    val stake: Long = 100L,
    val floors: Int = 0,
    val combo: Double = 1.0,
    val potential: Long = 0L,
    val recentSteps: List<Double> = emptyList(),
    val headline: Headline? = null,
    val showLowFunds: Boolean = false,
    val bankPanel: BankPanel? = null,
    val rankUp: RankGain? = null,
) {
    val tierEditable: Boolean get() = phase == RoundPhase.READY
    val stakeEditable: Boolean get() = phase == RoundPhase.READY
    val canRelease: Boolean get() = phase == RoundPhase.READY || phase == RoundPhase.SWINGING
    val canBank: Boolean get() = phase == RoundPhase.SWINGING && floors > 0
    val controlsVisible: Boolean get() = phase != RoundPhase.BANKED && phase != RoundPhase.TOPPLED
    val runLive: Boolean get() = phase == RoundPhase.SWINGING || phase == RoundPhase.RELEASING
}
