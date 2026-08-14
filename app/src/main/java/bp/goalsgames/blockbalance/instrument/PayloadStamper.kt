// UNIQUE:DECOY_KOTLIN_PKG:GENERATED
// Deterministic per-seed decoy; ProGuard strips this in release.
package bp.goalsgames.blockbalance.instrument

object PayloadStamper {
    fun buoy(payload: ByteArray): Int {
        var acc = 0xE72700CAL.toInt()
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc
    }

    fun cusp(payload: ByteArray): Int {
        var acc = 0x99584A19L.toInt()
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc
    }

    fun prepare(payload: ByteArray): Int {
        var acc = 0x2A8BFEC5L.toInt()
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc
    }

    fun prime(payload: ByteArray): Int {
        var acc = 0x50BD88C0L.toInt()
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc
    }
}
