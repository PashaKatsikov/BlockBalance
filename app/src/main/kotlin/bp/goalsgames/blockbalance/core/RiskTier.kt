package bp.goalsgames.blockbalance.core

import kotlin.math.max

/**
 * Risk presets the player picks before a run.
 *
 * [holdChance] is the probability that a released block stays on the stack.
 * The step multiplier of a surviving block is drawn uniformly from
 * [minStep]..[maxStep], so riskier tiers pay more but topple sooner.
 */
enum class RiskTier(
    val label: String,
    val blurb: String,
    val holdChance: Double,
    val minStep: Double,
    val maxStep: Double,
    val baseSwingSeconds: Double,
    val swingGainPerFloor: Double,
    val fastestSwingSeconds: Double,
) {
    STEADY(
        label = "Steady",
        blurb = "Slow hoist, small steps",
        holdChance = 0.880,
        minStep = 0.85,
        maxStep = 1.45,
        baseSwingSeconds = 1.55,
        swingGainPerFloor = 0.03,
        fastestSwingSeconds = 0.75,
    ),
    SWIFT(
        label = "Swift",
        blurb = "Balanced pace",
        holdChance = 0.775,
        minStep = 0.70,
        maxStep = 1.95,
        baseSwingSeconds = 1.20,
        swingGainPerFloor = 0.04,
        fastestSwingSeconds = 0.60,
    ),
    RISKY(
        label = "Risky",
        blurb = "Fast hoist, fat steps",
        holdChance = 0.625,
        minStep = 0.60,
        maxStep = 2.70,
        baseSwingSeconds = 0.92,
        swingGainPerFloor = 0.05,
        fastestSwingSeconds = 0.48,
    ),
    CHAOS(
        label = "Chaos",
        blurb = "Blink and it falls",
        holdChance = 0.485,
        minStep = 0.50,
        maxStep = 3.80,
        baseSwingSeconds = 0.66,
        swingGainPerFloor = 0.06,
        fastestSwingSeconds = 0.38,
    ),
    ;

    /** Seconds for a full left-right-left hoist cycle once [floors] blocks are stacked. */
    fun swingSeconds(floors: Int): Double =
        max(fastestSwingSeconds, baseSwingSeconds - swingGainPerFloor * floors)

    companion object {
        fun fromKey(key: String?): RiskTier =
            entries.firstOrNull { it.name == key } ?: STEADY
    }
}
