package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PresetUrl
import com.example.model.RefreshState
import com.example.service.AutoRefreshManager
import com.example.ui.theme.EmeraldGreen

@Composable
fun CustomIntervalDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var secondsText by remember { mutableStateOf(currentInterval.toString()) }
    var selectedUnit by remember { mutableStateOf("seconds") } // "seconds" or "minutes"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Refresh Interval", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Specify how often the webpage should auto-refresh:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedUnit == "seconds",
                        onClick = { selectedUnit = "seconds" },
                        label = { Text("Seconds") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedUnit == "minutes",
                        onClick = { selectedUnit = "minutes" },
                        label = { Text("Minutes") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 5) {
                            secondsText = input
                        }
                    },
                    label = { Text(if (selectedUnit == "seconds") "Enter seconds (min: 1)" else "Enter minutes (e.g. 5)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_interval_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick increment buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 15, 30, 60).forEach { addVal ->
                        OutlinedButton(
                            onClick = {
                                val current = secondsText.toIntOrNull() ?: 0
                                secondsText = (current + addVal).toString()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+$addVal", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawNum = secondsText.toIntOrNull() ?: 15
                    val finalSeconds = if (selectedUnit == "minutes") (rawNum * 60).coerceAtLeast(1) else rawNum.coerceAtLeast(1)
                    onConfirm(finalSeconds)
                },
                modifier = Modifier.testTag("save_custom_interval_button")
            ) {
                Text("Set Interval")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    state: RefreshState,
    onDismiss: () -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleClearCache: (Boolean) -> Unit,
    onToggleAutoScroll: (Boolean) -> Unit,
    onSetRandomJitter: (Int) -> Unit,
    onResetStats: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto Refresh & Background Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 24/7 Background Service
            SettingsToggleItem(
                icon = Icons.Default.NotificationsActive,
                title = "24/7 Background Service",
                description = "Keeps refreshing websites continuously in the background with a persistent system notification.",
                checked = state.backgroundServiceActive,
                onCheckedChange = onToggleBackgroundService,
                testTag = "toggle_background_service"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Mini Floating Icon
            SettingsToggleItem(
                icon = Icons.Default.PictureInPictureAlt,
                title = "Mini Floating Icon (Overlay)",
                description = "Draggable mini bubble on your screen showing live countdown and quick pause/reload controls.",
                checked = state.floatingWidgetActive,
                onCheckedChange = onToggleFloatingBubble,
                testTag = "toggle_floating_bubble"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Keep Screen On
            SettingsToggleItem(
                icon = Icons.Default.Visibility,
                title = "Keep Screen Awake",
                description = "Prevents device display from sleeping while auto-refresh is viewing the webpage.",
                checked = state.keepScreenOn,
                onCheckedChange = onToggleKeepScreenOn,
                testTag = "toggle_keep_screen_on"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Clear Cache on Refresh
            SettingsToggleItem(
                icon = Icons.Default.Layers,
                title = "Clear Cache on Reload",
                description = "Forces fresh download of webpage assets instead of loading from local cache.",
                checked = state.clearCacheOnRefresh,
                onCheckedChange = onToggleClearCache,
                testTag = "toggle_clear_cache"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Auto-scroll to bottom
            SettingsToggleItem(
                icon = Icons.Default.VerticalAlignBottom,
                title = "Auto-Scroll to Bottom",
                description = "Automatically scrolls down to the bottom of the webpage after each reload (great for live logs/feeds).",
                checked = state.autoScrollToBottom,
                onCheckedChange = onToggleAutoScroll,
                testTag = "toggle_auto_scroll"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Random Jitter
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Random Interval Variation",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (state.randomJitterSeconds == 0) "Exact interval (no variation)" else "± ${state.randomJitterSeconds}s natural jitter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "±${state.randomJitterSeconds}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = state.randomJitterSeconds.toFloat(),
                    onValueChange = { onSetRandomJitter(it.toInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                    modifier = Modifier.testTag("jitter_slider")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reset Statistics Button
            OutlinedButton(
                onClick = onResetStats,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_stats_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Reload Counter (Currently: ${state.totalRefreshes})")
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsBottomSheet(
    onDismiss: () -> Unit,
    onSelectPreset: (String, Int) -> Unit,
    onAddCustomBookmark: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customUrlInput by remember { mutableStateOf("") }

    val defaultPresets = listOf(
        PresetUrl("Google Search", "https://www.google.com", 30, "Clean standard search engine homepage"),
        PresetUrl("World Time Live", "https://time.is", 5, "Live synchronous atomic world clock"),
        PresetUrl("Hacker News", "https://news.ycombinator.com", 60, "Tech headlines & discussions"),
        PresetUrl("Fast.com Speed Test", "https://fast.com", 20, "Internet bandwidth and latency monitor"),
        PresetUrl("Wikipedia Current Events", "https://en.wikipedia.org/wiki/Portal:Current_events", 120, "Global news & ongoing events portal"),
        PresetUrl("GitHub Trending", "https://github.com/trending", 300, "Trending developer repositories today"),
        PresetUrl("HTTPBin API Echo", "https://httpbin.org/get", 10, "Reliable testing endpoint returning request headers")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Preset Websites & Quick Load",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select any website to immediately load and configure its recommended auto-refresh interval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Custom URL Quick Add
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customUrlInput,
                    onValueChange = { customUrlInput = it },
                    placeholder = { Text("e.g. mysite.com/dashboard") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_custom_preset_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customUrlInput.isNotBlank()) {
                            onAddCustomBookmark(customUrlInput)
                            customUrlInput = ""
                        }
                    },
                    modifier = Modifier.testTag("add_custom_preset_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(defaultPresets) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPreset(preset.url, preset.recommendedIntervalSeconds) }
                            .testTag("preset_card_${preset.title.replace(" ", "_")}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = preset.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${preset.recommendedIntervalSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PictureInPictureAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text("Enable Mini Floating Icon")
        },
        text = {
            Column {
                Text(
                    text = "To show the mini draggable floating widget over other apps and on your home screen, Android requires 'Display over other apps' permission.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap 'Open Settings', find 'Suru Auto', and toggle 'Allow display over other apps' to ON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("open_overlay_settings_button")
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
