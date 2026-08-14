package bp.goalsgames.blockbalance.meta

/** A purchasable block sprite. */
data class BlockStyle(
    val id: Int,
    val title: String,
    val asset: String,
    val price: Long,
    /** Rank that gifts the style for free; 0 means it is never gifted. */
    val giftRank: Int,
    /** Corrects for unusually wide art so every floor has comparable visual mass. */
    val stageScale: Float = 1f,
)

/**
 * A sky preset. The gameplay backdrop is one painted gradient, so moods are
 * produced by grading it with a translucent vertical wash.
 */
data class SkyMood(
    val id: Int,
    val title: String,
    val price: Long,
    val requiredRank: Int,
    val washTop: Int,
    val washBottom: Int,
    val washAlpha: Float,
    val cloudAlpha: Float,
    val groundDim: Float,
)

object Catalogue {

    val blockStyles = listOf(
        BlockStyle(
            id = 1,
            title = "Sunny Flat",
            asset = "gameplay/block_asset_02.webp",
            price = 0L,
            giftRank = 0,
        ),
        BlockStyle(
            id = 2,
            title = "Timber Lodge",
            asset = "gameplay/block_asset_03.webp",
            price = 800L,
            giftRank = 2,
        ),
        BlockStyle(
            id = 3,
            title = "Brick Loft",
            asset = "gameplay/block_asset_04.webp",
            price = 1_500L,
            giftRank = 4,
        ),
        BlockStyle(
            id = 4,
            title = "Hex Cabin",
            asset = "gameplay/block_asset_06.webp",
            price = 2_500L,
            giftRank = 7,
            stageScale = 1.33f,
        ),
    )

    val skyMoods = listOf(
        SkyMood(
            id = 1,
            title = "Daylight",
            price = 0L,
            requiredRank = 0,
            washTop = 0x00000000,
            washBottom = 0x00000000,
            washAlpha = 0f,
            cloudAlpha = 0.85f,
            groundDim = 0f,
        ),
        SkyMood(
            id = 2,
            title = "Sunset",
            price = 1_500L,
            requiredRank = 0,
            washTop = 0xFF4B2C74.toInt(),
            washBottom = 0xFFFF9A3C.toInt(),
            washAlpha = 0.55f,
            cloudAlpha = 0.75f,
            groundDim = 0.12f,
        ),
        SkyMood(
            id = 3,
            title = "Dusk",
            price = 3_000L,
            requiredRank = 3,
            washTop = 0xFF141A44.toInt(),
            washBottom = 0xFF6E4E8F.toInt(),
            washAlpha = 0.68f,
            cloudAlpha = 0.55f,
            groundDim = 0.28f,
        ),
        SkyMood(
            id = 4,
            title = "Midnight",
            price = 6_000L,
            requiredRank = 6,
            washTop = 0xFF05060F.toInt(),
            washBottom = 0xFF1B2A5C.toInt(),
            washAlpha = 0.80f,
            cloudAlpha = 0.35f,
            groundDim = 0.42f,
        ),
    )

    val defaultBlockStyle: BlockStyle get() = blockStyles.first()
    val defaultSkyMood: SkyMood get() = skyMoods.first()

    fun blockStyle(id: Int): BlockStyle = blockStyles.firstOrNull { it.id == id } ?: defaultBlockStyle

    fun skyMood(id: Int): SkyMood = skyMoods.firstOrNull { it.id == id } ?: defaultSkyMood

    /** Style ids that a player of [rank] owns for free. */
    fun giftedStyles(rank: Int): Set<Int> =
        blockStyles.filter { it.giftRank in 1..rank }.map { it.id }.toSet()
}
