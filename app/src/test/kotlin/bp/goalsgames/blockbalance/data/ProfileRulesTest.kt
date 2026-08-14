package bp.goalsgames.blockbalance.data

import bp.goalsgames.blockbalance.core.Economy
import bp.goalsgames.blockbalance.meta.Catalogue
import bp.goalsgames.blockbalance.meta.DayClock
import bp.goalsgames.blockbalance.meta.LadderMetric
import bp.goalsgames.blockbalance.meta.QuestBoard
import bp.goalsgames.blockbalance.meta.QuestKind
import bp.goalsgames.blockbalance.meta.RankLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {

    private val day = 20_500L
    private val week = DayClock.weekOf(day)

    private fun fresh(): PlayerProfile = ProfileRules.rollOver(PlayerProfile(), day, week)

    @Test
    fun `a first run profile starts with the opening balance and one style`() {
        val profile = PlayerProfile()
        assertEquals(Economy.STARTING_CREDITS, profile.credits)
        assertEquals(setOf(Catalogue.defaultBlockStyle.id), profile.ownedStyles)
        assertEquals(1, profile.rank.rank)
        assertTrue(profile.shuffleStyles)
        assertTrue(profile.hapticsOn)
    }

    @Test
    fun `roll over seeds quests for the day and clears counters`() {
        val stale = PlayerProfile(
            questDay = day - 1,
            blocksToday = 12,
            banksToday = 3,
            creditsToday = 900,
            bestFloorToday = 7,
            bestStreakToday = 4,
        )
        val rolled = ProfileRules.rollOver(stale, day, week)

        assertEquals(day, rolled.questDay)
        assertEquals(QuestBoard.picksFor(day).map { it.code }, rolled.quests.map { it.code })
        assertEquals(0, rolled.blocksToday)
        assertEquals(0, rolled.banksToday)
        assertEquals(0L, rolled.creditsToday)
        assertEquals(0, rolled.bestFloorToday)
        assertEquals(0, rolled.bestStreakToday)
    }

    @Test
    fun `roll over on the same day leaves progress alone`() {
        val today = ProfileRules.rollOver(PlayerProfile(), day, week).copy(blocksToday = 5)
        val again = ProfileRules.rollOver(today, day, week)
        assertEquals(5, again.blocksToday)
    }

    @Test
    fun `a new week wipes only the weekly bests`() {
        val stored = fresh().copy(
            weekBestHeight = 21,
            weekBestCredits = 5_000,
            weekBestStreak = 6,
            bestHeight = 21,
            bankStreak = 6,
        )
        val rolled = ProfileRules.rollOver(stored, day, week + 1)

        assertEquals(0L, rolled.weekBestHeight)
        assertEquals(0L, rolled.weekBestCredits)
        assertEquals(0L, rolled.weekBestStreak)
        assertEquals(21, rolled.bestHeight)
        assertEquals(6, rolled.bankStreak)
    }

    @Test
    fun `placing a stake debits the balance and remembers it`() {
        val after = ProfileRules.onStakePlaced(fresh(), 250)
        assertEquals(Economy.STARTING_CREDITS - 250, after.credits)
        assertEquals(250L, after.lastStake)
    }

    @Test
    fun `a landed block pays experience and tracks the height`() {
        val change = ProfileRules.onBlockPlaced(fresh(), floors = 4)
        assertEquals(Economy.XP_PER_FLOOR, change.profile.totalXp)
        assertEquals(1, change.profile.blocksToday)
        assertEquals(4, change.profile.bestFloorToday)
        assertEquals(4, change.profile.bestHeight)
        assertEquals(4L, change.profile.weekBestHeight)
    }

    @Test
    fun `a bank credits the payout and extends the streak`() {
        val change = ProfileRules.onBanked(fresh(), payout = 1_800, floors = 6)
        val profile = change.profile

        assertEquals(Economy.STARTING_CREDITS + 1_800, profile.credits)
        assertEquals(1, profile.banksToday)
        assertEquals(1_800L, profile.creditsToday)
        assertEquals(1, profile.bankStreak)
        assertEquals(1, profile.bestStreakToday)
        assertEquals(1_800L, profile.bestPayout)
        assertEquals(6, profile.bestHeight)
        assertEquals(1, profile.runsPlayed)
        assertEquals(Economy.XP_PER_BANK, profile.totalXp)
    }

    @Test
    fun `a topple breaks the streak but keeps the daily best`() {
        val banked = ProfileRules.onBanked(fresh(), payout = 500, floors = 3).profile
        val toppled = ProfileRules.onToppled(banked, floors = 5)

        assertEquals(0, toppled.bankStreak)
        assertEquals(1, toppled.bestStreakToday)
        assertEquals(5, toppled.bestHeight)
        assertEquals(2, toppled.runsPlayed)
    }

    @Test
    fun `crossing a rank pays its reward and gifts its style`() {
        val nearRankTwo = fresh().copy(totalXp = RankLadder.costFor(1) - 1)
        val change = ProfileRules.awardXp(nearRankTwo, Economy.XP_PER_FLOOR)

        assertNotNull(change.rankGain)
        val gain = change.rankGain!!
        assertEquals(1, gain.fromRank)
        assertEquals(2, gain.toRank)
        assertEquals(RankLadder.rewardFor(2), gain.credits)
        assertEquals(setOf(2), gain.unlockedStyles)
        assertTrue(change.profile.owns(2))
        assertEquals(Economy.STARTING_CREDITS + RankLadder.rewardFor(2), change.profile.credits)
    }

    @Test
    fun `a single award can cross several ranks and pay for each`() {
        val change = ProfileRules.awardXp(fresh(), RankLadder.xpToReach(4))
        val gain = change.rankGain!!
        assertEquals(4, gain.toRank)
        val expected = (2..4).sumOf { RankLadder.rewardFor(it) }
        assertEquals(expected, gain.credits)
    }

    @Test
    fun `staying inside a rank reports no gain`() {
        val change = ProfileRules.awardXp(fresh(), 1)
        assertNull(change.rankGain)
        assertEquals(1L, change.profile.totalXp)
    }

    @Test
    fun `quest progress reads the matching counter`() {
        val profile = fresh().copy(
            blocksToday = 9,
            banksToday = 2,
            bestStreakToday = 3,
            bestFloorToday = 11,
            creditsToday = 2_400,
        )
        fun progressOf(kind: QuestKind) = ProfileRules.questProgress(
            profile,
            QuestBoard.pool.first { it.kind == kind },
        )

        assertEquals(9, progressOf(QuestKind.BLOCKS_PLACED))
        assertEquals(2, progressOf(QuestKind.RUNS_BANKED))
        assertEquals(3, progressOf(QuestKind.BANK_STREAK))
        assertEquals(11, progressOf(QuestKind.FLOOR_REACHED))
        assertEquals(2_400, progressOf(QuestKind.CREDITS_BANKED))
    }

    @Test
    fun `a quest pays out once`() {
        val target = ProfileRules.questCards(fresh()).first()
        val ready = when (target.template.kind) {
            QuestKind.BLOCKS_PLACED -> fresh().copy(blocksToday = target.template.target)
            QuestKind.RUNS_BANKED -> fresh().copy(banksToday = target.template.target)
            QuestKind.BANK_STREAK -> fresh().copy(bestStreakToday = target.template.target)
            QuestKind.FLOOR_REACHED -> fresh().copy(bestFloorToday = target.template.target)
            QuestKind.CREDITS_BANKED -> fresh().copy(creditsToday = target.template.target.toLong())
        }

        val first = ProfileRules.claimQuest(ready, target.template.code)
        assertEquals(target.template.reward, first.questRewards)
        assertEquals(ready.credits + target.template.reward, first.profile.credits)

        val second = ProfileRules.claimQuest(first.profile, target.template.code)
        assertEquals(0L, second.questRewards)
        assertEquals(first.profile.credits, second.profile.credits)
    }

    @Test
    fun `an unfinished quest cannot be claimed`() {
        val target = ProfileRules.questCards(fresh()).first().template
        val change = ProfileRules.claimQuest(fresh(), target.code)
        assertEquals(0L, change.questRewards)
    }

    @Test
    fun `the daily reward can only be taken once per day`() {
        val first = ProfileRules.claimBonus(fresh(), day)
        assertEquals(100L, first.questRewards)
        assertEquals(day, first.profile.bonusLastDay)
        assertEquals(1, first.profile.bonusStreak)

        val repeat = ProfileRules.claimBonus(first.profile, day)
        assertEquals(0L, repeat.questRewards)

        val nextDay = ProfileRules.claimBonus(first.profile, day + 1)
        assertEquals(150L, nextDay.questRewards)
        assertEquals(2, nextDay.profile.bonusStreak)
    }

    @Test
    fun `buying a style spends the price and equips it`() {
        val style = Catalogue.blockStyle(2)
        val rich = fresh().copy(credits = style.price + 10)
        val bought = ProfileRules.buyStyle(rich, style.id)

        assertTrue(bought.owns(style.id))
        assertEquals(style.id, bought.activeStyle)
        assertEquals(10L, bought.credits)
    }

    @Test
    fun `an unaffordable style changes nothing`() {
        val poor = fresh().copy(credits = 5)
        val same = ProfileRules.buyStyle(poor, 4)
        assertFalse(same.owns(4))
        assertEquals(5L, same.credits)
    }

    @Test
    fun `re-selecting an owned style is free`() {
        val owned = fresh().copy(ownedStyles = setOf(1, 3), credits = 40)
        val equipped = ProfileRules.buyStyle(owned, 3)
        assertEquals(3, equipped.activeStyle)
        assertEquals(40L, equipped.credits)
    }

    @Test
    fun `a sky locked behind a rank cannot be bought yet`() {
        val mood = Catalogue.skyMood(4)
        val rich = fresh().copy(credits = mood.price * 2)
        assertFalse(ProfileRules.buySky(rich, mood.id).ownsSky(mood.id))

        val ranked = rich.copy(totalXp = RankLadder.xpToReach(mood.requiredRank))
        val bought = ProfileRules.buySky(ranked, mood.id)
        assertTrue(bought.ownsSky(mood.id))
        assertEquals(mood.id, bought.activeSky)
    }

    @Test
    fun `weekly score picks the metric it was asked for`() {
        val profile = fresh().copy(weekBestHeight = 12, weekBestCredits = 7_400, weekBestStreak = 5)
        assertEquals(12L, ProfileRules.weeklyScore(profile, LadderMetric.HEIGHT))
        assertEquals(7_400L, ProfileRules.weeklyScore(profile, LadderMetric.CREDITS))
        assertEquals(5L, ProfileRules.weeklyScore(profile, LadderMetric.STREAK))
    }

    @Test
    fun `shuffling only ever picks an owned style`() {
        val profile = fresh().copy(ownedStyles = setOf(1, 4))
        repeat(30) {
            assertTrue(profile.playableStyles().random() in profile.ownedStyles)
        }
    }

    @Test
    fun `spending never pushes the balance below zero`() {
        assertEquals(0L, ProfileRules.spend(fresh().copy(credits = 20), 900).credits)
    }
}
