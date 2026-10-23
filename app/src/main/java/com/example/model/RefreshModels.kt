package com.example.model

data class RefreshState(
    val url: String = "https://www.google.com",
    val intervalSeconds: Int = 120, // Default 2 minutes
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val remainingSeconds: Int = 120,
    val totalRefreshes: Int = 0,
    val lastRefreshTime: Long = 0L,
    val desktopMode: Boolean = false,
    val keepScreenOn: Boolean = false,
    val clearCacheOnRefresh: Boolean = false,
    val autoScrollToBottom: Boolean = false,
    val randomJitterSeconds: Int = 0,
    val floatingWidgetActive: Boolean = false,
    val backgroundServiceActive: Boolean = false,
    val pageTitle: String = "Google",
    val pageProgress: Int = 100,
    val isLoading: Boolean = false,
    val isReloadBlinking: Boolean = false,
    val errorMessage: String? = null
) {
    val remainingFormatted: String
        get() = formatTimeRemaining(remainingSeconds)

    val intervalFormatted: String
        get() = formatIntervalLabel(intervalSeconds)
}

fun formatTimeRemaining(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

fun formatIntervalLabel(seconds: Int): String {
    val m = seconds / 60
    return if (m > 0) "$m min" else "$seconds sec"
}

data class PresetUrl(
    val title: String,
    val url: String,
    val recommendedIntervalSeconds: Int,
    val description: String,
    val iconName: String = "public"
)
