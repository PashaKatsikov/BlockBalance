package bp.goalsgames.blockbalance.stage

import bp.goalsgames.blockbalance.meta.Catalogue
import bp.goalsgames.blockbalance.meta.SkyMood
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The animated yard. It plays back verdicts that were already decided by the
 * round rules, so geometry never determines whether a block survives; it only
 * decides how the drop looks and when the result should be shown.
 *
 * Commands arrive from the UI thread through a queue and are consumed at the
 * start of a frame. Events travel back through [onEvent], which is invoked on
 * the render thread and is expected to hand off to a thread-safe sink.
 */
class StageScene(private val random: Random = Random.Default) {

    @Volatile
    var onEvent: ((StageEvent) -> Unit)? = null

    private val inbox = ConcurrentLinkedQueue<StageCommand>()

    val settled = ArrayList<SettledBlock>(32)
    val flecks = ArrayList<Fleck>(96)
    val puffs = ArrayList<Puff>(StageMetrics.CLOUD_COUNT)

    var hoisted: HoistedBlock? = null
        private set
    var airborne: AirborneBlock? = null
        private set

    var sky: SkyMood = Catalogue.defaultSkyMood
        private set

    /** Written by the UI once the base sprite is decoded, read while drawing. */
    @Volatile
    var baseAspect = 1.11f

    var cameraY = 0f
        private set
    var shake = 0f
        private set
    var swingAngle = 0f
        private set
    var hoistX = 0f
        private set
    var hookLift = 0f
        private set

    private var swingPhase = 0f
    private var swingSeconds = 1.2f
    private var hookLiftTarget = 0f
    private var viewportHalfHeight = 12f
    private var visibleHalfWidth = StageMetrics.WORLD_WIDTH / 2f
    private var cameraReady = false
    private var elapsed = 0f

    val floors: Int get() = settled.size

    val baseHeight: Float get() = StageMetrics.BASE_WIDTH / baseAspect

    val baseTopY: Float get() = StageMetrics.GROUND_Y - baseHeight

    val stackTopY: Float get() = settled.lastOrNull()?.topY ?: baseTopY

    fun submit(command: StageCommand) {
        inbox.add(command)
    }

    /**
     * Called from the render thread when the drawing surface changes size, so the
     * cloud list is never grown while a frame is walking it.
     */
    fun updateViewport(worldHalfWidth: Float, worldHalfHeight: Float) {
        visibleHalfWidth = worldHalfWidth
        viewportHalfHeight = worldHalfHeight
        if (!cameraReady) {
            cameraY = restingCameraY()
            cameraReady = true
        }
        if (puffs.isEmpty()) seedPuffs()
    }

    fun advance(rawSeconds: Float) {
        val dt = min(rawSeconds, StageMetrics.MAX_STEP_SECONDS)
        if (dt <= 0f) return
        elapsed += dt
        // Keeps the shake oscillator in a range where float precision stays fine.
        if (elapsed > 1_000f) elapsed -= 1_000f

        drainCommands()
        advanceHoist(dt)
        advanceAirborne(dt)
        advanceSettled(dt)
        advanceCamera(dt)
        advanceFlecks(dt)
        advancePuffs(dt)

        shake = (shake - StageMetrics.SHAKE_DECAY * dt).coerceAtLeast(0f)
    }

    /** Random-but-steady screen offset for the current shake level. */
    fun shakeOffset(): Pair<Float, Float> {
        if (shake <= 0f) return 0f to 0f
        val amount = shake * StageMetrics.SHAKE_TRAVEL
        val x = sin(elapsed * 47f) * amount
        val y = cos(elapsed * 39f) * amount
        return x to y
    }

