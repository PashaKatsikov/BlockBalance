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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import bp.goalsgames.blockbalance.R
import bp.goalsgames.blockbalance.legal.LegalEndpoints
import bp.goalsgames.blockbalance.ui.components.GhostButton
import bp.goalsgames.blockbalance.ui.components.ScreenHeader
import bp.goalsgames.blockbalance.ui.theme.Yard

/**
 * Shows the hosted privacy policy. The bundled copy is used when the public
 * page is unreachable, so the button still works offline.
 */
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remote = LegalEndpoints.PRIVACY_URL
    val local = LegalEndpoints.localUrl()

    val client = remember {
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
                title = stringResource(R.string.legal_privacy_title).uppercase(),
                onBack = onBack,
                trailing = {
                    GhostButton(
                        label = stringResource(R.string.legal_open_browser),
                        onClick = { openExternally(context, remote) },
                    )
                },
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    webViewClient = client
                    loadUrl(remote)
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
