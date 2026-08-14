package bp.goalsgames.blockbalance.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The yard only plays back verdicts, so the checks here are about the animation
 * reaching a conclusion and reporting it exactly once.
 */
class StageSceneTest {

    private val steps = ArrayList<StageEvent>()

    private fun scene(): StageScene {
        val scene = StageScene(Random(7))
        scene.onEvent = { event -> steps += event }
        scene.updateViewport(8f, 12f)
        return scene
    }

    /** Runs frames at a steady 60 fps until [stop] is happy or the budget runs out. */
    private fun StageScene.run(seconds: Float = 6f, stop: () -> Boolean = { false }) {
        val frames = (seconds / FRAME).toInt()
        repeat(frames) {
            if (stop()) return
            advance(FRAME)
        }
    }

    @Test
    fun `a scene survives frames before it ever gets a viewport`() {
        val scene = StageScene(Random(1))
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1f))
        scene.run()
        assertNotNull(scene.hoisted)
    }

    @Test
    fun `priming hangs a block on the hook`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)

        assertNotNull(scene.hoisted)
        assertEquals(0, scene.floors)
        assertTrue(steps.isEmpty())
    }

    @Test
    fun `the wide hex cabin is scaled to the visual mass of a house`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 4, aspect = 1.5f))
        scene.advance(FRAME)

        val hex = scene.hoisted ?: error("nothing on the hook")
        val hexArea = hex.width * hex.height
        val houseArea = StageMetrics.BLOCK_WIDTH * (StageMetrics.BLOCK_WIDTH / 0.85f)

        assertEquals(StageMetrics.BLOCK_WIDTH * 1.33f, hex.width, 0.001f)
        assertEquals(houseArea, hexArea, houseArea * 0.08f)
    }

    @Test
    fun `a held drop lands on the stack and reports one floor`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)
        scene.submit(StageCommand.Release(holds = true))
        scene.run { steps.isNotEmpty() }

        assertEquals(listOf(StageEvent.Landed(1)), steps)
        assertEquals(1, scene.floors)
        assertNull(scene.airborne)
        assertNull(scene.hoisted)
    }

    @Test
    fun `a missed drop reports once and leaves the stack alone`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)
        scene.submit(StageCommand.Release(holds = false))
        scene.run()

        assertEquals(listOf(StageEvent.Missed), steps)
        assertEquals(0, scene.floors)
    }

    @Test
    fun `a stack climbs while the camera keeps up`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)

        repeat(4) { floor ->
            scene.submit(StageCommand.Release(holds = true))
            scene.run { steps.size > floor }
            scene.submit(StageCommand.Hoist(styleId = 1, aspect = 1.2f))
            scene.advance(FRAME)
        }
        val restingCamera = scene.cameraY
        scene.run(seconds = 2f)

        assertEquals(4, scene.floors)
        assertTrue(scene.stackTopY < scene.baseTopY)
        assertTrue("camera should have climbed", scene.cameraY <= restingCamera)
    }

    @Test
    fun `a hanging block clears the hook by the length of its straps`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)

        val block = scene.hoisted ?: error("nothing on the hook")
        val hookTipY = scene.alongRopeY(scene.hookTipDistance())
        val blockTopY = block.centerY - block.height / 2f

        assertEquals(StageMetrics.STRAP_DROP, blockTopY - hookTipY, 0.001f)
        assertEquals(0f, block.lean, 0.0001f)
    }

    @Test
    fun `the trolley keeps its cable vertical while moving sideways`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.run(seconds = 0.2f)

        assertTrue("trolley should have moved", kotlin.math.abs(scene.hoistX) > 0.1f)
        assertEquals(scene.pivotX(), scene.alongRopeX(scene.ropeLength()), 0.0001f)
        assertEquals(scene.pivotX(), scene.hoisted?.centerX ?: 0f, 0.0001f)
    }

    @Test
    fun `the pavement stays above the control deck`() {
        val halfHeight = 17f
        val scene = StageScene(Random(3))
        scene.updateViewport(6f, halfHeight)
        scene.advance(FRAME)

        val bottomEdge = scene.cameraY + halfHeight
        val streetBottom = StageMetrics.GROUND_Y + StageMetrics.STREET_BELOW
        val deck = halfHeight * 2f * StageMetrics.DECK_FRACTION

        assertEquals(deck, bottomEdge - streetBottom, 0.01f)
    }

    @Test
    fun `a wipe empties the yard`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)
        scene.submit(StageCommand.Release(holds = true))
        scene.run { steps.isNotEmpty() }
        scene.submit(StageCommand.Wipe)
        scene.advance(FRAME)

        assertEquals(0, scene.floors)
        assertNull(scene.hoisted)
        assertNull(scene.airborne)
    }

    @Test
    fun `banking showers coins without touching the stack`() {
        val scene = scene()
        scene.submit(StageCommand.Prime(styleId = 1, aspect = 1.2f))
        scene.advance(FRAME)
        scene.submit(StageCommand.Release(holds = true))
        scene.run { steps.isNotEmpty() }
        scene.submit(StageCommand.Celebrate)
        scene.advance(FRAME)

        assertEquals(1, scene.floors)
        assertTrue(scene.flecks.any { it.kind == FleckKind.COIN })
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}
