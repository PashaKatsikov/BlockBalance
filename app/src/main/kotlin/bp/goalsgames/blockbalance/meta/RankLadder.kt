package bp.goalsgames.blockbalance.meta

data class RankState(
    val rank: Int,
    val xpInto: Long,
    val xpForNext: Long,
) {
    val isMaxed: Boolean get() = xpForNext <= 0L
    val progress: Float
        get() = if (isMaxed) 1f else (xpInto.toFloat() / xpForNext.toFloat()).coerceIn(0f, 1f)
}

/** Experience curve, rank rewards and the ranks that hand out cosmetics. */
object RankLadder {

    const val MAX_RANK = 60

    /** Experience needed to move from [rank] to `rank + 1`. */
    fun costFor(rank: Int): Long = 60L + (rank - 1L) * 45L

    /** Credits handed out on reaching [rank]. */
    fun rewardFor(rank: Int): Long = 100L + rank * 50L

    fun resolve(totalXp: Long): RankState {
        var rank = 1
        var left = totalXp.coerceAtLeast(0L)
        while (rank < MAX_RANK) {
            val cost = costFor(rank)
            if (left < cost) return RankState(rank, left, cost)
            left -= cost
            rank++
        }
        return RankState(MAX_RANK, 0L, 0L)
    }

    fun xpToReach(rank: Int): Long {
        var total = 0L
        for (step in 1 until rank.coerceIn(1, MAX_RANK)) total += costFor(step)
        return total
    }
}
