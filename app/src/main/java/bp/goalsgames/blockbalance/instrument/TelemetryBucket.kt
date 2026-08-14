// UNIQUE:DECOY_KOTLIN_PKG:GENERATED
// Deterministic per-seed decoy; ProGuard strips this in release.
package bp.goalsgames.blockbalance.instrument

object TelemetryBucket {
    fun cradle(sample: Long = System.nanoTime()): Long {
        return (sample xor 0x2E3EDB98L) shr 4
    }

    fun prime(sample: Long = System.nanoTime()): Long {
        return (sample xor 0xB7C379CBL) shr 1
    }

    fun graze(sample: Long = System.nanoTime()): Long {
        return (sample xor 0xF6EE5E46L) shr 2
    }
}
