package bp.goalsgames.blockbalance.legal

/**
 * The privacy page is hosted from the public
 * [PrivacyPolicy_BlockBalance](https://github.com/PashaKatsikov/PrivacyPolicy_BlockBalance)
 * repo via GitHub Pages. The bundled copy is the offline fallback.
 */
object LegalEndpoints {

    const val LOCAL_ASSET = "legal/privacy-policy.html"

    val PRIVACY_URL: String =
        "https://pashakatsikov.github.io/PrivacyPolicy_BlockBalance/"

    fun localUrl(): String = "file:///android_asset/$LOCAL_ASSET"
}
