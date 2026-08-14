package bp.goalsgames.blockbalance.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `multipliers always show two decimals`() {
        assertEquals("1.00", formatMultiplier(1.0))
        assertEquals("0.85", formatMultiplier(0.85))
        assertEquals("2.70", formatMultiplier(2.7))
        assertEquals("12.05", formatMultiplier(12.049))
        assertEquals("0.00", formatMultiplier(0.0))
    }

    @Test
    fun `digits are grouped in threes`() {
        assertEquals("7", groupDigits(7))
        assertEquals("999", groupDigits(999))
        assertEquals("1 000", groupDigits(1_000))
        assertEquals("100 000", groupDigits(100_000))
    }

    @Test
    fun `large balances collapse to compact units`() {
        assertEquals("9 999", formatCredits(9_999))
        assertEquals("10K", formatCredits(10_000))
        assertEquals("12.3K", formatCredits(12_345))
        assertEquals("1M", formatCredits(1_000_000))
        assertEquals("-250", formatCredits(-250))
    }
}
