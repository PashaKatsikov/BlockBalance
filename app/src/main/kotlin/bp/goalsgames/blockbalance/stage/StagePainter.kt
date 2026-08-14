package bp.goalsgames.blockbalance.stage

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import bp.goalsgames.blockbalance.meta.Catalogue
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a frame of [StageScene] onto a canvas. It owns no state beyond reusable
 * paints and rectangles, so it is safe to keep one instance per surface.
 */
class StagePainter {

    private companion object {
        /** The lower part of the source contains only the pulley and hook. */
        const val HOOK_CABLE_SHARE = 0.52f
    }

    private val spritePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply { isAntiAlias = true }
    private val washPaint = Paint().apply { isAntiAlias = true }
    private val cablePaint = Paint().apply {
        isAntiAlias = true
        color = 0xFF2B2F3A.toInt()
        strokeWidth = 3f
    }
    private val fleckPaint = Paint().apply { isAntiAlias = true }

    private val src = Rect()
    private val dst = RectF()

    private var washShader: LinearGradient? = null
    private var washKey = 0

    var pixelsPerUnit = 60f
        private set

    private var halfWidthPx = 0f
    private var halfHeightPx = 0f

    /** Recomputes the projection; returns the visible half-extents in world units. */
    fun measure(widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val byWidth = widthPx / StageMetrics.WORLD_WIDTH
        val byHeight = heightPx / StageMetrics.MIN_WORLD_HEIGHT
        pixelsPerUnit = max(1f, min(byWidth, byHeight))
        halfWidthPx = widthPx / 2f
        halfHeightPx = heightPx / 2f
        cablePaint.strokeWidth = max(2f, pixelsPerUnit * 0.055f)
        return (halfWidthPx / pixelsPerUnit) to (halfHeightPx / pixelsPerUnit)
    }

    fun draw(canvas: Canvas, scene: StageScene) {
        val width = halfWidthPx * 2f
        val height = halfHeightPx * 2f
        if (width <= 0f || height <= 0f) return

        val (shakeX, shakeY) = scene.shakeOffset()
        canvas.withTranslation(shakeX * pixelsPerUnit, shakeY * pixelsPerUnit) {
            drawSky(canvas, scene, width, height)
            drawPuffs(canvas, scene)
            drawGround(canvas, scene, width, height)
            drawBase(canvas, scene)
            drawSettled(canvas, scene)
            drawHoist(canvas, scene)
            drawAirborne(canvas, scene)
            drawFlecks(canvas, scene)
        }
    }

    private fun drawSky(canvas: Canvas, scene: StageScene, width: Float, height: Float) {
        val sky = SpriteBank.bitmap(Art.SKY)
        if (sky != null) {
            src.set(0, 0, sky.width, sky.height)
            dst.set(0f, 0f, width, height)
            spritePaint.alpha = 255
            canvas.drawBitmap(sky, src, dst, spritePaint)
        } else {
            fillPaint.shader = null
            fillPaint.color = 0xFF62B4EE.toInt()
            canvas.drawRect(0f, 0f, width, height, fillPaint)
        }

        val mood = scene.sky
        if (mood.washAlpha <= 0.001f) return
        val key = mood.id * 31 + height.toInt()
        if (washShader == null || washKey != key) {
            washShader = LinearGradient(
                0f, 0f, 0f, height,
                mood.washTop, mood.washBottom,
                Shader.TileMode.CLAMP,
            )
            washKey = key
        }
        washPaint.shader = washShader
        washPaint.alpha = (mood.washAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width, height, washPaint)
    }

    private fun drawPuffs(canvas: Canvas, scene: StageScene) {
        val mood = scene.sky
        for (puff in scene.puffs) {
            val sprite = SpriteBank.bitmap(Art.cloudSprites[puff.spriteIndex % Art.cloudSprites.size]) ?: continue
            val widthUnits = 5f * puff.scale
            val heightUnits = widthUnits / max(0.05f, SpriteBank.aspect(Art.cloudSprites[puff.spriteIndex % Art.cloudSprites.size]))
            placeRect(scene, puff.x, puff.y, widthUnits, heightUnits)
            spritePaint.alpha = (puff.alpha * mood.cloudAlpha * 255f).toInt().coerceIn(0, 255)
            src.set(0, 0, sprite.width, sprite.height)
            canvas.drawBitmap(sprite, src, dst, spritePaint)
        }
        spritePaint.alpha = 255
    }

