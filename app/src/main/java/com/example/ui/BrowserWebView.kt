package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.RefreshState
import com.example.service.AutoRefreshManager
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    state: RefreshState,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    val defaultUserAgent = remember { WebSettings.getDefaultUserAgent(context) }

    // Listen for reload trigger events
    LaunchedEffect(Unit) {
        AutoRefreshManager.reloadTrigger.collectLatest {
            webViewInstance?.let { wv ->
                if (state.clearCacheOnRefresh) {
                    wv.clearCache(true)
                }
                // Refresh the exact active URL the user is currently browsing
                val activeUrl = wv.url
                if (!activeUrl.isNullOrBlank() && activeUrl != "about:blank") {
                    wv.reload()
                } else {
                    wv.loadUrl(state.url)
                }
            }
        }
    }

    // Apply Desktop / Mobile user agent switch
    LaunchedEffect(state.desktopMode) {
        webViewInstance?.let { wv ->
            wv.settings.userAgentString = if (state.desktopMode) desktopUserAgent else defaultUserAgent
            wv.settings.useWideViewPort = state.desktopMode
            wv.settings.loadWithOverviewMode = state.desktopMode
            wv.reload()
        }
    }

    // React to URL changes if the user entered a different URL from the Omnibox or presets
    LaunchedEffect(state.url) {
        if (state.url.isNotEmpty() && state.url != loadedUrl && state.url != webViewInstance?.url && webViewInstance != null) {
            loadedUrl = state.url
            webViewInstance?.loadUrl(state.url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = state.desktopMode
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = if (state.desktopMode) desktopUserAgent else defaultUserAgent
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            AutoRefreshManager.updatePageStatus(
                                url = view?.url,
                                title = view?.title,
                                progress = newProgress,
                                isLoading = newProgress < 100
                            )
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            AutoRefreshManager.updatePageStatus(
                                url = view?.url,
                                title = title,
                                progress = 100,
                                isLoading = false
                            )
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            val currentUrl = view?.url ?: url
                            AutoRefreshManager.updatePageStatus(
                                url = currentUrl,
                                title = view?.title,
                                progress = 20,
                                isLoading = true,
                                error = null
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val currentUrl = view?.url ?: url
                            loadedUrl = currentUrl
                            AutoRefreshManager.updatePageStatus(
                                url = currentUrl,
                                title = view?.title,
                                progress = 100,
                                isLoading = false,
                                error = null
                            )
                            if (state.autoScrollToBottom) {
                                view?.postDelayed({
                                    view.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null)
                                }, 500)
                            }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            val currentUrl = view?.url ?: url
                            if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank") {
                                loadedUrl = currentUrl
                                AutoRefreshManager.updatePageStatus(
                                    url = currentUrl,
                                    title = view?.title,
                                    progress = view?.progress ?: 100,
                                    isLoading = false
                                )
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val desc = error?.description?.toString() ?: "Failed to load page"
                                AutoRefreshManager.updatePageStatus(
                                    title = null,
                                    progress = 100,
                                    isLoading = false,
                                    error = desc
                                )
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // Proceed for dev/testing or standard sites
                            handler?.proceed()
                        }
                    }

                    loadUrl(state.url)
                    loadedUrl = state.url
                    webViewInstance = this
                    onWebViewCreated(this)
                }
            },
            update = { wv ->
                webViewInstance = wv
            }
        )
    }
}
