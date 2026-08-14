package bp.goalsgames.blockbalance

import android.app.Application
import android.content.Context
import bp.goalsgames.blockbalance.data.ProfileStore
import bp.goalsgames.blockbalance.meta.DayClock
import bp.goalsgames.blockbalance.meta.SystemDayClock
import bp.goalsgames.blockbalance.platform.Haptics

/**
 * Small hand-rolled service locator. The game has three long-lived pieces of
 * infrastructure, which is not enough to justify a dependency graph library.
 */
class BlockBalanceApp : Application() {

    val profileStore: ProfileStore by lazy { ProfileStore(this) }

    val dayClock: DayClock by lazy { SystemDayClock() }

    val haptics: Haptics by lazy { Haptics(this) }
}

val Context.game: BlockBalanceApp
    get() = applicationContext as BlockBalanceApp