    private fun drainCommands() {
        while (true) {
            val command = inbox.poll() ?: return
            when (command) {
                is StageCommand.Configure -> {
                    swingSeconds = command.swingSeconds.coerceAtLeast(0.2f)
                    sky = command.sky
                }

                is StageCommand.Prime -> {
                    settled.clear()
                    airborne = null
                    flecks.clear()
                    hookLift = 0f
                    hookLiftTarget = 0f
                    hoisted = spawnHoisted(command.styleId, command.aspect)
                }

                is StageCommand.Release -> releaseHoisted(command.holds)

                is StageCommand.Hoist -> {
                    hoisted = spawnHoisted(command.styleId, command.aspect)
                    hookLiftTarget = 0f
                }

                StageCommand.Celebrate -> {
                    spawnCoins()
                    shake = maxOf(shake, StageMetrics.SHAKE_BANK)
                }

                StageCommand.Wipe -> {
                    settled.clear()
                    airborne = null
                    hoisted = null
                    flecks.clear()
                    hookLiftTarget = 1f
                }
            }
        }
    }

    private fun spawnHoisted(styleId: Int, aspect: Float): HoistedBlock {
        val safeAspect = if (aspect > 0.05f) aspect else 1f
        val width = StageMetrics.BLOCK_WIDTH * Catalogue.blockStyle(styleId).stageScale
        return HoistedBlock(
            styleId = styleId,
            width = width,
            height = width / safeAspect,
        )
    }

    private fun advanceHoist(dt: Float) {
        swingPhase += dt / swingSeconds
        if (swingPhase > 1f) swingPhase -= swingPhase.toInt().toFloat()

        val liftStep = dt / StageMetrics.REEL_SECONDS
        hookLift = if (hookLift < hookLiftTarget) {
            min(hookLift + liftStep, hookLiftTarget)
        } else {
            maxOf(hookLift - liftStep, hookLiftTarget)
        }

        // The reference rig travels on a trolley: hook, straps and block move
        // horizontally together instead of describing a long pendulum arc.
        val swingScale = 1f - smoothStep(hookLift)
        val wave = sin(swingPhase * 2f * PI.toFloat()) * swingScale
        hoistX = StageMetrics.MAX_SWAY_X * wave
        swingAngle = StageMetrics.MAX_HOOK_TILT_RAD * wave

        val block = hoisted ?: return
        val distance = blockDistance(block.height)
        block.centerX = alongRopeX(distance)
        block.centerY = alongRopeY(distance)
        // A block on straps hangs level however far the rope leans.
        block.lean = 0f
    }

    /** Rope paid out from the pivot down to the middle of the pulley. */
    fun ropeLength(): Float =
        StageMetrics.HOOK_RADIUS * (1f - StageMetrics.REEL_TAKEUP * hookLift)

    /** Distance from the pivot to the tip of the hook. */
    fun hookTipDistance(): Float = ropeLength() + StageMetrics.HOOK_HEIGHT / 2f

    /** Distance from the pivot to the middle of a block of [blockHeight] on the straps. */
    fun blockDistance(blockHeight: Float): Float =
        hookTipDistance() + StageMetrics.STRAP_DROP + blockHeight / 2f

    /** All points of the trolley rig share one X, so its cable stays vertical. */
    fun alongRopeX(distance: Float): Float = hoistX + distance * 0f

    fun alongRopeY(distance: Float): Float = pivotY() + distance

