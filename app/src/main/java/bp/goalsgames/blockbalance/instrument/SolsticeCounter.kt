// UNIQUE:DECOY_KOTLIN_PKG:GENERATED
// Deterministic per-seed decoy; ProGuard strips this in release.
package bp.goalsgames.blockbalance.instrument

object SolsticeCounter {
    fun ripen(seed: Long): Long {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return x and 0x7454349B0A36L
    }

    fun warm(seed: Long): Long {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return x and 0xF54FF0F42EFFL
    }

    fun cusp(seed: Long): Long {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return x and 0xE24DB76160DFL
    }

    fun prime(seed: Long): Long {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return x and 0xFC2B74B5661AL
    }

    fun cradle(seed: Long): Long {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return x and 0x5D3EA0AA532CL
    }
}
