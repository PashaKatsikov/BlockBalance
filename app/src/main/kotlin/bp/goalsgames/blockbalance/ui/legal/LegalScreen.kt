package bp.goalsgames.blockbalance.ui.legal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.legal.LegalEndpoints
import bp.goalsgames.blockbalance.legal.LegalPage
import bp.goalsgames.blockbalance.ui.components.GhostButton
import bp.goalsgames.blockbalance.ui.components.ScreenHeader
import bp.goalsgames.blockbalance.ui.theme.Yard

/**
 * Shows the policy or support page. The bundled copy is used whenever a public
 * address is missing or unreachable, so the buttons work offline and before any
 * domain is live.
 */
@Composable
fun LegalScreen(
    page: LegalPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remote = LegalEndpoints.urlFor(page)
    val local = LegalEndpoints.localUrlFor(page)
    val title = when (page) {
        LegalPage.PRIVACY -> stringResource(R.string.legal_privacy_title)
        LegalPage.SUPPORT -> stringResource(R.string.legal_support_title)
    }

    val client = remember(page) {
        object : WebViewClient() {
            private var fellBack = false

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (fellBack || !request.isForMainFrame) return
                fellBack = true
                view.loadUrl(local)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url ?: return false
                if (target.scheme == "file") return false
                return try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, target))
                    true
                } catch (error: ActivityNotFoundException) {
                    false
                }
            }
        }
    }

    Column(modifier = modifier.background(Yard.night)) {
        Box(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(
                title = title.uppercase(),
                onBack = onBack,
                trailing = {
                    if (remote != null) {
                        GhostButton(
                            label = stringResource(R.string.legal_open_browser),
                            onClick = { openExternally(context, remote) },
                        )
                    }
                },
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .background(if (page == LegalPage.PRIVACY) Color.White else Yard.night),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    // Privacy is a light page: a transparent WebView over the
                    // night chrome makes the body text unreadable.
                    setBackgroundColor(
                        if (page == LegalPage.PRIVACY) android.graphics.Color.WHITE
                        else android.graphics.Color.TRANSPARENT,
                    )
                    settings.javaScriptEnabled = remote != null
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = remote != null
                    webViewClient = client
                    loadUrl(remote ?: local)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
        )
    }
}

private fun openExternally(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (error: ActivityNotFoundException) {
        // No browser installed; the in-app page is already showing the content.
    }
}
