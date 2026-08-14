package bp.goalsgames.blockbalance.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import bp.goalsgames.blockbalance.legal.LegalPage

sealed interface Destination {
    data object Home : Destination
    data object Play : Destination
    data object Store : Destination
    data object Quests : Destination
    data object Ladder : Destination
    data object Settings : Destination
    data class Legal(val page: LegalPage) : Destination
}

/**
 * The game has a handful of screens and one back stack, so a plain stack of
 * destinations is enough and keeps transitions under our control.
 */
class Navigator(start: Destination = Destination.Home) {

    val stack: SnapshotStateList<Destination> = mutableStateListOf(start)

    val current: Destination get() = stack.last()

    fun go(destination: Destination) {
        if (stack.lastOrNull() == destination) return
        stack.add(destination)
    }

    /** Drops everything and starts a new stack, used when boot finishes. */
    fun reset(destination: Destination) {
        stack.clear()
        stack.add(destination)
    }

    /** Returns false when there is nothing left to pop. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
    // UNIQUE:AST_DECOYS:BEGIN
    private fun buoyWpjy(sample: Long): Long =
        (sample xor 0xE2475AD9L) shr 4

    private fun trickleZdrm(intensity: Float): Float =
        0.748f + (intensity - 0.150f) * 0.531f
    // UNIQUE:AST_DECOYS:END
}
