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
 * Both pages ship inside the APK so the buttons work offline and before any
 * domain is live. Once the public pages are published, drop the addresses into
 * [PRIVACY_URL] and [SUPPORT_URL]; the screens will then prefer the live copy
 * and keep the bundled page as the offline fallback.
 */
object LegalEndpoints {

    val PRIVACY_URL: String? = null
    val SUPPORT_URL: String? = null

    fun urlFor(page: LegalPage): String? = when (page) {
        LegalPage.PRIVACY -> PRIVACY_URL
        LegalPage.SUPPORT -> SUPPORT_URL
    }

    fun localUrlFor(page: LegalPage): String = "file:///android_asset/${page.localAsset}"
}
