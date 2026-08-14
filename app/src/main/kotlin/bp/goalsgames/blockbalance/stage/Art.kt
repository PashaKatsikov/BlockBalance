package bp.goalsgames.blockbalance.stage

import bp.goalsgames.blockbalance.meta.Catalogue

/** Asset paths shipped with the app, grouped by where they are used. */
object Art {

    const val SKY = "gameplay/backround.webp"
    const val STREET = "gameplay/start_bg_asset_same_visual.webp"
    const val HOOK = "gameplay/hook_asset.webp"
    const val CLOUD_SOFT = "gameplay/cloud_asset_01d.webp"
    const val CLOUD_WISP = "gameplay/cloud_asset_02.webp"

    const val STACK_BASE = "derived/stack_base.png"
    const val LOGO = "derived/logo_wordmark.png"
    const val PLATE = "derived/plate_blank.png"

    /** Hazard plate that already carries the BUILD wording. */
    const val BUILD_PLATE = "gameplay/button_asset.webp"

    const val MENU_BACKDROP = "ui/menu_bg.png"

    val cloudSprites = listOf(CLOUD_SOFT, CLOUD_WISP)

    /** Everything the yard renderer needs before the first frame. */
    fun stageAssets(): List<String> = buildList {
        add(SKY)
        add(STREET)
        add(HOOK)
        addAll(cloudSprites)
        add(STACK_BASE)
        Catalogue.blockStyles.forEach { add(it.asset) }
    }

    /** Menu and overlay art, loaded together with the yard on the boot screen. */
    fun shellAssets(): List<String> = listOf(MENU_BACKDROP, LOGO, PLATE, BUILD_PLATE)
}
