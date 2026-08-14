package bp.goalsgames.blockbalance.core

import kotlin.random.Random

/** Randomness seam so round outcomes can be pinned down in tests. */
interface Chance {
    /** Uniform value in `[0, 1)`. */
    fun unit(): Double

    /** Uniform value in `[from, until)`. */
    fun between(from: Double, until: Double): Double

    /** Uniform integer in `[0, until)`. */
    fun below(until: Int): Int
}

class SeededChance(private val random: Random = Random.Default) : Chance {
    override fun unit(): Double = random.nextDouble()

    override fun between(from: Double, until: Double): Double =
        if (until <= from) from else random.nextDouble(from, until)

    override fun below(until: Int): Int = if (until <= 0) 0 else random.nextInt(until)
}

/** Always answers with the same value; handy for deterministic checks. */
class FixedChance(private val value: Double) : Chance {
    override fun unit(): Double = value

    override fun between(from: Double, until: Double): Double = from + (until - from) * value

    override fun below(until: Int): Int = if (until <= 0) 0 else (value * until).toInt().coerceAtMost(until - 1)
}
