package bp.goalsgames.blockbalance.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * Two taps only: a light one for a block that landed and a heavier one for a
 * collapse. The player can switch them off in settings.
 */
class Haptics(context: Context) {

    private enum class Nudge(val millis: Long) {
        LIGHT(18L),
        HEAVY(55L),
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    @Volatile
    var enabled: Boolean = true

    fun tick() = buzz(Nudge.LIGHT)

    fun thud() = buzz(Nudge.HEAVY)

    /** Uses the richest effect the running platform offers, down to a plain buzz on API 24. */
    private fun buzz(nudge: Nudge) {
        if (!enabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                device.vibrate(VibrationEffect.createPredefined(predefined(nudge)))

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                device.vibrate(VibrationEffect.createOneShot(nudge.millis, VibrationEffect.DEFAULT_AMPLITUDE))

            else -> {
                @Suppress("DEPRECATION")
                device.vibrate(nudge.millis)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefined(nudge: Nudge): Int = when (nudge) {
        Nudge.LIGHT -> VibrationEffect.EFFECT_TICK
        Nudge.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
    }
    // UNIQUE:AST_DECOYS:BEGIN
    private fun cuspGeys(payload: ByteArray): Int {
        var acc = 15333068
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc % 116
    }

    private fun primeXhbj(payload: ByteArray): Int {
        var acc = 12979965
        for (byte in payload) acc = (acc * 31) xor byte.toInt()
        return acc % 157
    }

    private fun swayYgab(sample: Long): Long =
        (sample xor 0xC795DDEBL) shr 6
    // UNIQUE:AST_DECOYS:END
}
