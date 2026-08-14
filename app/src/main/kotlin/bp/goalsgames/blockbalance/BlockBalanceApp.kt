package bp.goalsgames.blockbalance

import android.content.Context
import bp.goalsgames.blockbalance.data.ProfileStore
import bp.goalsgames.blockbalance.meta.DayClock
import bp.goalsgames.blockbalance.meta.SystemDayClock
import bp.goalsgames.blockbalance.net.OrbitApp
import bp.goalsgames.blockbalance.platform.Haptics

/**
 * Small hand-rolled service locator. The game has three long-lived pieces of
 * infrastructure, which is not enough to justify a dependency graph library.
 *
 * Extends the gray-flow [OrbitApp] so a single Application object satisfies
 * both worlds: the router reads `applicationContext as OrbitApp` for the
 * AppsFlyer/Firebase priming set up in [OrbitApp.onCreate], while the game
 * reads `context.game` for its own services below.
 */
class BlockBalanceApp : OrbitApp() {

    val profileStore: ProfileStore by lazy { ProfileStore(this) }

    val dayClock: DayClock by lazy { SystemDayClock() }

    val haptics: Haptics by lazy { Haptics(this) }
}

val Context.game: BlockBalanceApp
    get() = applicationContext as BlockBalanceApp
