package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuromify.kuromix.notification.SuperIslandManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun HyperIslandScreen(
    bottomPadding: Dp = 0.dp,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // =========================================================================
    // HYPERISLAND STATE
    // =========================================================================

    var hyperIslandHookEnabled by remember {
        mutableStateOf(
            SuperIslandManager.isHyperIslandHookEnabled(
                context
            )
        )
    }

    var testIslandEnabled by remember {
        mutableStateOf(false)
    }

    var selectedTestEvent by remember {
        mutableStateOf<String?>(null)
    }

    var downloadTestRunning by remember {
        mutableStateOf(
            SuperIslandManager.isDownloadTestRunning()
        )
    }

    // =========================================================================
    // DOWNLOAD STATE SYNC
    // =========================================================================
    //
    // The download simulation runs outside Compose, so periodically mirror
    // its state into the UI.
    //
    // =========================================================================

    LaunchedEffect(Unit) {
        while (true) {

            val running =
                SuperIslandManager.isDownloadTestRunning()

            if (downloadTestRunning != running) {
                downloadTestRunning = running
            }

            kotlinx.coroutines.delay(250L)
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    fun stopDownload() {
        SuperIslandManager.stopDownloadTest(
            context
        )

        downloadTestRunning = false
    }

    fun cancelTests() {
        stopDownload()

        selectedTestEvent = null
        testIslandEnabled = false

        SuperIslandManager.cancelTestIsland(
            context
        )
    }

    // =========================================================================
    // UI
    // =========================================================================

    Scaffold(
        topBar = {
            TopAppBar(
                title = "HyperIsland",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    top =
                        padding.calculateTopPadding() +
                                8.dp,

                    bottom =
                        bottomPadding +
                                24.dp
                )
            ) {

                // =================================================================
                // HYPERISLAND
                // =================================================================

                item {

                    SmallTitle(
                        text = "HYPERISLAND"
                    )

                    Card(
                        modifier = Modifier
                            .padding(
                                horizontal = 12.dp
                            )
                            .fillMaxWidth()
                    ) {

                        // ---------------------------------------------------------
                        // ENABLE HYPERISLAND
                        // ---------------------------------------------------------

                        SwitchPreference(
                            title =
                                "Enable HyperIsland Hook",

                            summary =
                                if (hyperIslandHookEnabled) {
                                    "HyperIsland test notifications are enabled"
                                } else {
                                    "Enable HyperIsland test notifications"
                                },

                            checked =
                                hyperIslandHookEnabled,

                            onCheckedChange = { enabled ->

                                hyperIslandHookEnabled =
                                    enabled

                                SuperIslandManager
                                    .setHyperIslandHookEnabled(
                                        context = context,
                                        enabled = enabled
                                    )

                                if (!enabled) {

                                    selectedTestEvent =
                                        null

                                    testIslandEnabled =
                                        false

                                    downloadTestRunning =
                                        false

                                    SuperIslandManager
                                        .cancelTestIsland(
                                            context
                                        )
                                }
                            }
                        )

                        // ---------------------------------------------------------
                        // TEST ISLAND
                        // ---------------------------------------------------------

                        SwitchPreference(
                            title =
                                "Test Island",

                            summary =
                                when {

                                    !hyperIslandHookEnabled ->
                                        "Enable HyperIsland first"

                                    downloadTestRunning ->
                                        "Download simulation is running"

                                    testIslandEnabled ->
                                        "Test events are ready"

                                    else ->
                                        "Enable to use test events"
                                },

                            checked =
                                testIslandEnabled,

                            onCheckedChange = { enabled ->

                                if (!hyperIslandHookEnabled) {
                                    return@SwitchPreference
                                }

                                testIslandEnabled =
                                    enabled

                                if (!enabled) {

                                    selectedTestEvent =
                                        null

                                    stopDownload()

                                    SuperIslandManager
                                        .cancelTestIsland(
                                            context
                                        )
                                }
                            }
                        )
                    }
                }

                // =================================================================
                // TEST EVENTS
                // =================================================================

                item {

                    SmallTitle(
                        text = "TEST EVENTS"
                    )

                    Card(
                        modifier = Modifier
                            .padding(
                                horizontal = 12.dp
                            )
                            .fillMaxWidth()
                    ) {

                        val eventsEnabled =
                            hyperIslandHookEnabled &&
                                    testIslandEnabled

                        // ---------------------------------------------------------
                        // CHARGING
                        // ---------------------------------------------------------

                        TestEventPreference(
                            title = "Charging",

                            summary =
                                "82% • 67W",

                            selected =
                                selectedTestEvent ==
                                        "charging",

                            enabled =
                                eventsEnabled,

                            onClick = {

                                selectedTestEvent =
                                    "charging"

                                stopDownload()

                                SuperIslandManager
                                    .showChargingTest(
                                        context = context,
                                        battery = 82,
                                        power = 67
                                    )
                            }
                        )

                        // ---------------------------------------------------------
                        // MEDIA
                        // ---------------------------------------------------------

                        TestEventPreference(
                            title = "Media",

                            summary =
                                "KuroMix • HyperIsland Demo",

                            selected =
                                selectedTestEvent ==
                                        "media",

                            enabled =
                                eventsEnabled,

                            onClick = {

                                selectedTestEvent =
                                    "media"

                                stopDownload()

                                SuperIslandManager
                                    .showMediaTest(
                                        context = context,
                                        artist = "KuroMix",
                                        title = "HyperIsland Demo"
                                    )
                            }
                        )

                        // ---------------------------------------------------------
                        // TIMER
                        // ---------------------------------------------------------

                        TestEventPreference(
                            title = "Timer",

                            summary =
                                "05:00 remaining",

                            selected =
                                selectedTestEvent ==
                                        "timer",

                            enabled =
                                eventsEnabled,

                            onClick = {

                                selectedTestEvent =
                                    "timer"

                                stopDownload()

                                SuperIslandManager
                                    .showTimerTest(
                                        context = context,
                                        remaining = "05:00"
                                    )
                            }
                        )

                        // ---------------------------------------------------------
                        // DOWNLOAD
                        // ---------------------------------------------------------

                        TestEventPreference(
                            title = "Download",

                            summary =
                                if (downloadTestRunning) {
                                    "KuroMix.apk • Downloading"
                                } else {
                                    "KuroMix.apk • 0% → 100%"
                                },

                            selected =
                                selectedTestEvent ==
                                        "download",

                            enabled =
                                eventsEnabled,

                            onClick = {

                                selectedTestEvent =
                                    "download"

                                if (!downloadTestRunning) {

                                    SuperIslandManager
                                        .startDownloadTest(
                                            context
                                        )

                                    downloadTestRunning =
                                        SuperIslandManager
                                            .isDownloadTestRunning()
                                }
                            }
                        )

                        // ---------------------------------------------------------
                        // NETWORK
                        // ---------------------------------------------------------

                        TestEventPreference(
                            title = "Network",

                            summary =
                                "5G • 128 Mbps",

                            selected =
                                selectedTestEvent ==
                                        "network",

                            enabled =
                                eventsEnabled,

                            onClick = {

                                selectedTestEvent =
                                    "network"

                                stopDownload()

                                SuperIslandManager
                                    .showNetworkTest(
                                        context = context,
                                        network = "5G",
                                        speed = "128 Mbps"
                                    )
                            }
                        )

                        // ---------------------------------------------------------
                        // GAMING
                        // ---------------------------------------------------------

                        TestEventPreference(
                            title = "Gaming",

                            summary =
                                "120 FPS • 34°C",

                            selected =
                                selectedTestEvent ==
                                        "gaming",

                            enabled =
                                eventsEnabled,

                            onClick = {

                                selectedTestEvent =
                                    "gaming"

                                stopDownload()

                                SuperIslandManager
                                    .showGameTest(
                                        context = context,
                                        fps = 120,
                                        temperature = 34
                                    )
                            }
                        )
                    }
                }

                // =================================================================
                // DOWNLOAD
                // =================================================================

                item {

                    SmallTitle(
                        text = "DOWNLOAD"
                    )

                    Card(
                        modifier = Modifier
                            .padding(
                                horizontal = 12.dp
                            )
                            .fillMaxWidth()
                    ) {

                        // ---------------------------------------------------------
                        // START
                        // ---------------------------------------------------------

                        DebugPreference(
                            title =
                                "Start Download Test",

                            summary =
                                if (downloadTestRunning) {
                                    "Downloading KuroMix.apk"
                                } else {
                                    "Simulate KuroMix.apk download"
                                },

                            enabled =
                                hyperIslandHookEnabled &&
                                        testIslandEnabled &&
                                        !downloadTestRunning,

                            onClick = {

                                selectedTestEvent =
                                    "download"

                                SuperIslandManager
                                    .startDownloadTest(
                                        context
                                    )

                                downloadTestRunning =
                                    SuperIslandManager
                                        .isDownloadTestRunning()
                            }
                        )

                        // ---------------------------------------------------------
                        // STOP
                        // ---------------------------------------------------------

                        DebugPreference(
                            title =
                                "Stop Download Test",

                            summary =
                                "Stop the current download simulation",

                            enabled =
                                downloadTestRunning,

                            onClick = {

                                stopDownload()

                                if (
                                    selectedTestEvent ==
                                    "download"
                                ) {
                                    selectedTestEvent =
                                        null
                                }
                            }
                        )
                    }
                }

                // =================================================================
                // DEBUG
                // =================================================================

                item {

                    SmallTitle(
                        text = "DEBUG"
                    )

                    Card(
                        modifier = Modifier
                            .padding(
                                horizontal = 12.dp
                            )
                            .fillMaxWidth()
                    ) {

                        DebugPreference(
                            title =
                                "Cancel Test Island",

                            summary =
                                "Remove all KuroMix test notifications",

                            enabled =
                                true,

                            onClick = {

                                cancelTests()
                            }
                        )
                    }
                }
            }

            // =================================================================
            // SCROLL BAR
            // =================================================================

            VerticalScrollBar(
                adapter =
                    rememberScrollBarAdapter(
                        lazyListState
                    ),

                modifier =
                    Modifier
                        .align(
                            Alignment.CenterEnd
                        )
                        .fillMaxHeight()
            )
        }
    }
}


// =============================================================================
// TEST EVENT PREFERENCE
// =============================================================================

@Composable
private fun TestEventPreference(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    RadioButtonPreference(
        title = title,
        summary = summary,
        selected = selected,
        enabled = enabled,
        onClick = onClick
    )
}


// =============================================================================
// DEBUG PREFERENCE
// =============================================================================

@Composable
private fun DebugPreference(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    RadioButtonPreference(
        title = title,
        summary = summary,
        selected = false,
        enabled = enabled,
        onClick = onClick
    )
}