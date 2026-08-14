package bp.goalsgames.blockbalance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the shell the way a player would: through the boot screen into the menu
 * and out to every secondary screen and back.
 */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<StageActivity>()

    private fun reachMenu() {
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("PLAY").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun menuOpensAfterBoot() {
        reachMenu()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }

    @Test
    fun storeOpensAndReturns() {
        reachMenu()
        rule.onNodeWithText("Store").performClick()
        rule.onNodeWithText("BLOCK STYLES").assertIsDisplayed()
        rule.onNodeWithText("BACK").performClick()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }

    @Test
    fun questsOpensAndReturns() {
        reachMenu()
        rule.onNodeWithText("Quests").performClick()
        rule.onNodeWithText("DAILY QUESTS").assertIsDisplayed()
        rule.onNodeWithText("BACK").performClick()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }

    @Test
    fun ranksOpensAndReturns() {
        reachMenu()
        rule.onNodeWithText("Ranks").performClick()
        rule.onNodeWithText("WEEKLY RANKS").assertIsDisplayed()
        rule.onNodeWithText("BACK").performClick()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }

    @Test
    fun settingsOpensAndReturns() {
        reachMenu()
        rule.onNodeWithText("SETTINGS").performClick()
        rule.onNodeWithText("Haptics").assertIsDisplayed()
        rule.onNodeWithText("BACK").performClick()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }

    @Test
    fun yardOpensWithTheControlDeck() {
        reachMenu()
        rule.onNodeWithText("PLAY").performClick()
        rule.onNodeWithText("STACK").assertIsDisplayed()
        rule.onNodeWithText("BANK IT").assertIsDisplayed()
        rule.onNodeWithText("EXIT").performClick()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }

    @Test
    fun privacyPageOpensFromTheMenu() {
        reachMenu()
        rule.onNodeWithText("Privacy").performClick()
        rule.onNodeWithText("PRIVACY POLICY").assertIsDisplayed()
    }

    /** A configuration change or a process rebuild has to land back on a usable menu. */
    @Test
    fun theShellComesBackAfterTheActivityIsRecreated() {
        reachMenu()
        rule.activityRule.scenario.recreate()
        reachMenu()
        rule.onNodeWithText("PLAY").assertIsDisplayed()
    }
}
