package bp.goalsgames.blockbalance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import bp.goalsgames.blockbalance.core.RiskTier
import bp.goalsgames.blockbalance.data.PlayerProfile
import bp.goalsgames.blockbalance.data.ProfileStore
import bp.goalsgames.blockbalance.meta.QuestBoard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Round trips the stored profile through the real DataStore file. */
@RunWith(AndroidJUnit4::class)
class ProfileStoreTest {

    private lateinit var store: ProfileStore

    @Before
    fun setUp() = runBlocking {
        store = ProfileStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.reset()
    }

    @Test
    fun defaultsAreUsedOnAFreshInstall() = runBlocking {
        val profile = store.profile.first()
        assertEquals(PlayerProfile().credits, profile.credits)
        assertEquals(RiskTier.STEADY, profile.tier)
        assertTrue(profile.ownedStyles.isNotEmpty())
    }

    @Test
    fun everyFieldSurvivesARoundTrip() = runBlocking {
        val quests = QuestBoard.blankProgress(20_777).map { it.copy(claimed = true) }
        val written = PlayerProfile(
            credits = 4_321,
            totalXp = 654,
            tier = RiskTier.CHAOS,
            lastStake = 2_500,
            hapticsOn = false,
            shuffleStyles = false,
            ownedStyles = setOf(1, 3, 4),
            activeStyle = 3,
            ownedSkies = setOf(1, 2),
            activeSky = 2,
            bonusLastDay = 20_777,
            bonusStreak = 5,
            questDay = 20_777,
            quests = quests,
            blocksToday = 17,
            banksToday = 4,
            creditsToday = 9_000,
            bankStreak = 3,
            bestFloorToday = 11,
            bestStreakToday = 3,
            ladderWeek = 2_968,
            weekBestHeight = 19,
            weekBestCredits = 12_000,
            weekBestStreak = 4,
            bestPayout = 44_000,
            bestHeight = 23,
            bestStreak = 6,
            runsPlayed = 88,
        )

        store.mutate { written }
        val read = store.profile.first()

        assertEquals(written, read)
    }

    @Test
    fun mutationsSeeThePreviousValue() = runBlocking {
        store.mutate { it.copy(credits = 100) }
        store.mutate { it.copy(credits = it.credits + 50) }
        assertEquals(150L, store.profile.first().credits)
    }

    @Test
    fun resetReturnsToTheOpeningProfile() = runBlocking {
        store.mutate { it.copy(credits = 1, totalXp = 9_000, ownedStyles = setOf(1, 2, 3, 4)) }
        store.reset()
        assertEquals(PlayerProfile(), store.profile.first())
    }
}
