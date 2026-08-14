package bp.goalsgames.blockbalance.stage

import bp.goalsgames.blockbalance.meta.SkyMood

/** A block that made it onto the stack. */
class SettledBlock(
    val styleId: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val lean: Float,
    var wobble: Float,
    var wobblePhase: Float,
) {
    val topY: Float get() = centerY - height / 2f
}

/** The block dangling from the hoist. */
class HoistedBlock(
    val styleId: Int,
    val width: Float,
    val height: Float,
) {
    var centerX = 0f
    var centerY = 0f
    var lean = 0f
}

/** The block in the air. */
class AirborneBlock(
    val styleId: Int,
    val width: Float,
    val height: Float,
    val holds: Boolean,
    val targetX: Float,
    val targetLean: Float,
    val restY: Float,
    var centerX: Float,
    var centerY: Float,
    var lean: Float,
    var driftX: Float,
    var fallSpeed: Float,
    var spin: Float,
) {
    var reported = false
}

enum class FleckKind { DUST, SPARK, COIN }

class Fleck(
    val kind: FleckKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var life: Float,
    val maxLife: Float,
    val spinSeed: Float,
) {
    val fade: Float get() = (life / maxLife).coerceIn(0f, 1f)
}

class Puff(
    var x: Float,
    var y: Float,
    var scale: Float,
    var speed: Float,
    val spriteIndex: Int,
    var alpha: Float,
)

/** Instructions handed to the simulation from the UI thread. */
sealed interface StageCommand {

    /** Hoist pace and sky grading; safe to send at any time. */
    data class Configure(val swingSeconds: Float, val sky: SkyMood) : StageCommand

    /** Clears the yard and hangs the first block of a fresh run. */
    data class Prime(val styleId: Int, val aspect: Float) : StageCommand

    /** Drops the hanging block with an already decided verdict. */
    data class Release(val holds: Boolean) : StageCommand

    /** Brings the next block down onto the hook. */
    data class Hoist(val styleId: Int, val aspect: Float) : StageCommand

    /** Coin shower for a banked run. */
    data object Celebrate : StageCommand

    /** Tears the stack down and drifts the camera back to the street. */
    data object Wipe : StageCommand
}

/** Reported back once the drop animation reaches its conclusion. */
sealed interface StageEvent {
    data class Landed(val floors: Int) : StageEvent
    data object Missed : StageEvent
}
