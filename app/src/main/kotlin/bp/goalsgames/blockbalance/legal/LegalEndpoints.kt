package bp.goalsgames.blockbalance.legal

enum class LegalPage(
    /** Page bundled with the app, always available. */
    val localAsset: String,
) {
    PRIVACY(localAsset = "legal/privacy-policy.html"),
    SUPPORT(localAsset = "legal/support.html"),
}

/**
 * Where the policy and support pages live.
 *
 * Live pages are preferred; the bundled copies stay as the offline fallback.
 */
object LegalEndpoints {

    val PRIVACY_URL: String? = "https://citadelclash.com/privacy-policy.html"
    val SUPPORT_URL: String? = "https://citadelclash.com/support.html"

    fun urlFor(page: LegalPage): String? = when (page) {
        LegalPage.PRIVACY -> PRIVACY_URL
        LegalPage.SUPPORT -> SUPPORT_URL
    }

    fun localUrlFor(page: LegalPage): String = "file:///android_asset/${page.localAsset}"
}
