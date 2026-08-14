package bp.goalsgames.blockbalance.core

import kotlin.math.floor
import kotlin.math.roundToLong

/** Stake limits, payout math and experience awards. */
object Economy {

    const val CURRENCY_LABEL = "CR"
    const val STARTING_CREDITS = 1_000L

    const val MIN_STAKE = 10L
    const val MAX_STAKE = 100_000L

    /**
     * How much one press of the minus or plus key moves the stake. The step grows
     * with the stake so the bar walks the same ladder a player would expect.
     */
    fun stepFor(stake: Long): Long = when {
        stake < 100L -> 10L
        stake < 500L -> 50L
        stake < 2_000L -> 100L
        stake < 10_000L -> 500L
        else -> 1_000L
    }

    const val XP_PER_FLOOR = 6L
    const val XP_PER_BANK = 14L

    /** Multipliers are kept at two decimals so the shown value is the value paid. */
    fun trim(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    fun combine(combo: Double, step: Double): Double = trim(combo * step)

    fun payout(stake: Long, combo: Double): Long = floor(stake * combo).toLong()

    /**
     * Keeps a stake inside the affordable window. A player who cannot even cover
     * [MIN_STAKE] still sees [MIN_STAKE] so the bar never shows a nonsense number.
     */
    fun clampStake(stake: Long, balance: Long): Long {
        val ceiling = minOf(MAX_STAKE, maxOf(balance, MIN_STAKE))
        return stake.coerceIn(MIN_STAKE, ceiling)
    }

    fun canAfford(stake: Long, balance: Long): Boolean = balance >= stake && stake >= MIN_STAKE
}
