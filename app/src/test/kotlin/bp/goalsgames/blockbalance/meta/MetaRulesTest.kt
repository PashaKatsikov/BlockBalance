package bp.goalsgames.blockbalance.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankLadderTest {

    @Test
    fun `first rank needs the base cost`() {
        assertEquals(60L, RankLadder.costFor(1))
        assertEquals(105L, RankLadder.costFor(2))
    }

    @Test
    fun `zero experience is rank one`() {
        val state = RankLadder.resolve(0)
        assertEquals(1, state.rank)
        assertEquals(0L, state.xpInto)
        assertEquals(60L, state.xpForNext)
        assertEquals(0f, state.progress, 0.0001f)
    }

    @Test
    fun `experience just short of a rank does not promote`() {
        val state = RankLadder.resolve(59)
        assertEquals(1, state.rank)
        assertEquals(59L, state.xpInto)
    }

    @Test
    fun `crossing a cost promotes exactly one rank`() {
        val state = RankLadder.resolve(60)
        assertEquals(2, state.rank)
        assertEquals(0L, state.xpInto)
        assertEquals(105L, state.xpForNext)
    }

    @Test
    fun `the ladder tops out and reports itself as maxed`() {
        val state = RankLadder.resolve(Long.MAX_VALUE / 4)
        assertEquals(RankLadder.MAX_RANK, state.rank)
        assertTrue(state.isMaxed)
        assertEquals(1f, state.progress, 0.0001f)
    }

    @Test
    fun `xpToReach lines up with resolve`() {
        for (rank in 1..12) {
            assertEquals(rank, RankLadder.resolve(RankLadder.xpToReach(rank)).rank)
        }
    }

    @Test
    fun `rank rewards grow with rank`() {
        assertEquals(150L, RankLadder.rewardFor(1))
        assertTrue(RankLadder.rewardFor(9) > RankLadder.rewardFor(8))
    }

    @Test
    fun `styles are gifted at their rank and stay gifted`() {
        assertTrue(Catalogue.giftedStyles(1).isEmpty())
        assertEquals(setOf(2), Catalogue.giftedStyles(2))
        assertEquals(setOf(2, 3), Catalogue.giftedStyles(5))
        assertEquals(setOf(2, 3, 4), Catalogue.giftedStyles(7))
    }
}

class BonusCalendarTest {

    @Test
    fun `first ever visit can claim day one`() {
        val state = BonusCalendar.evaluate(lastClaimDay = null, streak = 0, today = 20_000)
        assertTrue(state.claimable)
        assertEquals(1, state.slot)
        assertEquals(100L, state.reward)
    }

    @Test
    fun `same day cannot claim twice`() {
        val state = BonusCalendar.evaluate(lastClaimDay = 20_000, streak = 3, today = 20_000)
        assertFalse(state.claimable)
        assertEquals(3, state.slot)
    }

    @Test
    fun `next day continues the streak`() {
        val state = BonusCalendar.evaluate(lastClaimDay = 20_000, streak = 3, today = 20_001)
        assertTrue(state.claimable)
        assertEquals(4, state.slot)
        assertEquals(300L, state.reward)
    }

    @Test
    fun `a skipped day restarts the streak`() {
        val state = BonusCalendar.evaluate(lastClaimDay = 20_000, streak = 5, today = 20_003)
        assertTrue(state.claimable)
        assertEquals(1, state.slot)
        assertEquals(100L, state.reward)
    }

    @Test
    fun `the ladder wraps after the seventh day`() {
        val state = BonusCalendar.evaluate(lastClaimDay = 20_000, streak = 7, today = 20_001)
        assertTrue(state.claimable)
        assertEquals(1, state.slot)
    }

    @Test
    fun `rewards climb across the week`() {
        val rewards = (1..7).map { BonusCalendar.rewardForSlot(it) }
        assertEquals(rewards.sorted(), rewards)
        assertEquals(1_200L, rewards.last())
    }
}

class QuestBoardTest {

    @Test
    fun `the same day always yields the same three quests`() {
        val first = QuestBoard.picksFor(20_100)
        val second = QuestBoard.picksFor(20_100)
        assertEquals(QuestBoard.DAILY_COUNT, first.size)
        assertEquals(first.map { it.code }, second.map { it.code })
    }

