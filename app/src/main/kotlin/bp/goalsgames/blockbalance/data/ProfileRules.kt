package bp.goalsgames.blockbalance.data

import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.meta.BonusCalendar
import bp.goalsgames.blockbalance.meta.Catalogue
import bp.goalsgames.blockbalance.meta.LadderMetric
import bp.goalsgames.blockbalance.meta.QuestBoard
import bp.goalsgames.blockbalance.meta.QuestCard
import bp.goalsgames.blockbalance.meta.QuestKind
import bp.goalsgames.blockbalance.meta.QuestTemplate
import bp.goalsgames.blockbalance.meta.RankLadder

/** Ranks crossed by an experience award, with the credits they paid out. */
data class RankGain(
    val fromRank: Int,
    val toRank: Int,
    val credits: Long,
    val unlockedStyles: Set<Int>,
) {
    val gainedRank: Boolean get() = toRank > fromRank
}

data class ProfileChange(
    val profile: PlayerProfile,
    val rankGain: RankGain? = null,
    val questRewards: Long = 0L,
)

/**
 * Pure transitions over [PlayerProfile]. Keeping them free of Android lets the
 * whole meta layer be checked with plain unit tests.
 */
object ProfileRules {

    /** Clears day and week scoped counters when the calendar moved on. */
    fun rollOver(profile: PlayerProfile, day: Long, week: Long): PlayerProfile {
        var next = profile
        if (next.questDay != day) {
            next = next.copy(
                questDay = day,
                quests = QuestBoard.blankProgress(day),
                blocksToday = 0,
                banksToday = 0,
                creditsToday = 0L,
                bestFloorToday = 0,
                bestStreakToday = 0,
            )
        }
        if (next.ladderWeek != week) {
            next = next.copy(
                ladderWeek = week,
                weekBestHeight = 0L,
                weekBestCredits = 0L,
                weekBestStreak = 0L,
            )
        }
        return next
    }

