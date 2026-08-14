package bp.goalsgames.blockbalance.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundMachineTest {

    /** Rolls below every hold chance, so the block always stays. */
    private fun alwaysHolds() = RoundMachine(FixedChance(0.0))

    /** Rolls above every hold chance, so the block always misses. */
    private fun alwaysMisses() = RoundMachine(FixedChance(0.999))

    @Test
    fun `fresh machine is idle and cannot bank`() {
        val machine = RoundMachine(FixedChance(0.5))
        assertEquals(RoundPhase.READY, machine.snapshot.phase)
        assertFalse(machine.snapshot.canRelease)
        assertFalse(machine.snapshot.canBank)
        assertNull(machine.bank())
    }

    @Test
    fun `stake below the floor is rejected`() {
        val machine = alwaysHolds()
        assertFalse(machine.begin(Economy.MIN_STAKE - 1))
        assertEquals(RoundPhase.READY, machine.snapshot.phase)
    }

    @Test
    fun `tier is locked once a stake is live`() {
        val machine = alwaysHolds()
        assertTrue(machine.selectTier(RiskTier.CHAOS))
        machine.begin(100)
        assertFalse(machine.selectTier(RiskTier.STEADY))
        assertEquals(RiskTier.CHAOS, machine.snapshot.tier)
    }

    @Test
    fun `release decides the verdict before the drop is settled`() {
        val machine = alwaysHolds()
        machine.begin(100)
        val verdict = machine.release()
        assertNotNull(verdict)
        assertTrue(verdict!!.holds)
        assertEquals(RoundPhase.RELEASING, machine.snapshot.phase)
        // Nothing is credited until the drop reports back.
        assertEquals(0, machine.snapshot.floors)
    }

    @Test
    fun `a held block raises the floor and compounds the multiplier`() {
        val machine = alwaysHolds()
        machine.selectTier(RiskTier.STEADY)
        machine.begin(100)
        machine.release()
        val placed = machine.settle() as SettleResult.Placed

        // FixedChance(0) returns the lower bound of the step range.
        assertEquals(RiskTier.STEADY.minStep, placed.step, 0.0001)
        assertEquals(1, placed.floors)
        assertEquals(0.85, machine.snapshot.combo, 0.0001)
        assertEquals(RoundPhase.SWINGING, machine.snapshot.phase)
    }

    @Test
    fun `multiplier is trimmed to two decimals after every floor`() {
        val machine = RoundMachine(FixedChance(0.5))
        machine.selectTier(RiskTier.CHAOS)
        machine.begin(1_000)
        repeat(4) {
            machine.release()
            machine.settle()
        }
        val combo = machine.snapshot.combo
        assertEquals(combo, Economy.trim(combo), 0.0)
    }

    @Test
    fun `payout floors the product of stake and multiplier`() {
        assertEquals(149L, Economy.payout(100L, 1.49))
        assertEquals(1L, Economy.payout(1L, 1.99))
        assertEquals(0L, Economy.payout(10L, 0.0))
    }

    @Test
    fun `banking is blocked before the first floor and allowed after it`() {
        val machine = alwaysHolds()
        machine.begin(200)
        assertNull(machine.bank())

        machine.release()
        machine.settle()
        val result = machine.bank()
        assertNotNull(result)
        assertEquals(RoundPhase.BANKED, machine.snapshot.phase)
        assertEquals(Economy.payout(200, machine.snapshot.combo), result!!.payout)
    }

    @Test
    fun `a missed block topples the run and keeps the stake`() {
        val machine = alwaysMisses()
        machine.begin(500)
        machine.release()
        val result = machine.settle() as SettleResult.Toppled

        assertEquals(0, result.floors)
        assertEquals(500L, result.lostStake)
        assertEquals(RoundPhase.TOPPLED, machine.snapshot.phase)
        assertNull(machine.release())
        assertNull(machine.bank())
    }

    @Test
    fun `settling twice has no extra effect`() {
        val machine = alwaysHolds()
        machine.begin(100)
        machine.release()
        assertNotNull(machine.settle())
        assertNull(machine.settle())
        assertEquals(1, machine.snapshot.floors)
    }

    @Test
    fun `clearing keeps the tier and drops the run`() {
        val machine = alwaysHolds()
        machine.selectTier(RiskTier.RISKY)
        machine.begin(100)
        machine.release()
        machine.settle()
        machine.clear()

        assertEquals(RoundPhase.READY, machine.snapshot.phase)
        assertEquals(RiskTier.RISKY, machine.snapshot.tier)
        assertEquals(0, machine.snapshot.floors)
        assertEquals(1.0, machine.snapshot.combo, 0.0)
        assertTrue(machine.snapshot.steps.isEmpty())
    }

    @Test
    fun `step history keeps every floor in order`() {
        val machine = RoundMachine(FixedChance(0.25))
        machine.begin(100)
        repeat(3) {
            machine.release()
            machine.settle()
        }
        assertEquals(3, machine.snapshot.steps.size)
        assertTrue(machine.snapshot.steps.all { it > 0.0 })
    }

    @Test
    fun `hoist speeds up with height but never past the tier floor`() {
        val tier = RiskTier.CHAOS
        assertEquals(tier.baseSwingSeconds, tier.swingSeconds(0), 0.0001)
        assertTrue(tier.swingSeconds(3) < tier.swingSeconds(0))
        assertEquals(tier.fastestSwingSeconds, tier.swingSeconds(200), 0.0001)
    }

    @Test
    fun `stake clamping respects the balance and the hard ceiling`() {
        assertEquals(Economy.MIN_STAKE, Economy.clampStake(1, 10_000))
        assertEquals(400L, Economy.clampStake(900, 400))
        assertEquals(Economy.MAX_STAKE, Economy.clampStake(Long.MAX_VALUE, Long.MAX_VALUE))
        // A broke player still sees a sane number rather than zero.
        assertEquals(Economy.MIN_STAKE, Economy.clampStake(50, 0))
        assertFalse(Economy.canAfford(Economy.MIN_STAKE, 0))
    }

    @Test
    fun `every tier keeps its risk and reward ordering`() {
        val tiers = RiskTier.entries
        for (index in 1 until tiers.size) {
            val safer = tiers[index - 1]
            val riskier = tiers[index]
            assertTrue(riskier.holdChance < safer.holdChance)
            assertTrue(riskier.maxStep > safer.maxStep)
            assertTrue(riskier.baseSwingSeconds < safer.baseSwingSeconds)
        }
    }
}
