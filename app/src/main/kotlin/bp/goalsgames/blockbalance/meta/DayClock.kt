package bp.goalsgames.blockbalance.meta

import java.util.TimeZone

/**
 * Calendar helpers expressed as plain integer arithmetic so daily and weekly
 * resets behave identically in tests and on device.
 *
 * Day 0 of the epoch is a Thursday, so shifting by three days lines the integer
 * division up with Monday-based ISO weeks.
 */
interface DayClock {
    /** Local calendar day index. */
    fun today(): Long

    /** Monday-aligned week index; increments at local midnight between Sunday and Monday. */
    fun week(): Long = weekOf(today())

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val EPOCH_DAY_SHIFT = 3L

        fun weekOf(day: Long): Long = (day + EPOCH_DAY_SHIFT).floorDiv(7L)

        /** Whole days until the next Monday; a Monday reports a full week. */
        fun daysUntilWeekRollover(day: Long): Int {
            val intoWeek = ((day + EPOCH_DAY_SHIFT) % 7L + 7L) % 7L
            return (7L - intoWeek).toInt()
        }

        fun dayOf(epochMillis: Long, zoneOffsetMillis: Int): Long =
            (epochMillis + zoneOffsetMillis).floorDiv(MILLIS_PER_DAY)
    }
}

/** Reads the device clock and the current local offset. */
class SystemDayClock(
    private val millis: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault,
) : DayClock {
    override fun today(): Long {
        val now = millis()
        return DayClock.dayOf(now, zone().getOffset(now))
    }
}

/** Fixed clock for tests and previews. */
class FrozenDayClock(private var day: Long) : DayClock {
    override fun today(): Long = day

    fun advance(days: Long) {
        day += days
    }
}
