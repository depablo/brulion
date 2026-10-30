package pl.brulion.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import pl.brulion.R
import pl.brulion.librus.Librus
import pl.brulion.librus.LibrusException
import pl.brulion.ui.common.toUiError

/**
 * Shows the real Librus portal login page, so captcha and 2FA are handled by Librus itself.
 * The page finally redirects to app://librus?code=..., which we intercept and exchange for tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalLoginScreen(viewModel: LoginViewModel, onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val state = viewModel.state
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is LoginViewModel.State.Done && state.needingAction.isEmpty()) onLoggedIn()
    }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.startPortalLogin() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.portal_restart))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        configure(
                            onRedirect = { uri ->
                                val code = uri.getQueryParameter("code")
                                when {
                                    code != null -> viewModel.loginWithPortalCode(code)
                                    // "command=close" is what the portal sends during maintenance.
                                    else -> viewModel.failWith(
                                        LibrusException(LibrusException.Reason.MAINTENANCE, uri.toString()).toUiError()
                                    )
                                }
                            },
                            onLoading = { pageLoading = it },
                            onHistoryChanged = { canGoBack = it },
                        )
                        startPortalLogin()
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
            if (pageLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            if (state == LoginViewModel.State.Loading || state is LoginViewModel.State.Done) {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.portal_importing))
                }
            }
        }
    }

    if (state is LoginViewModel.State.Done && state.needingAction.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onLoggedIn,
            title = { Text(stringResource(R.string.portal_skipped_title)) },
            text = { Text(stringResource(R.string.portal_skipped_message, state.needingAction.joinToString(", "))) },
            confirmButton = { TextButton(onClick = onLoggedIn) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (state is LoginViewModel.State.Failed) {
        LoginErrorDialog(state.error) {
            viewModel.dismissError()
            webView?.startPortalLogin()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configure(
    onRedirect: (Uri) -> Unit,
    onLoading: (Boolean) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // Same marker the official app appends; the portal uses it to pick the mobile login flow.
    settings.userAgentString = settings.userAgentString + Librus.USER_AGENT_SUFFIX

    webViewClient = object : WebViewClient() {
        private var handled = false

        private fun intercept(url: String?): Boolean {
            if (url == null || !url.startsWith(Librus.REDIRECT_URL)) return false
            if (!handled) {
                handled = true
                onRedirect(Uri.parse(url))
            }
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            intercept(request.url.toString())

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (intercept(url)) {
                view.stopLoading()
                return
            }
            handled = false
            onLoading(true)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onLoading(false)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            onHistoryChanged(view.canGoBack())
        }
    }
}

/** Starts the OAuth flow from scratch, with no leftover portal session. */
private fun WebView.startPortalLogin() {
    CookieManager.getInstance().removeAllCookies {
        clearHistory()
        loadUrl(Librus.AUTHORIZE_URL, mapOf("X-Requested-With" to Librus.REQUESTED_WITH))
    }
}
