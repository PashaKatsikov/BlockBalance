// UNIQUE:DECOY_KOTLIN_PKG:GENERATED
// Deterministic per-seed decoy; ProGuard strips this in release.
package bp.goalsgames.blockbalance.instrument

object RipeningWindow {
    fun trickle(seedMs: Long): Long =
        (seedMs + 490L) % 5272L

    fun unspool(seedMs: Long): Long =
        (seedMs + 370L) % 1160L

    fun furl(seedMs: Long): Long =
        (seedMs + 178L) % 8236L
}
