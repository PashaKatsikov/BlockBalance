package bp.goalsgames.blockbalance.stage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Runs the yard on its own thread and blits it through a [SurfaceHolder].
 *
 * The loop is deliberately dumb: advance the scene by the measured frame time,
 * draw, repeat. Everything that can change the outcome of a run happens in the
 * simulation and the round rules, never here.
 *
 * The painter and the scene are touched by the render thread only. Size changes
 * arrive on the main thread and are handed over as plain values, so no scene
 * collection is ever written while the loop walks it.
 */
class StageSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private companion object {
        const val TAG = "StageSurface"
        const val FRAME_NANOS = 1_000_000_000L / 60L
    }

    private val painter = StagePainter()
    private var loop: RenderLoop? = null

    @Volatile
    private var pendingWidth = 0

    @Volatile
    private var pendingHeight = 0

    @Volatile
    private var viewportStale = true

    var scene: StageScene? = null
        set(value) {
            if (field === value) return
            field = value
            viewportStale = true
        }

    /** Invoked on the main thread when the player taps the yard. */
    var onTap: (() -> Unit)? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Some devices hand out a fresh surface without destroying the old one.
        this.loop?.finish()
        val loop = RenderLoop(holder)
        this.loop = loop
        loop.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        pendingWidth = width
        pendingHeight = height
        viewportStale = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        loop?.finish()
        loop = null
    }

    fun pauseRendering() {
        loop?.paused = true
    }

    fun resumeRendering() {
        loop?.paused = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onTap?.invoke()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun applyViewport(scene: StageScene, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val (halfWidth, halfHeight) = painter.measure(width, height)
        scene.updateViewport(halfWidth, halfHeight)
    }

    private inner class RenderLoop(private val holder: SurfaceHolder) : Thread("block-balance-stage") {

        @Volatile
        private var active = true

        @Volatile
        var paused = false

        private var lastFrameNanos = 0L

        fun finish() {
            active = false
            interrupt()
            try {
                join(750)
            } catch (error: InterruptedException) {
                currentThread().interrupt()
            }
        }

        override fun run() {
            lastFrameNanos = System.nanoTime()
            while (active) {
                if (paused) {
                    lastFrameNanos = System.nanoTime()
                    sleepQuietly(60)
                    continue
                }
                val scene = scene
                if (scene == null) {
                    sleepQuietly(16)
                    continue
                }
                if (viewportStale) {
                    viewportStale = false
                    applyViewport(scene, pendingWidth, pendingHeight)
                }

                val now = System.nanoTime()
                val delta = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
                lastFrameNanos = now

                var canvas: Canvas? = null
                try {
                    scene.advance(delta)
                    canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.lockHardwareCanvas()
                    } else {
                        holder.lockCanvas()
                    }
                    if (canvas != null) painter.draw(canvas, scene)
                } catch (error: IllegalArgumentException) {
                    // Surface went away mid-frame; the callback will stop the loop.
                } catch (error: Throwable) {
                    // A broken frame must never take the whole game down: park the
                    // loop and leave the last good picture on screen.
                    Log.e(TAG, "stage frame failed", error)
                    active = false
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (error: IllegalStateException) {
                            active = false
                        }
                    }
                }
                paceFrame(now)
            }
        }

        /** Prevents the software loop from drawing hundreds of identical frames. */
        private fun paceFrame(frameStartedNanos: Long) {
            val remaining = FRAME_NANOS - (System.nanoTime() - frameStartedNanos)
            if (remaining <= 0L) return
            try {
                sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
            } catch (error: InterruptedException) {
                currentThread().interrupt()
                active = false
            }
        }

        private fun sleepQuietly(millis: Long) {
            try {
                sleep(millis)
            } catch (error: InterruptedException) {
                currentThread().interrupt()
                active = false
            }
        }
    }
}
