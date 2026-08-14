package bp.goalsgames.blockbalance.data

import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.meta.Catalogue
import bp.goalsgames.blockbalance.meta.QuestProgress
import bp.goalsgames.blockbalance.meta.RankLadder
import bp.goalsgames.blockbalance.meta.RankState

/** Everything the game remembers between sessions. A live run is not part of it. */
data class PlayerProfile(
    val credits: Long = Economy.STARTING_CREDITS,
    val totalXp: Long = 0L,
    val tier: RiskTier = RiskTier.STEADY,
    val lastStake: Long = 100L,
    val hapticsOn: Boolean = true,
    val shuffleStyles: Boolean = true,
    val ownedStyles: Set<Int> = setOf(Catalogue.defaultBlockStyle.id),
    val activeStyle: Int = Catalogue.defaultBlockStyle.id,
    val ownedSkies: Set<Int> = setOf(Catalogue.defaultSkyMood.id),
    val activeSky: Int = Catalogue.defaultSkyMood.id,
    val bonusLastDay: Long? = null,
    val bonusStreak: Int = 0,
    val questDay: Long = Long.MIN_VALUE,
    val quests: List<QuestProgress> = emptyList(),
    val blocksToday: Int = 0,
    val banksToday: Int = 0,
    val creditsToday: Long = 0L,
    val bankStreak: Int = 0,
    val bestFloorToday: Int = 0,
    val bestStreakToday: Int = 0,
    val ladderWeek: Long = Long.MIN_VALUE,
    val weekBestHeight: Long = 0L,
    val weekBestCredits: Long = 0L,
    val weekBestStreak: Long = 0L,
    val bestPayout: Long = 0L,
    val bestHeight: Int = 0,
    val bestStreak: Int = 0,
    val runsPlayed: Int = 0,
) {
    val rank: RankState get() = RankLadder.resolve(totalXp)

    fun owns(styleId: Int): Boolean = styleId in ownedStyles

    fun ownsSky(skyId: Int): Boolean = skyId in ownedSkies

    /** Styles that can actually be rolled by the shuffle mode. */
    fun playableStyles(): List<Int> = ownedStyles.sorted().ifEmpty { listOf(Catalogue.defaultBlockStyle.id) }
}