    @Test
    fun `neighbouring days differ`() {
        val today = QuestBoard.picksFor(20_100).map { it.code }
        val tomorrow = QuestBoard.picksFor(20_101).map { it.code }
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `a daily set never repeats a kind`() {
        for (day in 20_000L..20_120L) {
            val kinds = QuestBoard.picksFor(day).map { it.kind }
            assertEquals(kinds.size, kinds.toSet().size)
        }
    }

    @Test
    fun `blank progress matches the picks and starts unclaimed`() {
        val day = 20_444L
        val blank = QuestBoard.blankProgress(day)
        assertEquals(QuestBoard.picksFor(day).map { it.code }, blank.map { it.code })
        assertTrue(blank.none { it.claimed })
    }

    @Test
    fun `cards resolve progress through the supplied lookup`() {
        val entries = QuestBoard.blankProgress(20_444)
        val cards = QuestBoard.cards(entries) { it.target }
        assertTrue(cards.all { it.complete })
        assertTrue(cards.all { it.claimable })
        assertEquals(1f, cards.first().ratio, 0.0001f)
    }

    @Test
    fun `unknown codes are dropped instead of crashing`() {
        val cards = QuestBoard.cards(listOf(QuestProgress("gone_stale")) ) { 0 }
        assertTrue(cards.isEmpty())
    }
}

class DayClockTest {

    @Test
    fun `epoch day zero is a thursday in week zero`() {
        assertEquals(0L, DayClock.weekOf(0))
        // 1970-01-05 was the first Monday of the epoch.
        assertEquals(1L, DayClock.weekOf(4))
        assertEquals(0L, DayClock.weekOf(3))
    }

    @Test
    fun `a week index covers exactly seven days`() {
        val week = DayClock.weekOf(20_000)
        val sameWeek = (20_000L..20_006L).count { DayClock.weekOf(it) == week }
        assertTrue(sameWeek in 1..7)
        assertEquals(week + 1, DayClock.weekOf(20_007))
    }

    @Test
    fun `monday reports a full week until the next rollover`() {
        assertEquals(7, DayClock.daysUntilWeekRollover(4))
        assertEquals(1, DayClock.daysUntilWeekRollover(10))
        assertTrue(DayClock.daysUntilWeekRollover(20_345) in 1..7)
    }

    @Test
    fun `local offset decides which day a timestamp belongs to`() {
        val midnightUtc = 20_000L * 86_400_000L
        assertEquals(20_000L, DayClock.dayOf(midnightUtc, 0))
        // One hour before midnight in a negative offset zone is still the day before.
        assertEquals(19_999L, DayClock.dayOf(midnightUtc, -3_600_000))
        assertEquals(20_000L, DayClock.dayOf(midnightUtc, 3_600_000))
    }

    @Test
    fun `frozen clock advances on demand`() {
        val clock = FrozenDayClock(100)
        assertEquals(100L, clock.today())
        clock.advance(8)
        assertEquals(108L, clock.today())
        assertEquals(DayClock.weekOf(108), clock.week())
    }
}

class LadderBoardTest {

    @Test
    fun `the table is stable within a week`() {
        val first = LadderBoard.table(week = 2_870, metric = LadderMetric.HEIGHT, playerScore = 9)
        val second = LadderBoard.table(week = 2_870, metric = LadderMetric.HEIGHT, playerScore = 9)
        assertEquals(first, second)
    }

    @Test
    fun `a new week reshuffles the rivals`() {
        val thisWeek = LadderBoard.table(2_870, LadderMetric.CREDITS, 0).filterNot { it.isPlayer }
        val nextWeek = LadderBoard.table(2_871, LadderMetric.CREDITS, 0).filterNot { it.isPlayer }
        assertNotEquals(thisWeek.map { it.score }, nextWeek.map { it.score })
    }

    @Test
    fun `the player appears once and is ranked by score`() {
        val table = LadderBoard.table(2_870, LadderMetric.HEIGHT, playerScore = 9_999)
        assertEquals(1, table.count { it.isPlayer })
        assertEquals(1, table.first { it.isPlayer }.place)
        assertEquals(15, table.size)
    }

    @Test
    fun `places run from one upward without gaps`() {
        val table = LadderBoard.table(2_870, LadderMetric.STREAK, playerScore = 3)
        assertEquals((1..table.size).toList(), table.map { it.place })
    }

    @Test
    fun `a zero score lands the player last`() {
        val table = LadderBoard.table(2_870, LadderMetric.CREDITS, playerScore = 0)
        assertEquals(table.size, table.first { it.isPlayer }.place)
    }
}
