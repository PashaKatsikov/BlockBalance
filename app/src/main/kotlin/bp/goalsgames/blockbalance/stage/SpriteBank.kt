package bp.goalsgames.blockbalance.stage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import bp.goalsgames.blockbalance.meta.Catalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes art once and keeps it for the life of the process. Only bitmaps are
 * held here, never a context, so the cache cannot leak an activity.
 *
 * Every sprite is downsampled to roughly the size it is actually drawn at, which
 * keeps the whole pack well inside a phone's bitmap budget.
 */
object SpriteBank {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    @Volatile
    var loaded: Boolean = false
        private set

    fun bitmap(path: String): Bitmap? = cache[path]

    /** Width divided by height, or 1 when the sprite is not loaded yet. */
    fun aspect(path: String): Float {
        val bitmap = cache[path] ?: return 1f
        if (bitmap.height <= 0) return 1f
        return bitmap.width.toFloat() / bitmap.height.toFloat()
    }

    suspend fun preload(context: Context, screenWidthPx: Int, screenHeightPx: Int) {
        withContext(Dispatchers.IO) {
            val shortEdge = max(1, minOf(screenWidthPx, screenHeightPx))
            val longEdge = max(1, maxOf(screenWidthPx, screenHeightPx))

            val plan = buildList {
                add(Art.SKY to longEdge)
                add(Art.STREET to longEdge)
                add(Art.HOOK to (shortEdge * 0.22f).roundToInt())
                Art.cloudSprites.forEach { add(it to (shortEdge * 0.7f).roundToInt()) }
                add(Art.STACK_BASE to (shortEdge * 0.62f).roundToInt())
                Catalogue.blockStyles.forEach { add(it.asset to (shortEdge * 0.45f).roundToInt()) }
                add(Art.MENU_BACKDROP to longEdge)
                add(Art.LOGO to (shortEdge * 0.9f).roundToInt())
                add(Art.PLATE to (shortEdge * 0.8f).roundToInt())
                add(Art.BUILD_PLATE to (shortEdge * 0.8f).roundToInt())
            }

            for ((path, targetEdge) in plan) {
                if (cache.containsKey(path)) continue
                decode(context, path, targetEdge)?.let { cache[path] = it }
            }
            loaded = true
        }
    }

    /** Decodes on demand; used by screens that show art the boot step skipped. */
    suspend fun ensure(context: Context, path: String, targetEdgePx: Int): Bitmap? {
        cache[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            decode(context, path, targetEdgePx)?.also { cache[path] = it }
        }
    }

    private fun decode(context: Context, path: String, targetEdgePx: Int): Bitmap? {
        val assets = context.applicationContext.assets
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            val sourceEdge = max(bounds.outWidth, bounds.outHeight)
            val target = max(64, targetEdgePx)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(sourceEdge, target)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (error: IOException) {
            null
        } catch (error: OutOfMemoryError) {
            null
        }
    }

    private fun sampleSizeFor(sourceEdge: Int, targetEdge: Int): Int {
        if (sourceEdge <= 0) return 1
        var sample = 1
        while (sourceEdge / (sample * 2) >= targetEdge) sample *= 2
        return sample
    }
}
