package bp.goalsgames.blockbalance.core

enum class RoundPhase {
    /** No stake is live: tier and stake can be changed. */
    READY,

    /** A block hangs from the hoist and the player may release or bank. */
    SWINGING,

    /** A block is in the air; the verdict is already decided but not shown yet. */
    RELEASING,

    /** The run was banked and the payout is credited. */
    BANKED,

    /** The block missed and the stake is gone. */
    TOPPLED,
}

/**
 * Verdict for a released block, rolled the moment the player releases it.
 * The visual drop only plays this back, which is why hoist timing cannot be
 * "aimed" by the player.
 */
data class StepVerdict(
    val holds: Boolean,
    val step: Double,
)

data class RoundSnapshot(
    val phase: RoundPhase = RoundPhase.READY,
    val tier: RiskTier = RiskTier.STEADY,
    val stake: Long = 0L,
    val floors: Int = 0,
    val combo: Double = 1.0,
    val steps: List<Double> = emptyList(),
    val verdict: StepVerdict? = null,
    val banked: Long = 0L,
) {
    val potential: Long get() = Economy.payout(stake, combo)
    val canRelease: Boolean get() = phase == RoundPhase.SWINGING
    val canBank: Boolean get() = phase == RoundPhase.SWINGING && floors > 0
    val isLive: Boolean get() = phase == RoundPhase.SWINGING || phase == RoundPhase.RELEASING
}

sealed interface SettleResult {
    data class Placed(
        val floors: Int,
        val step: Double,
        val combo: Double,
        val potential: Long,
    ) : SettleResult

    data class Toppled(
        val floors: Int,
        val lostStake: Long,
    ) : SettleResult
}

data class BankResult(
    val payout: Long,
    val floors: Int,
    val combo: Double,
    val stake: Long,
)
