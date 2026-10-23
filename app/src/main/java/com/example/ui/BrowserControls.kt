package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RefreshState
import com.example.model.formatTimeRemaining
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.ChromeBlue
import com.example.ui.theme.EmeraldGreen

@Composable
fun BrowserTopBar(
    state: RefreshState,
    onUrlSubmit: (String) -> Unit,
    onRunUrl: (String) -> Unit = onUrlSubmit,
    onToggleFloatingBubble: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPresets: () -> Unit,
    onToggleDesktop: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by remember(state.url) { mutableStateOf(state.url) }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Omnibox and quick actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation buttons
                IconButton(
                    onClick = onGoBack,
                    enabled = canGoBack,
                    modifier = Modifier.testTag("nav_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = onGoForward,
                    enabled = canGoForward,
                    modifier = Modifier.testTag("nav_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }

                // Chrome-style Omnibox URL Input
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("url_input_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    leadingIcon = {
                        val isHttps = state.url.startsWith("https://")
                        Icon(
                            imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = if (isHttps) "Secure connection" else "Web page",
                            modifier = Modifier.size(16.dp),
                            tint = if (isHttps) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlText = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear URL",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onRunUrl(urlText)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("run_url_button")
                            ) {
                                Text("RUN", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            onRunUrl(urlText)
                        }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Presets & Bookmarks Button
                IconButton(
                    onClick = onOpenPresets,
                    modifier = Modifier.testTag("presets_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Preset Websites",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Settings Menu Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Refresh Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Web Loading Progress Bar
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.pageProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = ChromeBlue,
                    trackColor = Color.Transparent,
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
    }
}

@Composable
fun AutoRefreshControlRibbon(
    state: RefreshState,
    onIntervalSelected: (Int) -> Unit,
    onToggleStartPause: () -> Unit,
    onReloadNow: () -> Unit,
    onToggleFloatingBubble: () -> Unit,
    onToggleBackgroundService: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
    onOpenCustomIntervalDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Live status & Action controls bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Live Status Badge with pulsing indicator
                LiveStatusBadge(
                    isRunning = state.isRunning,
                    isPaused = state.isPaused,
                    isReloadBlinking = state.isReloadBlinking,
                    remainingSeconds = state.remainingSeconds,
                    totalRefreshes = state.totalRefreshes
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Floating Mini Icon Widget Button
                    val floatingBg = if (state.floatingWidgetActive) {
                        EmeraldGreen.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val floatingTint = if (state.floatingWidgetActive) {
                        EmeraldGreen
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    FilledTonalIconButton(
                        onClick = onToggleFloatingBubble,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("floating_bubble_toggle_button"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Mini Floating Icon",
                            tint = floatingTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Desktop mode toggle
                    FilledTonalIconButton(
                        onClick = onToggleDesktop,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("desktop_mode_button"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (state.desktopMode) Icons.Default.DesktopMac else Icons.Default.PhoneAndroid,
                            contentDescription = "Toggle Desktop Mode",
                            tint = if (state.desktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reload Now Button
                    FilledTonalIconButton(
                        onClick = onReloadNow,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("reload_now_button"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Now",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Main Start / Pause Button
                    Button(
                        onClick = onToggleStartPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                !state.isRunning -> EmeraldGreen
                                state.isPaused -> AmberOrange
                                else -> MaterialTheme.colorScheme.error
                            }
                        ),
                        shape = RoundedCornerShape(19.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("start_stop_button")
                    ) {
                        Icon(
                            imageVector = when {
                                !state.isRunning -> Icons.Default.PlayArrow
                                state.isPaused -> Icons.Default.PlayArrow
                                else -> Icons.Default.Pause
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                !state.isRunning -> "Start"
                                state.isPaused -> "Resume"
                                else -> "Pause"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.isReloadBlinking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF1744))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ REFRESHING WEBPAGE (RED BLINK 1-3s) ⚡",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Quick Interval Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interval:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp)
                )

                val minuteIntervals = listOf(
                    120 to "2 mint",
                    300 to "5 mint",
                    600 to "10 mint",
                    900 to "15 mint"
                )

                minuteIntervals.forEach { (seconds, label) ->
                    val isSelected = state.intervalSeconds == seconds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onIntervalSelected(seconds) },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("interval_chip_${seconds / 60}m")
                    )
                }
            }
        }
    }
}

@Composable
fun LiveStatusBadge(
    isRunning: Boolean,
    isPaused: Boolean,
    isReloadBlinking: Boolean = false,
    remainingSeconds: Int,
    totalRefreshes: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isReloadBlinking) 0.7f else 0.85f,
        targetValue = if (isReloadBlinking) 1.35f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isReloadBlinking) 250 else 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val redColor = Color(0xFFFF1744)

    val statusDotColor = when {
        isReloadBlinking -> redColor
        !isRunning -> MaterialTheme.colorScheme.outline
        isPaused -> AmberOrange
        else -> EmeraldGreen
    }

    val badgeBg = when {
        isReloadBlinking -> Color(0x33FF1744)
        else -> MaterialTheme.colorScheme.surface
    }

    val badgeBorder = when {
        isReloadBlinking -> BorderStroke(1.5.dp, redColor)
        else -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(if (badgeBorder != null) Modifier.border(badgeBorder, RoundedCornerShape(14.dp)) else Modifier)
            .background(badgeBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isReloadBlinking) 10.dp else 8.dp)
                .scale(if ((isRunning && !isPaused) || isReloadBlinking) pulseScale else 1f)
                .clip(CircleShape)
                .background(statusDotColor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        if (isReloadBlinking) {
            Text(
                text = "⚡ RELOADING ⚡",
                style = MaterialTheme.typography.labelSmall,
                color = redColor,
                fontWeight = FontWeight.ExtraBold
            )
        } else if (isRunning) {
            if (isPaused) {
                Text(
                    text = "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberOrange,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = formatTimeRemaining(remainingSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "Ready",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "• #$totalRefreshes",
            style = MaterialTheme.typography.labelSmall,
            color = if (isReloadBlinking) redColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