    private fun drawGround(canvas: Canvas, scene: StageScene, width: Float, height: Float) {
        val street = SpriteBank.bitmap(Art.STREET)
        // The strip has to reach both edges, however wide the window turns out.
        val streetWidth = max(StageMetrics.WORLD_WIDTH, width / pixelsPerUnit) * 1.02f
        val aspect = if (street != null) SpriteBank.aspect(Art.STREET) else 2.6f
        val streetHeight = streetWidth / max(0.05f, aspect)
        val bottomY = StageMetrics.GROUND_Y + StageMetrics.STREET_BELOW
        val centerY = bottomY - streetHeight / 2f

        val fillTop = screenY(scene, bottomY)
        if (fillTop < height) {
            fillPaint.shader = null
            fillPaint.color = tint(0xFF3B3F4B.toInt(), scene.sky.groundDim)
            canvas.drawRect(0f, fillTop, width, height, fillPaint)
        }

        if (street != null) {
            placeRect(scene, 0f, centerY, streetWidth, streetHeight)
            spritePaint.alpha = 255
            src.set(0, 0, street.width, street.height)
            canvas.drawBitmap(street, src, dst, spritePaint)
        }

        if (scene.sky.groundDim > 0.001f) {
            fillPaint.shader = null
            fillPaint.color = Color.argb((scene.sky.groundDim * 190f).toInt().coerceIn(0, 255), 8, 10, 26)
            val top = screenY(scene, centerY - streetHeight / 2f)
            canvas.drawRect(0f, top, width, height, fillPaint)
        }
    }

    private fun drawBase(canvas: Canvas, scene: StageScene) {
        val base = SpriteBank.bitmap(Art.STACK_BASE) ?: return
        val height = scene.baseHeight
        val centerY = StageMetrics.GROUND_Y - height / 2f
        placeRect(scene, 0f, centerY, StageMetrics.BASE_WIDTH, height)
        spritePaint.alpha = 255
        src.set(0, 0, base.width, base.height)
        canvas.drawBitmap(base, src, dst, spritePaint)
    }

    private fun drawSettled(canvas: Canvas, scene: StageScene) {
        for (block in scene.settled) {
            val lean = block.lean + kotlin.math.sin(block.wobblePhase) * block.wobble
            drawBlock(canvas, scene, block.styleId, block.centerX, block.centerY, block.width, block.height, lean)
        }
    }

    /** One vertical cable, a compact pulley and two straps on the moving trolley. */
    private fun drawHoist(canvas: Canvas, scene: StageScene) {
        val pivotX = screenX(scene.pivotX())
        val pivotY = screenY(scene, scene.pivotY())
        val hookCenterX = screenX(scene.alongRopeX(scene.ropeLength()))
        val hookCenterY = screenY(scene, scene.alongRopeY(scene.ropeLength()))
        val hookWidthPx = StageMetrics.HOOK_WIDTH * pixelsPerUnit
        val hookHeightPx = StageMetrics.HOOK_HEIGHT * pixelsPerUnit
        val degrees = Math.toDegrees(scene.swingAngle.toDouble()).toFloat()

        // The source art contains two long cable strips. Drawing those and another
        // pendulum line produced three ropes, so only its pulley/hook slice is used.
        canvas.drawLine(pivotX, pivotY, hookCenterX, hookCenterY, cablePaint)
        val hook = SpriteBank.bitmap(Art.HOOK)
        if (hook != null) {
            val split = (hook.height * HOOK_CABLE_SHARE).toInt().coerceIn(1, hook.height - 1)
            canvas.withRotation(degrees, hookCenterX, hookCenterY) {
                src.set(0, split, hook.width, hook.height)
                dst.set(
                    hookCenterX - hookWidthPx / 2f,
                    hookCenterY - hookHeightPx / 2f,
                    hookCenterX + hookWidthPx / 2f,
                    hookCenterY + hookHeightPx / 2f,
                )
                spritePaint.alpha = 255
                canvas.drawBitmap(hook, src, dst, spritePaint)
            }
        }

        val block = scene.hoisted ?: return
        drawStraps(canvas, scene, block)
        drawBlock(canvas, scene, block.styleId, block.centerX, block.centerY, block.width, block.height, 0f)
    }

