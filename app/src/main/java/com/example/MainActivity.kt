package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AutoRefreshManager
import com.example.service.AutoRefreshService
import com.example.service.FloatingBubbleService
import com.example.ui.AutoRefreshControlRibbon
import com.example.ui.BrowserTopBar
import com.example.ui.BrowserWebView
import com.example.ui.CustomIntervalDialog
import com.example.ui.OverlayPermissionDialog
import com.example.ui.PresetsBottomSheet
import com.example.ui.SettingsBottomSheet
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    override fun onPause() {
        super.onPause()
        // Save battery and CPU in background
        webView?.onPause()
        webView?.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AutoRefreshManager.init(this)

        setContent {
            MyApplicationTheme {
                val state by AutoRefreshManager.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                // Dialog and sheet states
                var showSettingsSheet by remember { mutableStateOf(false) }
                var showPresetsSheet by remember { mutableStateOf(false) }
                var showCustomIntervalDialog by remember { mutableStateOf(false) }
                var showOverlayPermissionDialog by remember { mutableStateOf(false) }
                var isFullscreen by remember { mutableStateOf(false) }

                // Manage Keep Screen Awake Flag
                LaunchedEffect(state.keepScreenOn) {
                    if (state.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Request Notification Permission for Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted && state.backgroundServiceActive) {
                        AutoRefreshService.startService(context)
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Handle Hardware / Gesture Back Press
                BackHandler {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        // Minimize app rather than closing so background service & floating bubble keep running!
                        moveTaskToBack(true)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (!isFullscreen) {
                            Column(modifier = Modifier.statusBarsPadding()) {
                                BrowserTopBar(
                                    state = state,
                                    onUrlSubmit = { newUrl ->
                                        AutoRefreshManager.setUrl(newUrl)
                                    },
                                    onRunUrl = { newUrl ->
                                        AutoRefreshManager.runAndStart(newUrl)
                                        AutoRefreshService.startService(context)
                                        if (Settings.canDrawOverlays(context)) {
                                            FloatingBubbleService.start(context)
                                        } else {
                                            showOverlayPermissionDialog = true
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Running Auto Refresh: $newUrl")
                                        }
                                    },
                                    onToggleFloatingBubble = {
                                        if (!state.floatingWidgetActive) {
                                            if (Settings.canDrawOverlays(context)) {
                                                FloatingBubbleService.start(context)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Mini Floating Widget Activated")
                                                }
                                            } else {
                                                showOverlayPermissionDialog = true
                                            }
                                        } else {
                                            FloatingBubbleService.stop(context)
                                        }
                                    },
                                    onOpenSettings = { showSettingsSheet = true },
                                    onOpenPresets = { showPresetsSheet = true },
                                    onToggleDesktop = {
                                        AutoRefreshManager.setDesktopMode(!state.desktopMode)
                                    },
                                    isFullscreen = isFullscreen,
                                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                                    canGoBack = webView?.canGoBack() == true,
                                    canGoForward = webView?.canGoForward() == true,
                                    onGoBack = { webView?.goBack() },
                                    onGoForward = { webView?.goForward() }
                                )

                                AutoRefreshControlRibbon(
                                    state = state,
                                    onIntervalSelected = { seconds ->
                                        AutoRefreshManager.setInterval(seconds)
                                    },
                                    onToggleStartPause = {
                                        val willStart = !state.isRunning
                                        AutoRefreshManager.toggleRunning()
                                        if (willStart) {
                                            AutoRefreshService.startService(context)
                                            if (Settings.canDrawOverlays(context)) {
                                                FloatingBubbleService.start(context)
                                            } else {
                                                showOverlayPermissionDialog = true
                                            }
                                        }
                                    },
                                    onReloadNow = {
                                        AutoRefreshManager.triggerReload()
                                    },
                                    onToggleFloatingBubble = {
                                        if (!state.floatingWidgetActive) {
                                            if (Settings.canDrawOverlays(context)) {
                                                FloatingBubbleService.start(context)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Mini Floating Widget Enabled")
                                                }
                                            } else {
                                                showOverlayPermissionDialog = true
                                            }
                                        } else {
                                            FloatingBubbleService.stop(context)
                                        }
                                    },
                                    onToggleBackgroundService = {
                                        val newState = !state.backgroundServiceActive
                                        if (newState) {
                                            AutoRefreshService.startService(context)
                                        } else {
                                            AutoRefreshService.stopService(context)
                                        }
                                    },
                                    onToggleDesktop = {
                                        AutoRefreshManager.setDesktopMode(!state.desktopMode)
                                    },
                                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                                    isFullscreen = isFullscreen,
                                    onOpenCustomIntervalDialog = { showCustomIntervalDialog = true }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        BrowserWebView(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onWebViewCreated = { wv ->
                                webView = wv
                            }
                        )

                        // Web Error Banner if URL failed to load
                        if (state.errorMessage != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .align(Alignment.BottomCenter)
                                    .testTag("error_banner"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Page Load Issue",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = state.errorMessage ?: "Check website URL or network",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Button(
                                        onClick = { AutoRefreshManager.triggerReload() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Interval Dialog
                if (showCustomIntervalDialog) {
                    CustomIntervalDialog(
                        currentInterval = state.intervalSeconds,
                        onDismiss = { showCustomIntervalDialog = false },
                        onConfirm = { newInterval ->
                            AutoRefreshManager.setInterval(newInterval)
                            showCustomIntervalDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Interval updated to ${newInterval}s")
                            }
                        }
                    )
                }

                // Presets & Bookmarks Sheet
                if (showPresetsSheet) {
                    PresetsBottomSheet(
                        onDismiss = { showPresetsSheet = false },
                        onSelectPreset = { url, interval ->
                            AutoRefreshManager.runAndStart(url, interval)
                            AutoRefreshService.startService(context)
                            if (Settings.canDrawOverlays(context)) {
                                FloatingBubbleService.start(context)
                            } else {
                                showOverlayPermissionDialog = true
                            }
                            showPresetsSheet = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Running: $url (${interval}s)")
                            }
                        },
                        onAddCustomBookmark = { customUrl ->
                            AutoRefreshManager.setUrl(customUrl)
                            showPresetsSheet = false
                        }
                    )
                }

                // Settings Bottom Sheet
                if (showSettingsSheet) {
                    SettingsBottomSheet(
                        state = state,
                        onDismiss = { showSettingsSheet = false },
                        onToggleBackgroundService = { enabled ->
                            if (enabled) {
                                AutoRefreshService.startService(context)
                            } else {
                                AutoRefreshService.stopService(context)
                            }
                        },
                        onToggleFloatingBubble = { enabled ->
                            if (enabled) {
                                if (Settings.canDrawOverlays(context)) {
                                    FloatingBubbleService.start(context)
                                } else {
                                    showOverlayPermissionDialog = true
                                }
                            } else {
                                FloatingBubbleService.stop(context)
                            }
                        },
                        onToggleKeepScreenOn = { AutoRefreshManager.setKeepScreenOn(it) },
                        onToggleClearCache = { AutoRefreshManager.setClearCache(it) },
                        onToggleAutoScroll = { AutoRefreshManager.setAutoScroll(it) },
                        onSetRandomJitter = { AutoRefreshManager.setRandomJitter(it) },
                        onResetStats = {
                            AutoRefreshManager.resetStats()
                            scope.launch {
                                snackbarHostState.showSnackbar("Statistics counter reset to 0")
                            }
                        }
                    )
                }

                // Overlay Permission Dialog
                if (showOverlayPermissionDialog) {
                    OverlayPermissionDialog(
                        onDismiss = { showOverlayPermissionDialog = false },
                        onOpenSettings = {
                            showOverlayPermissionDialog = false
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
