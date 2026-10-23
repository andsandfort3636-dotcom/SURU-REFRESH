package com.example.service

import android.content.Context
import android.content.SharedPreferences
import com.example.model.RefreshState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

object AutoRefreshManager {
    private const val PREFS_NAME = "auto_refresh_prefs"
    private const val KEY_URL = "last_url"
    private const val KEY_INTERVAL = "interval_sec"
    private const val KEY_DESKTOP = "desktop_mode"
    private const val KEY_KEEP_SCREEN = "keep_screen_on"
    private const val KEY_CLEAR_CACHE = "clear_cache"
    private const val KEY_AUTO_SCROLL = "auto_scroll"
    private const val KEY_RANDOM_JITTER = "random_jitter"

    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null
    private var blinkJob: Job? = null
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(RefreshState())
    val state = _state.asStateFlow()

    private val _reloadTrigger = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val reloadTrigger = _reloadTrigger.asSharedFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = prefs?.getString(KEY_URL, "https://www.google.com") ?: "https://www.google.com"
            val savedInterval = prefs?.getInt(KEY_INTERVAL, 120) ?: 120
            val savedDesktop = prefs?.getBoolean(KEY_DESKTOP, false) ?: false
            val savedKeepScreen = prefs?.getBoolean(KEY_KEEP_SCREEN, false) ?: false
            val savedClearCache = prefs?.getBoolean(KEY_CLEAR_CACHE, false) ?: false
            val savedAutoScroll = prefs?.getBoolean(KEY_AUTO_SCROLL, false) ?: false
            val savedRandomJitter = prefs?.getInt(KEY_RANDOM_JITTER, 0) ?: 0

            _state.update {
                it.copy(
                    url = savedUrl,
                    intervalSeconds = savedInterval,
                    remainingSeconds = savedInterval,
                    desktopMode = savedDesktop,
                    keepScreenOn = savedKeepScreen,
                    clearCacheOnRefresh = savedClearCache,
                    autoScrollToBottom = savedAutoScroll,
                    randomJitterSeconds = savedRandomJitter
                )
            }
        }
    }

    fun setUrl(url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        _state.update { it.copy(url = cleanUrl, errorMessage = null) }
        prefs?.edit()?.putString(KEY_URL, cleanUrl)?.apply()
    }

    fun setInterval(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(1)
        _state.update {
            it.copy(
                intervalSeconds = safeSeconds,
                remainingSeconds = if (!it.isRunning || it.isPaused) safeSeconds else it.remainingSeconds.coerceAtMost(safeSeconds)
            )
        }
        prefs?.edit()?.putInt(KEY_INTERVAL, safeSeconds)?.apply()
    }

    fun setDesktopMode(enabled: Boolean) {
        _state.update { it.copy(desktopMode = enabled) }
        prefs?.edit()?.putBoolean(KEY_DESKTOP, enabled)?.apply()
        triggerReload()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _state.update { it.copy(keepScreenOn = enabled) }
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN, enabled)?.apply()
    }

    fun setClearCache(enabled: Boolean) {
        _state.update { it.copy(clearCacheOnRefresh = enabled) }
        prefs?.edit()?.putBoolean(KEY_CLEAR_CACHE, enabled)?.apply()
    }

    fun setAutoScroll(enabled: Boolean) {
        _state.update { it.copy(autoScrollToBottom = enabled) }
        prefs?.edit()?.putBoolean(KEY_AUTO_SCROLL, enabled)?.apply()
    }

    fun setRandomJitter(jitterSeconds: Int) {
        val safeJitter = jitterSeconds.coerceIn(0, 30)
        _state.update { it.copy(randomJitterSeconds = safeJitter) }
        prefs?.edit()?.putInt(KEY_RANDOM_JITTER, safeJitter)?.apply()
    }

    fun setFloatingWidgetActive(active: Boolean) {
        _state.update { it.copy(floatingWidgetActive = active) }
    }

    fun setBackgroundServiceActive(active: Boolean) {
        _state.update { it.copy(backgroundServiceActive = active) }
    }

    fun start() {
        val currentInterval = _state.value.intervalSeconds
        _state.update {
            it.copy(
                isRunning = true,
                isPaused = false,
                remainingSeconds = currentInterval
            )
        }
        startTimerLoop()
    }

    fun pause() {
        _state.update { it.copy(isPaused = true) }
        timerJob?.cancel()
        timerJob = null
    }

    fun resume() {
        if (_state.value.isRunning && _state.value.isPaused) {
            _state.update { it.copy(isPaused = false) }
            startTimerLoop()
        }
    }

    fun toggleRunning() {
        if (_state.value.isRunning) {
            if (_state.value.isPaused) {
                resume()
            } else {
                pause()
            }
        } else {
            start()
        }
    }

    fun stop() {
        _state.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                remainingSeconds = it.intervalSeconds
            )
        }
        timerJob?.cancel()
        timerJob = null
    }

    fun runAndStart(url: String, intervalSeconds: Int? = null) {
        setUrl(url)
        if (intervalSeconds != null) {
            setInterval(intervalSeconds)
        }
        start()
        triggerReload()
    }

    fun triggerReload() {
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                totalRefreshes = it.totalRefreshes + 1,
                lastRefreshTime = now,
                remainingSeconds = calculateNextInterval(),
                isReloadBlinking = true
            )
        }
        _reloadTrigger.tryEmit(now)

        // Red blink for 2 seconds (1-3 sec duration as requested)
        blinkJob?.cancel()
        blinkJob = scope.launch {
            delay(2000L)
            _state.update { it.copy(isReloadBlinking = false) }
        }
    }

    private fun calculateNextInterval(): Int {
        val base = _state.value.intervalSeconds
        val jitter = _state.value.randomJitterSeconds
        return if (jitter > 0) {
            val delta = Random.nextInt(-jitter, jitter + 1)
            (base + delta).coerceAtLeast(1)
        } else {
            base
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _state.value.isRunning && !_state.value.isPaused) {
                delay(1000L)
                val currentRemaining = _state.value.remainingSeconds
                if (currentRemaining <= 1) {
                    triggerReload()
                } else {
                    _state.update { it.copy(remainingSeconds = currentRemaining - 1) }
                }
            }
        }
    }

    fun updatePageStatus(url: String? = null, title: String? = null, progress: Int, isLoading: Boolean, error: String? = null) {
        val validUrl = if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) url else null
        _state.update {
            it.copy(
                url = validUrl ?: it.url,
                pageTitle = if (!title.isNullOrBlank()) title else it.pageTitle,
                pageProgress = progress,
                isLoading = isLoading,
                errorMessage = error
            )
        }
        if (validUrl != null) {
            prefs?.edit()?.putString(KEY_URL, validUrl)?.apply()
        }
    }

    fun resetStats() {
        _state.update {
            it.copy(
                totalRefreshes = 0,
                lastRefreshTime = 0L,
                remainingSeconds = it.intervalSeconds
            )
        }
    }
}