    private fun releaseHoisted(holds: Boolean) {
        val block = hoisted ?: return
        hoisted = null
        hookLiftTarget = 1f

        val restY = stackTopY - block.height / 2f
        val jitter = (random.nextFloat() * 2f - 1f) * StageMetrics.PLACE_JITTER
        val previousX = settled.lastOrNull()?.centerX ?: 0f
        val targetLean = (random.nextFloat() * 2f - 1f) * StageMetrics.TILT_JITTER_RAD
        val pulled = previousX * (1f - StageMetrics.AXIS_PULL)
        val targetX = (pulled + jitter).coerceIn(-StageMetrics.MAX_DRIFT, StageMetrics.MAX_DRIFT)
        // A block let go on the outswing keeps travelling that way when it misses.
        val leanSign = if (block.centerX >= 0f) 1f else -1f

        airborne = AirborneBlock(
            styleId = block.styleId,
            width = block.width,
            height = block.height,
            holds = holds,
            targetX = targetX,
            targetLean = targetLean,
            restY = restY,
            centerX = block.centerX,
            centerY = block.centerY,
            lean = block.lean,
            driftX = if (holds) 0f else StageMetrics.MISS_DRIFT * leanSign,
            fallSpeed = StageMetrics.DROP_START_VY,
            spin = if (holds) 0f else StageMetrics.MISS_SPIN * leanSign,
        )
    }

    private fun advanceAirborne(dt: Float) {
        val block = airborne ?: return
        block.fallSpeed += StageMetrics.GRAVITY * dt
        block.centerY += block.fallSpeed * dt

        if (block.holds) {
            val approach = min(1f, dt * 6.5f)
            block.centerX += (block.targetX - block.centerX) * approach
            block.lean += (block.targetLean - block.lean) * approach
            if (block.centerY >= block.restY) {
                settleAirborne(block)
                return
            }
        } else {
            block.centerX += block.driftX * dt
            block.lean += block.spin * dt
            if (!block.reported && block.centerY > block.restY + StageMetrics.MISS_TRIGGER * block.height) {
                block.reported = true
                shake = maxOf(shake, StageMetrics.SHAKE_MISS)
                onEvent?.invoke(StageEvent.Missed)
            }
            if (block.centerY > cameraY + viewportHalfHeight + 16f) {
                airborne = null
            }
        }
    }

    private fun settleAirborne(block: AirborneBlock) {
        airborne = null
        settled += SettledBlock(
            styleId = block.styleId,
            centerX = block.targetX,
            centerY = block.restY,
            width = block.width,
            height = block.height,
            lean = block.targetLean,
            wobble = StageMetrics.WOBBLE_START,
            wobblePhase = 0f,
        )
        shake = maxOf(shake, StageMetrics.SHAKE_LAND)
        spawnDust(block.targetX, block.restY + block.height / 2f)
        spawnSparks(block.targetX, block.restY)
        onEvent?.invoke(StageEvent.Landed(settled.size))
    }

    private fun advanceSettled(dt: Float) {
        for (block in settled) {
            if (block.wobble <= 0.0005f) continue
            block.wobblePhase += dt * StageMetrics.WOBBLE_SPEED
            block.wobble *= exp(-StageMetrics.WOBBLE_DECAY * dt)
        }
    }

    private fun advanceCamera(dt: Float) {
        val target = cameraTarget()
        val blend = 1f - exp(-StageMetrics.CAMERA_SMOOTH * dt)
        cameraY += (target - cameraY) * blend
        if (abs(target - cameraY) < 0.001f) cameraY = target
    }

    private fun cameraTarget(): Float = min(stackTopY + StageMetrics.CAMERA_LEAD, restingCameraY())

    /**
     * Keeps the pavement just above the control deck while the stack is short, so
     * the shop front is never hidden behind the buttons.
     */
    private fun restingCameraY(): Float =
        StageMetrics.GROUND_Y + StageMetrics.STREET_BELOW + deckHeight() - viewportHalfHeight

    private fun deckHeight(): Float = viewportHalfHeight * 2f * StageMetrics.DECK_FRACTION

    fun pivotY(): Float = cameraY - StageMetrics.PIVOT_ABOVE

    fun pivotX(): Float = hoistX

