package bp.goalsgames.blockbalance.meta

import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class QuestKind {
    /** Blocks that stayed on the stack today. */
    BLOCKS_PLACED,

    /** Runs banked today. */
    RUNS_BANKED,

    /** Runs banked back to back without a topple. */
    BANK_STREAK,

    /** Highest floor reached inside one run today. */
    FLOOR_REACHED,

    /** Credits banked today. */
    CREDITS_BANKED,
}

data class QuestTemplate(
    val code: String,
    val kind: QuestKind,
    val target: Int,
    val reward: Long,
) {
    fun title(): String = when (kind) {
        QuestKind.BLOCKS_PLACED -> "Land $target blocks today"
        QuestKind.RUNS_BANKED -> "Bank $target runs today"
        QuestKind.BANK_STREAK -> "Bank $target runs in a row"
        QuestKind.FLOOR_REACHED -> "Reach floor $target in one run"
        QuestKind.CREDITS_BANKED -> "Bank $target credits today"
    }
}

/**
 * Only the claim flag is stored. Progress itself is derived from the day
 * counters on the profile, which keeps the two from drifting apart.
 */
@Serializable
data class QuestProgress(
    val code: String,
    val claimed: Boolean = false,
)

data class QuestCard(
    val template: QuestTemplate,
    val progress: Int,
    val claimed: Boolean,
) {
    val complete: Boolean get() = progress >= template.target
    val claimable: Boolean get() = complete && !claimed
    val ratio: Float get() = (progress.toFloat() / template.target.toFloat()).coerceIn(0f, 1f)
}

/** Picks three quests per calendar day from a fixed pool, deterministically. */
object QuestBoard {

    const val DAILY_COUNT = 3

    val pool = listOf(
        QuestTemplate("blocks_12", QuestKind.BLOCKS_PLACED, 12, 200L),
        QuestTemplate("blocks_25", QuestKind.BLOCKS_PLACED, 25, 300L),
        QuestTemplate("blocks_40", QuestKind.BLOCKS_PLACED, 40, 450L),
        QuestTemplate("banked_3", QuestKind.RUNS_BANKED, 3, 220L),
        QuestTemplate("banked_6", QuestKind.RUNS_BANKED, 6, 320L),
        QuestTemplate("streak_2", QuestKind.BANK_STREAK, 2, 240L),
        QuestTemplate("streak_4", QuestKind.BANK_STREAK, 4, 400L),
        QuestTemplate("floor_5", QuestKind.FLOOR_REACHED, 5, 210L),
        QuestTemplate("floor_8", QuestKind.FLOOR_REACHED, 8, 330L),
        QuestTemplate("floor_12", QuestKind.FLOOR_REACHED, 12, 450L),
        QuestTemplate("credits_1500", QuestKind.CREDITS_BANKED, 1_500, 250L),
        QuestTemplate("credits_4000", QuestKind.CREDITS_BANKED, 4_000, 420L),
    )

    fun template(code: String): QuestTemplate? = pool.firstOrNull { it.code == code }

    /** Same three quests for everyone on a given day, different every day. */
    fun picksFor(day: Long): List<QuestTemplate> {
        val random = Random(day * 0x9E3779B9L)
        val bag = pool.toMutableList()
        val picks = mutableListOf<QuestTemplate>()
        val skipped = mutableListOf<QuestTemplate>()
        val wanted = minOf(DAILY_COUNT, bag.size)
        while (picks.size < wanted && bag.isNotEmpty()) {
            val candidate = bag.removeAt(random.nextInt(bag.size))
            // One quest per kind keeps the daily set varied.
            if (picks.none { it.kind == candidate.kind }) picks += candidate else skipped += candidate
        }
        var index = 0
        while (picks.size < wanted && index < skipped.size) {
            picks += skipped[index]
            index++
        }
        return picks
    }

    fun blankProgress(day: Long): List<QuestProgress> = picksFor(day).map { QuestProgress(it.code) }

    fun cards(entries: List<QuestProgress>, progressOf: (QuestTemplate) -> Int): List<QuestCard> =
        entries.mapNotNull { entry ->
            template(entry.code)?.let { QuestCard(it, progressOf(it), entry.claimed) }
        }
}