    /** Two short ropes from the hook tip to the upper corners of the block. */
    private fun drawStraps(canvas: Canvas, scene: StageScene, block: HoistedBlock) {
        val tipDistance = scene.hookTipDistance()
        val tipX = screenX(scene.alongRopeX(tipDistance))
        val tipY = screenY(scene, scene.alongRopeY(tipDistance))
        val corner = block.width * 0.32f * pixelsPerUnit
        val topY = screenY(scene, block.centerY - block.height / 2f)
        val centerX = screenX(block.centerX)
        canvas.drawLine(tipX, tipY, centerX - corner, topY, cablePaint)
        canvas.drawLine(tipX, tipY, centerX + corner, topY, cablePaint)
    }

    private fun drawAirborne(canvas: Canvas, scene: StageScene) {
        val block = scene.airborne ?: return
        drawBlock(canvas, scene, block.styleId, block.centerX, block.centerY, block.width, block.height, block.lean)
    }

    private fun drawBlock(
        canvas: Canvas,
        scene: StageScene,
        styleId: Int,
        centerX: Float,
        centerY: Float,
        widthUnits: Float,
        heightUnits: Float,
        lean: Float,
    ) {
        val asset = Catalogue.blockStyle(styleId).asset
        val sprite = SpriteBank.bitmap(asset)
        placeRect(scene, centerX, centerY, widthUnits, heightUnits)
        val pivotX = dst.centerX()
        val pivotY = dst.centerY()
        canvas.withRotation(Math.toDegrees(lean.toDouble()).toFloat(), pivotX, pivotY) {
            if (sprite != null) {
                spritePaint.alpha = 255
                src.set(0, 0, sprite.width, sprite.height)
                canvas.drawBitmap(sprite, src, dst, spritePaint)
            } else {
                fillPaint.shader = null
                fillPaint.color = 0xFFB98A4A.toInt()
                canvas.drawRoundRect(dst, 8f, 8f, fillPaint)
            }
        }
    }

    private fun drawFlecks(canvas: Canvas, scene: StageScene) {
        for (fleck in scene.flecks) {
            val radius = fleck.radius * pixelsPerUnit
            val x = screenX(fleck.x)
            val y = screenY(scene, fleck.y)
            when (fleck.kind) {
                FleckKind.DUST -> {
                    fleckPaint.color = Color.argb((fleck.fade * 120f).toInt().coerceIn(0, 255), 226, 222, 210)
                }

                FleckKind.SPARK -> {
                    fleckPaint.color = Color.argb((fleck.fade * 235f).toInt().coerceIn(0, 255), 255, 214, 102)
                }

                FleckKind.COIN -> {
                    fleckPaint.color = Color.argb((fleck.fade * 245f).toInt().coerceIn(0, 255), 255, 189, 46)
                }
            }
            canvas.drawCircle(x, y, radius, fleckPaint)
        }
    }

    private fun placeRect(scene: StageScene, centerX: Float, centerY: Float, widthUnits: Float, heightUnits: Float) {
        val halfW = widthUnits * pixelsPerUnit / 2f
        val halfH = heightUnits * pixelsPerUnit / 2f
        val cx = screenX(centerX)
        val cy = screenY(scene, centerY)
        dst.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    private fun screenX(worldX: Float): Float = halfWidthPx + worldX * pixelsPerUnit

    private fun screenY(scene: StageScene, worldY: Float): Float =
        halfHeightPx + (worldY - scene.cameraY) * pixelsPerUnit

    private fun tint(color: Int, dim: Float): Int {
        if (dim <= 0f) return color
        val factor = (1f - dim.coerceIn(0f, 1f))
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt(),
        )
    }
}
