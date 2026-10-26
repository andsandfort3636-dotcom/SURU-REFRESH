package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
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

    fun rePasteAndLoadUrl(wv: WebView) {
        val targetUrl = if (!wv.url.isNullOrBlank() && wv.url != "about:blank") {
            wv.url!!
        } else {
            AutoRefreshManager.state.value.url
        }
        if (targetUrl.isNotBlank()) {
            if (AutoRefreshManager.state.value.clearCacheOnRefresh) {
                wv.clearCache(true)
            }
            // Cut and re-paste URL and press enter: stop current loading and navigate from scratch!
            wv.stopLoading()
            wv.loadUrl(targetUrl)
        }
    }

    // Listen for reload trigger events
    LaunchedEffect(Unit) {
        AutoRefreshManager.reloadTrigger.collectLatest {
            webViewInstance?.let { wv ->
                rePasteAndLoadUrl(wv)
            }
        }
    }

    // Register direct reload handler for instant execution even in background
    DisposableEffect(Unit) {
        AutoRefreshManager.webViewReloadHandler = {
            webViewInstance?.let { wv ->
                rePasteAndLoadUrl(wv)
            }
        }
        onDispose {
            AutoRefreshManager.webViewReloadHandler = null
            webViewInstance?.stopLoading()
        }
    }

    // Listen for explicit URL navigation requests (from Omnibox or presets)
    LaunchedEffect(Unit) {
        AutoRefreshManager.loadUrlTrigger.collectLatest { targetUrl ->
            if (targetUrl.isNotBlank() && webViewInstance != null) {
                loadedUrl = targetUrl
                webViewInstance?.stopLoading()
                webViewInstance?.loadUrl(targetUrl)
            }
        }
    }

    // Apply Desktop / Mobile user agent switch
    LaunchedEffect(state.desktopMode) {
        webViewInstance?.let { wv ->
            wv.settings.userAgentString = if (state.desktopMode) desktopUserAgent else defaultUserAgent
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            rePasteAndLoadUrl(wv)
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

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowFileAccess = false
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = if (state.desktopMode) desktopUserAgent else defaultUserAgent
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            val stillLoading = newProgress < 75
                            AutoRefreshManager.updatePageStatus(
                                url = view?.url,
                                title = view?.title,
                                progress = newProgress,
                                isLoading = stillLoading
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
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                return false // Keep inside this WebView so it always keeps running!
                            }
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, request.url).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                view?.context?.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                true
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            val currentUrl = view?.url ?: url
                            if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank") {
                                loadedUrl = currentUrl
                            }
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
                            if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank") {
                                loadedUrl = currentUrl
                            }
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
                                val errorCode = error?.errorCode ?: 0
                                val desc = error?.description?.toString() ?: ""
                                // Error code -1 / ERR_ABORTED is normal on redirects
                                if (errorCode != -1 && !desc.contains("ERR_ABORTED", ignoreCase = true)) {
                                    AutoRefreshManager.updatePageStatus(
                                        title = null,
                                        progress = 100,
                                        isLoading = false,
                                        error = desc.ifBlank { "Failed to load page" }
                                    )
                                }
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
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
