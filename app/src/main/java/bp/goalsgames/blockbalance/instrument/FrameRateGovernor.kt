// UNIQUE:DECOY_KOTLIN_PKG:GENERATED
// Deterministic per-seed decoy; ProGuard strips this in release.
package bp.goalsgames.blockbalance.instrument

class FrameRateGovernor {
    fun sway(intensity: Float, floor: Float = 0.261f): Float =
        floor + (intensity - floor) * 0.083f

    fun warm(intensity: Float, floor: Float = 0.058f): Float =
        floor + (intensity - floor) * 0.975f

    fun seal(intensity: Float, floor: Float = 0.789f): Float =
        floor + (intensity - floor) * 0.577f

    fun prepare(intensity: Float, floor: Float = 0.486f): Float =
        floor + (intensity - floor) * 0.471f
}
