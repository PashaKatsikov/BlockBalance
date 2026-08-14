package bp.goalsgames.blockbalance.core

/**
 * Owns the rules of a single run. Nothing here touches Android or the renderer:
 * the caller starts a run, releases blocks, settles them once the drop animation
 * reports back, and banks whenever the player decides to stop.
 */
class RoundMachine(private val chance: Chance) {

    var snapshot: RoundSnapshot = RoundSnapshot()
        private set

    /** Tier is locked while a stake is live. */
    fun selectTier(tier: RiskTier): Boolean {
        if (snapshot.phase != RoundPhase.READY) return false
        snapshot = snapshot.copy(tier = tier)
        return true
    }

    /**
     * Takes the stake and puts a block on the hoist. The caller is expected to
     * follow up with [release] straight away so the run opens with a drop.
     */
    fun begin(stake: Long): Boolean {
        if (snapshot.phase == RoundPhase.SWINGING || snapshot.phase == RoundPhase.RELEASING) return false
        if (stake < Economy.MIN_STAKE) return false
        snapshot = RoundSnapshot(
            phase = RoundPhase.SWINGING,
            tier = snapshot.tier,
            stake = stake,
        )
        return true
    }

    /** Rolls the verdict for the block currently on the hoist. */
    fun release(): StepVerdict? {
        if (!snapshot.canRelease) return null
        val tier = snapshot.tier
        val holds = chance.unit() < tier.holdChance
        val step = if (holds) Economy.trim(chance.between(tier.minStep, tier.maxStep)) else 0.0
        val verdict = StepVerdict(holds = holds, step = step)
        snapshot = snapshot.copy(phase = RoundPhase.RELEASING, verdict = verdict)
        return verdict
    }

    /** Applies the pending verdict once the drop animation has finished. */
    fun settle(): SettleResult? {
        val verdict = snapshot.verdict ?: return null
        if (snapshot.phase != RoundPhase.RELEASING) return null
        return if (verdict.holds) {
            val combo = Economy.combine(snapshot.combo, verdict.step)
            snapshot = snapshot.copy(
                phase = RoundPhase.SWINGING,
                floors = snapshot.floors + 1,
                combo = combo,
                steps = snapshot.steps + verdict.step,
                verdict = null,
            )
            SettleResult.Placed(
                floors = snapshot.floors,
                step = verdict.step,
                combo = combo,
                potential = snapshot.potential,
            )
        } else {
            snapshot = snapshot.copy(phase = RoundPhase.TOPPLED, verdict = null)
            SettleResult.Toppled(floors = snapshot.floors, lostStake = snapshot.stake)
        }
    }

    /** Locks in the current potential payout. */
    fun bank(): BankResult? {
        if (!snapshot.canBank) return null
        val payout = snapshot.potential
        snapshot = snapshot.copy(phase = RoundPhase.BANKED, banked = payout)
        return BankResult(
            payout = payout,
            floors = snapshot.floors,
            combo = snapshot.combo,
            stake = snapshot.stake,
        )
    }

    /** Returns to the idle state, keeping the selected tier. */
    fun clear() {
        snapshot = RoundSnapshot(tier = snapshot.tier)
    }

    /** Seconds per hoist cycle for the current stack height. */
    fun swingSeconds(): Double = snapshot.tier.swingSeconds(snapshot.floors)
}
