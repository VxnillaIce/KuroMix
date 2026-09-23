package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuromify.kuromix.notification.SuperIslandManager
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.core.content.edit

// ─── Mode constants ─────────────────────────────────────────────

private const val MODE_SPLIT_SECOND = 0
private const val MODE_ALWAYS_ON = 1

private val MODE_OPTIONS = listOf("Split Second", "Always On")

private fun prefKey(module: String) = "kuromix_island_mode_$module"

private fun modeToString(mode: Int): String =
    if (mode == MODE_ALWAYS_ON) "always" else "split"

// ─── Timer duration persistence ─────────────────────────────────

private const val PREF_TIMER_MINUTES = "kuromix_timer_minutes"
private const val PREF_TIMER_SECONDS = "kuromix_timer_seconds"

private fun loadTimerMinutes(context: android.content.Context): Int =
    context.getSharedPreferences("kuromix_prefs", 0)
        .getInt(PREF_TIMER_MINUTES, 5)

private fun loadTimerSeconds(context: android.content.Context): Int =
    context.getSharedPreferences("kuromix_prefs", 0)
        .getInt(PREF_TIMER_SECONDS, 0)

private fun saveTimerDuration(
    context: android.content.Context,
    minutes: Int,
    seconds: Int
) {
    context.getSharedPreferences("kuromix_prefs", 0)
        .edit {
            putInt(PREF_TIMER_MINUTES, minutes)
                .putInt(PREF_TIMER_SECONDS, seconds)
        }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun HyperIslandScreen(
    bottomPadding: Dp = 0.dp,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // Global hook
    var hyperIslandHookEnabled by remember {
        mutableStateOf(SuperIslandManager.isHyperIslandHookEnabled(context))
    }

    // ── Module toggles ──────────────────────────────────────────
    var chargingEnabled by remember { mutableStateOf(false) }
    var timerEnabled by remember { mutableStateOf(false) }
    var networkEnabled by remember { mutableStateOf(false) }
    var temperatureEnabled by remember { mutableStateOf(false) }

    // ── Module modes (only for modules that have a choice) ──────
    var chargingMode by remember { mutableStateOf(loadMode(context, "charging")) }
    var networkMode by remember { mutableStateOf(loadMode(context, "network")) }
    var temperatureMode by remember { mutableStateOf(loadMode(context, "temperature")) }

    // ── Timer duration ─────────────────────────────────────────
    var timerMinutes by remember { mutableIntStateOf(loadTimerMinutes(context)) }
    var timerSeconds by remember { mutableIntStateOf(loadTimerSeconds(context)) }
    var showTimerPicker by remember { mutableStateOf(false) }

    // ── Helper: activate exactly one module ─────────────────────
    fun activateModule(
        moduleId: String,
        mode: Int,
        enabled: Boolean
    ) {
        if (!hyperIslandHookEnabled) return

        if (enabled) {
            chargingEnabled = (moduleId == "charging")
            timerEnabled = (moduleId == "timer")
            networkEnabled = (moduleId == "network")
            temperatureEnabled = (moduleId == "temperature")

            SuperIslandManager.switchLiveMode(
                context = context,
                mode = moduleId,
                displayMode = modeToString(mode)
            )
        } else {
            SuperIslandManager.stopLiveUpdates(context)
        }
    }

    // Disable everything if the global hook is turned off
    LaunchedEffect(hyperIslandHookEnabled) {
        if (!hyperIslandHookEnabled) {
            chargingEnabled = false
            timerEnabled = false
            networkEnabled = false
            temperatureEnabled = false
            SuperIslandManager.stopLiveUpdates(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "HyperIsland",
                largeTitle = "HyperIsland",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = bottomPadding + 16.dp
                )
            ) {
                // ── HYPERISLAND ─────────────────────────────────────
                item {
                    SmallTitle(text = "HYPERISLAND")

                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        SwitchPreference(
                            title = "Enable HyperIsland Hook",
                            summary = if (hyperIslandHookEnabled) {
                                "HyperIsland is enabled"
                            } else {
                                "Enable HyperIsland"
                            },
                            checked = hyperIslandHookEnabled,
                            onCheckedChange = { enabled ->
                                hyperIslandHookEnabled = enabled
                                SuperIslandManager.setHyperIslandHookEnabled(
                                    context = context,
                                    enabled = enabled
                                )
                            }
                        )
                    }
                }

                // ── MODULES ─────────────────────────────────────────
                item {
                    Spacer(Modifier.height(16.dp))
                    SmallTitle(text = "MODULES")

                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {

                        // ── Charging ────────────────────────────────
                        SwitchPreference(
                            title = "Charging",
                            summary = if (chargingEnabled) {
                                if (chargingMode == MODE_ALWAYS_ON) "Always On"
                                else "Split Second (5s)"
                            } else {
                                "Show battery & wattage on island"
                            },
                            checked = chargingEnabled,
                            onCheckedChange = { enabled ->
                                chargingEnabled = enabled
                                activateModule("charging", chargingMode, enabled)
                            }
                        )
                        if (chargingEnabled) {
                            OverlayDropdownPreference(
                                title = "Display Mode",
                                summary = MODE_OPTIONS[chargingMode],
                                items = MODE_OPTIONS,
                                selectedIndex = chargingMode,
                                onSelectedIndexChange = {
                                    chargingMode = it
                                    saveMode(context, "charging", it)
                                    SuperIslandManager.switchLiveMode(
                                        context = context,
                                        mode = "charging",
                                        displayMode = modeToString(it)
                                    )
                                }
                            )
                        }

                        // ── Timer ───────────────────────────────────
                        // Timer has no display-mode dropdown: it is always
                        // persistent until the user turns it off.
                        SwitchPreference(
                            title = "Timer",
                            summary = if (timerEnabled) {
                                "Running — %02d:%02d".format(
                                    timerMinutes,
                                    timerSeconds
                                )
                            } else {
                                "Countdown timer on island"
                            },
                            checked = timerEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    SuperIslandManager.setTimerDuration(
                                        minutes = timerMinutes,
                                        seconds = timerSeconds
                                    )
                                    activateModule("timer", MODE_ALWAYS_ON, true)
                                } else {
                                    timerEnabled = false
                                    SuperIslandManager.stopLiveUpdates(context)
                                }
                            }
                        )
                        if (timerEnabled) {
                            ArrowPreference(
                                title = "Duration",
                                summary = "%02d:%02d".format(
                                    timerMinutes,
                                    timerSeconds
                                ),
                                onClick = { showTimerPicker = true }
                            )
                        }

                        // ── Network ─────────────────────────────────
                        SwitchPreference(
                            title = "Network Speed",
                            summary = if (networkEnabled) {
                                if (networkMode == MODE_ALWAYS_ON) "Always On"
                                else "Split Second (5s)"
                            } else {
                                "Live upload/download speed"
                            },
                            checked = networkEnabled,
                            onCheckedChange = { enabled ->
                                networkEnabled = enabled
                                activateModule("network", networkMode, enabled)
                            }
                        )
                        if (networkEnabled) {
                            OverlayDropdownPreference(
                                title = "Display Mode",
                                summary = MODE_OPTIONS[networkMode],
                                items = MODE_OPTIONS,
                                selectedIndex = networkMode,
                                onSelectedIndexChange = {
                                    networkMode = it
                                    saveMode(context, "network", it)
                                    SuperIslandManager.switchLiveMode(
                                        context = context,
                                        mode = "network",
                                        displayMode = modeToString(it)
                                    )
                                }
                            )
                        }

                        // ── Temperature ─────────────────────────────
                        SwitchPreference(
                            title = "Temperature",
                            summary = if (temperatureEnabled) {
                                if (temperatureMode == MODE_ALWAYS_ON) "Always On"
                                else "Split Second (5s)"
                            } else {
                                "CPU/thermal status"
                            },
                            checked = temperatureEnabled,
                            onCheckedChange = { enabled ->
                                temperatureEnabled = enabled
                                activateModule("temperature", temperatureMode, enabled)
                            }
                        )
                        if (temperatureEnabled) {
                            OverlayDropdownPreference(
                                title = "Display Mode",
                                summary = MODE_OPTIONS[temperatureMode],
                                items = MODE_OPTIONS,
                                selectedIndex = temperatureMode,
                                onSelectedIndexChange = {
                                    temperatureMode = it
                                    saveMode(context, "temperature", it)
                                    SuperIslandManager.switchLiveMode(
                                        context = context,
                                        mode = "temperature",
                                        displayMode = modeToString(it)
                                    )
                                }
                            )
                        }
                    }
                }

                // ── DEBUG ───────────────────────────────────────────
                item {
                    Spacer(Modifier.height(16.dp))
                    SmallTitle(text = "DEBUG")

                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        DebugPreference(
                            title = "Stop Live Island",
                            summary = "Cancel any active island immediately",
                            onClick = {
                                chargingEnabled = false
                                timerEnabled = false
                                networkEnabled = false
                                temperatureEnabled = false
                                SuperIslandManager.stopLiveUpdates(context)
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }

        // ── Timer duration picker dialog ────────────────────────
        // Inside Scaffold content lambda so OverlayDialog has an anchor.
        if (showTimerPicker) {
            OverlayDialog(
                show = true,
                title = "Timer Duration",
                onDismissRequest = { showTimerPicker = false }
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberPicker(
                            value = timerMinutes,
                            onValueChange = { timerMinutes = it },
                            range = 0..99,
                            label = { it.toString().padStart(2, '0') },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ":",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(24.dp)
                        )
                        NumberPicker(
                            value = timerSeconds,
                            onValueChange = { timerSeconds = it },
                            range = 0..59,
                            label = { it.toString().padStart(2, '0') },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = "Cancel",
                            onClick = { showTimerPicker = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        TextButton(
                            text = "Save",
                            onClick = {
                                saveTimerDuration(context, timerMinutes, timerSeconds)
                                SuperIslandManager.setTimerDuration(
                                    minutes = timerMinutes,
                                    seconds = timerSeconds
                                )
                                if (timerEnabled) {
                                    SuperIslandManager.resetTimer(context)
                                }
                                showTimerPicker = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun loadMode(context: android.content.Context, module: String): Int =
    context.getSharedPreferences("kuromix_prefs", 0)
        .getInt(prefKey(module), MODE_SPLIT_SECOND)

private fun saveMode(context: android.content.Context, module: String, mode: Int) {
    context.getSharedPreferences("kuromix_prefs", 0)
        .edit { putInt(prefKey(module), mode) }
}

@Composable
private fun DebugPreference(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick
    )
}