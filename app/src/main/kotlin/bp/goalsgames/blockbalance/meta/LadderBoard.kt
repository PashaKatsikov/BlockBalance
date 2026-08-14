package bp.goalsgames.blockbalance.meta

import kotlin.random.Random

enum class LadderMetric {
    HEIGHT,
    CREDITS,
    STREAK,
}

data class LadderRow(
    val place: Int,
    val name: String,
    val score: Long,
    val isPlayer: Boolean,
)

/**
 * Offline weekly board. Rival scores are generated from the week index, so the
 * table is stable for the whole week and reshuffles when the week rolls over.
 * Nothing is uploaded anywhere.
 */
object LadderBoard {

    private val rivals = listOf(
        "Mira", "Kessel", "Boro", "Tanvi", "Odin", "Lupe", "Nyx",
        "Fenn", "Sable", "Rook", "Zephyr", "Ines", "Cato", "Wren",
    )

    private fun seedFor(week: Long, metric: LadderMetric): Long =
        week * 0x5DEECE66DL + metric.ordinal * 0x9E3779B9L + 0x2545F491L

    private fun rivalScore(random: Random, metric: LadderMetric, index: Int): Long {
        // Leading rivals sit higher; the tail stays beatable for new players.
        val slope = 1.0 - index / rivals.size.toDouble()
        val noise = 0.72 + random.nextDouble() * 0.56
        return when (metric) {
            LadderMetric.HEIGHT -> (4 + slope * 26 * noise).toLong().coerceAtLeast(1L)
            LadderMetric.CREDITS -> (900 + slope * 58_000 * noise).toLong().coerceAtLeast(120L)
            LadderMetric.STREAK -> (1 + slope * 11 * noise).toLong().coerceAtLeast(1L)
        }
    }

    fun table(week: Long, metric: LadderMetric, playerScore: Long, playerName: String = "You"): List<LadderRow> {
        val random = Random(seedFor(week, metric))
        val rows = rivals.mapIndexed { index, name ->
            LadderRow(place = 0, name = name, score = rivalScore(random, metric, index), isPlayer = false)
        } + LadderRow(place = 0, name = playerName, score = playerScore, isPlayer = true)

        return rows
            .sortedWith(compareByDescending<LadderRow> { it.score }.thenBy { it.name })
            .mapIndexed { index, row -> row.copy(place = index + 1) }
    }

    fun daysLeft(day: Long): Int = DayClock.daysUntilWeekRollover(day)
}