    fun questProgress(profile: PlayerProfile, template: QuestTemplate): Int = when (template.kind) {
        QuestKind.BLOCKS_PLACED -> profile.blocksToday
        QuestKind.RUNS_BANKED -> profile.banksToday
        QuestKind.BANK_STREAK -> profile.bestStreakToday
        QuestKind.FLOOR_REACHED -> profile.bestFloorToday
        QuestKind.CREDITS_BANKED -> profile.creditsToday.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun questCards(profile: PlayerProfile): List<QuestCard> =
        QuestBoard.cards(profile.quests) { questProgress(profile, it) }

    fun claimableQuests(profile: PlayerProfile): Int = questCards(profile).count { it.claimable }

    /**
     * Adds experience and pays out every rank crossed on the way, including the
     * cosmetics that come with those ranks.
     */
    fun awardXp(profile: PlayerProfile, xp: Long): ProfileChange {
        if (xp <= 0L) return ProfileChange(profile)
        val before = profile.rank.rank
        val withXp = profile.copy(totalXp = profile.totalXp + xp)
        val after = withXp.rank.rank
        if (after <= before) return ProfileChange(withXp)

        var credits = 0L
        for (rank in (before + 1)..after) credits += RankLadder.rewardFor(rank)
        val gifted = Catalogue.giftedStyles(after)
        val unlocked = gifted - withXp.ownedStyles

        val next = withXp.copy(
            credits = withXp.credits + credits,
            ownedStyles = withXp.ownedStyles + unlocked,
        )
        return ProfileChange(
            profile = next,
            rankGain = RankGain(before, after, credits, unlocked),
        )
    }

    fun spend(profile: PlayerProfile, amount: Long): PlayerProfile =
        profile.copy(credits = (profile.credits - amount).coerceAtLeast(0L))

    fun onStakePlaced(profile: PlayerProfile, stake: Long): PlayerProfile =
        spend(profile, stake).copy(lastStake = stake)

    /** A block stayed on the stack. */
    fun onBlockPlaced(profile: PlayerProfile, floors: Int): ProfileChange {
        val counted = profile.copy(
            blocksToday = profile.blocksToday + 1,
            bestFloorToday = maxOf(profile.bestFloorToday, floors),
            bestHeight = maxOf(profile.bestHeight, floors),
            weekBestHeight = maxOf(profile.weekBestHeight, floors.toLong()),
        )
        return awardXp(counted, Economy.XP_PER_FLOOR)
    }

    /** The run was banked. */
    fun onBanked(profile: PlayerProfile, payout: Long, floors: Int): ProfileChange {
        val streak = profile.bankStreak + 1
        val counted = profile.copy(
            credits = profile.credits + payout,
            banksToday = profile.banksToday + 1,
            creditsToday = profile.creditsToday + payout,
            bankStreak = streak,
            bestStreakToday = maxOf(profile.bestStreakToday, streak),
            bestStreak = maxOf(profile.bestStreak, streak),
            bestPayout = maxOf(profile.bestPayout, payout),
            bestHeight = maxOf(profile.bestHeight, floors),
            runsPlayed = profile.runsPlayed + 1,
            weekBestCredits = maxOf(profile.weekBestCredits, payout),
            weekBestStreak = maxOf(profile.weekBestStreak, streak.toLong()),
            weekBestHeight = maxOf(profile.weekBestHeight, floors.toLong()),
        )
        return awardXp(counted, Economy.XP_PER_BANK)
    }

    /** The run ended with a miss: the stake is gone and the bank streak breaks. */
    fun onToppled(profile: PlayerProfile, floors: Int): PlayerProfile = profile.copy(
        bankStreak = 0,
        runsPlayed = profile.runsPlayed + 1,
        bestHeight = maxOf(profile.bestHeight, floors),
        weekBestHeight = maxOf(profile.weekBestHeight, floors.toLong()),
    )

    fun claimQuest(profile: PlayerProfile, code: String): ProfileChange {
        val card = questCards(profile).firstOrNull { it.template.code == code } ?: return ProfileChange(profile)
        if (!card.claimable) return ProfileChange(profile)
        val marked = profile.quests.map { if (it.code == code) it.copy(claimed = true) else it }
        val paid = profile.copy(
            credits = profile.credits + card.template.reward,
            quests = marked,
        )
        return ProfileChange(profile = paid, questRewards = card.template.reward)
    }

    fun claimBonus(profile: PlayerProfile, day: Long): ProfileChange {
        val state = BonusCalendar.evaluate(profile.bonusLastDay, profile.bonusStreak, day)
        if (!state.claimable) return ProfileChange(profile)
        val paid = profile.copy(
            credits = profile.credits + state.reward,
            bonusLastDay = day,
            bonusStreak = state.slot,
        )
        return ProfileChange(profile = paid, questRewards = state.reward)
    }

    fun buyStyle(profile: PlayerProfile, styleId: Int): PlayerProfile {
        val style = Catalogue.blockStyle(styleId)
        if (profile.owns(styleId)) return profile.copy(activeStyle = styleId)
        if (profile.credits < style.price) return profile
        return profile.copy(
            credits = profile.credits - style.price,
            ownedStyles = profile.ownedStyles + styleId,
            activeStyle = styleId,
        )
    }

    fun buySky(profile: PlayerProfile, skyId: Int): PlayerProfile {
        val sky = Catalogue.skyMood(skyId)
        if (profile.ownsSky(skyId)) return profile.copy(activeSky = skyId)
        if (profile.rank.rank < sky.requiredRank) return profile
        if (profile.credits < sky.price) return profile
        return profile.copy(
            credits = profile.credits - sky.price,
            ownedSkies = profile.ownedSkies + skyId,
            activeSky = skyId,
        )
    }

    fun weeklyScore(profile: PlayerProfile, metric: LadderMetric): Long = when (metric) {
        LadderMetric.HEIGHT -> profile.weekBestHeight
        LadderMetric.CREDITS -> profile.weekBestCredits
        LadderMetric.STREAK -> profile.weekBestStreak
    }
}
