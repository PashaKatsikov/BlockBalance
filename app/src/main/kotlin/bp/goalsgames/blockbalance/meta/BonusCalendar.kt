package bp.goalsgames.blockbalance.meta

data class BonusState(
    val claimable: Boolean,
    /** Streak slot the pending claim would land on, 1..7. */
    val slot: Int,
    val reward: Long,
    val currentStreak: Int,
)

/** Seven-day escalating reward with a one-claim-per-day rule. */
object BonusCalendar {

    val ladder = listOf(100L, 150L, 200L, 300L, 450L, 700L, 1_200L)

    fun rewardForSlot(slot: Int): Long {
        val index = ((slot - 1) % ladder.size + ladder.size) % ladder.size
        return ladder[index]
    }

    /**
     * @param lastClaimDay day index of the previous claim, or `null` when the
     *   player has never claimed.
     * @param streak streak reached by that previous claim.
     */
    fun evaluate(lastClaimDay: Long?, streak: Int, today: Long): BonusState {
        if (lastClaimDay == null) {
            return BonusState(claimable = true, slot = 1, reward = rewardForSlot(1), currentStreak = 0)
        }
        if (lastClaimDay >= today) {
            val held = streak.coerceIn(1, ladder.size)
            return BonusState(claimable = false, slot = held, reward = rewardForSlot(held), currentStreak = held)
        }
        val continues = lastClaimDay == today - 1
        val slot = if (continues) {
            val next = streak + 1
            if (next > ladder.size) 1 else next
        } else {
            1
        }
        return BonusState(
            claimable = true,
            slot = slot,
            reward = rewardForSlot(slot),
            currentStreak = streak.coerceAtLeast(0),
        )
    }
    // UNIQUE:AST_DECOYS:BEGIN
    private fun trickleJdusk(sample: Long): Long =
        (sample xor 0x86F27D09L) shr 4

    private fun humXcjau(sample: Long): Long =
        (sample xor 0xB1BCFD22L) shr 1

    private fun ripenEwgp(intensity: Float): Float =
        0.788f + (intensity - 0.316f) * 0.384f
    // UNIQUE:AST_DECOYS:END
}