    private fun spawnDust(x: Float, y: Float) {
        repeat(StageMetrics.DUST_COUNT) {
            val spread = (random.nextFloat() * 2f - 1f) * StageMetrics.BLOCK_WIDTH * 0.55f
            flecks += Fleck(
                kind = FleckKind.DUST,
                x = x + spread,
                y = y - random.nextFloat() * 0.3f,
                vx = spread * 1.6f,
                vy = -random.nextFloat() * 2.2f,
                radius = 0.18f + random.nextFloat() * 0.3f,
                life = 0.55f + random.nextFloat() * 0.4f,
                maxLife = 0.95f,
                spinSeed = random.nextFloat(),
            )
        }
    }

    private fun spawnSparks(x: Float, y: Float) {
        repeat(StageMetrics.SPARK_COUNT) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = 2.5f + random.nextFloat() * 4.5f
            flecks += Fleck(
                kind = FleckKind.SPARK,
                x = x + (random.nextFloat() * 2f - 1f) * 1.2f,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 1.5f,
                radius = 0.07f + random.nextFloat() * 0.09f,
                life = 0.35f + random.nextFloat() * 0.35f,
                maxLife = 0.7f,
                spinSeed = random.nextFloat(),
            )
        }
    }

    private fun spawnCoins() {
        val top = stackTopY
        repeat(StageMetrics.COIN_COUNT) {
            flecks += Fleck(
                kind = FleckKind.COIN,
                x = (random.nextFloat() * 2f - 1f) * visibleHalfWidth * 0.75f,
                y = top + random.nextFloat() * 3f,
                vx = (random.nextFloat() * 2f - 1f) * 3.5f,
                vy = -6f - random.nextFloat() * 7f,
                radius = 0.22f + random.nextFloat() * 0.16f,
                life = 1.1f + random.nextFloat() * 0.6f,
                maxLife = 1.7f,
                spinSeed = random.nextFloat(),
            )
        }
    }

    private fun advanceFlecks(dt: Float) {
        var index = 0
        while (index < flecks.size) {
            val fleck = flecks[index]
            fleck.life -= dt
            if (fleck.life <= 0f) {
                flecks.removeAt(index)
                continue
            }
            val pull = when (fleck.kind) {
                FleckKind.DUST -> 2.5f
                FleckKind.SPARK -> 14f
                FleckKind.COIN -> 20f
            }
            fleck.vy += pull * dt
            fleck.x += fleck.vx * dt
            fleck.y += fleck.vy * dt
            fleck.vx *= 1f - 1.4f * dt
            index++
        }
    }

    private fun seedPuffs() {
        repeat(StageMetrics.CLOUD_COUNT) { index ->
            puffs += Puff(
                x = (random.nextFloat() * 2f - 1f) * visibleHalfWidth * 1.4f,
                y = cameraY - viewportHalfHeight * random.nextFloat() * 1.6f,
                scale = 0.55f + random.nextFloat() * 0.9f,
                speed = 0.25f + random.nextFloat() * 0.7f,
                spriteIndex = index % 2,
                alpha = 0.45f + random.nextFloat() * 0.45f,
            )
        }
    }

    private fun advancePuffs(dt: Float) {
        val limitX = visibleHalfWidth * 1.6f
        val topEdge = cameraY - viewportHalfHeight - 6f
        val bottomEdge = cameraY + viewportHalfHeight + 6f
        for (puff in puffs) {
            puff.x += puff.speed * dt
            if (puff.x > limitX) puff.x = -limitX
            // Recycle vertically so the sky keeps its cover while the stack climbs.
            if (puff.y > bottomEdge) {
                puff.y = topEdge
                puff.x = (random.nextFloat() * 2f - 1f) * limitX
            } else if (puff.y < topEdge) {
                puff.y = bottomEdge
                puff.x = (random.nextFloat() * 2f - 1f) * limitX
            }
        }
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    // UNIQUE:AST_DECOYS:BEGIN
    private fun ripenWhej(sample: Long): Long =
        (sample xor 0xE99FFC05L) shr 7

    private fun trickleNvar(sample: Long): Long =
        (sample xor 0x3DD44932L) shr 7
    // UNIQUE:AST_DECOYS:END
}
