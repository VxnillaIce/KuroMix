package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuromify.kuromix.notification.SuperIslandManager
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
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

    var hyperIslandHookEnabled by remember {
        mutableStateOf(
            SuperIslandManager.isHyperIslandHookEnabled(context)
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

    LaunchedEffect(Unit) {
        while (true) {
            val running = SuperIslandManager.isDownloadTestRunning()
            if (downloadTestRunning != running) {
                downloadTestRunning = running
            }
            delay(250L)
        }
    }

    fun stopDownload() {
        SuperIslandManager.stopDownloadTest(context)
        downloadTestRunning = false
    }

    fun cancelTests() {
        stopDownload()
        selectedTestEvent = null
        testIslandEnabled = false
        SuperIslandManager.cancelTestIsland(context)
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                item {
                    SmallTitle(
                        text = "HYPERISLAND"
                    )

                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        SwitchPreference(
                            title = "Enable HyperIsland Hook",
                            summary = if (hyperIslandHookEnabled) {
                                "HyperIsland test notifications are enabled"
                            } else {
                                "Enable HyperIsland test notifications"
                            },
                            checked = hyperIslandHookEnabled,
                            onCheckedChange = { enabled ->
                                hyperIslandHookEnabled = enabled

                                SuperIslandManager.setHyperIslandHookEnabled(
                                    context = context,
                                    enabled = enabled
                                )

                                if (!enabled) {
                                    selectedTestEvent = null
                                    testIslandEnabled = false
                                    downloadTestRunning = false
                                    SuperIslandManager.cancelTestIsland(context)
                                }
                            }
                        )

                        SwitchPreference(
                            title = "Test Island",
                            summary = when {
                                !hyperIslandHookEnabled ->
                                    "Enable HyperIsland first"

                                downloadTestRunning ->
                                    "Download simulation is running"

                                testIslandEnabled ->
                                    "Test events are ready"

                                else ->
                                    "Enable to use test events"
                            },
                            checked = testIslandEnabled,
                            onCheckedChange = { enabled ->
                                if (!hyperIslandHookEnabled) {
                                    return@SwitchPreference
                                }

                                testIslandEnabled = enabled

                                if (!enabled) {
                                    selectedTestEvent = null
                                    stopDownload()
                                    SuperIslandManager.cancelTestIsland(context)
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    SmallTitle(
                        text = "TEST EVENTS"
                    )

                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        val eventsEnabled =
                            hyperIslandHookEnabled && testIslandEnabled

                        TestEventPreference(
                            title = "Charging",
                            summary = "82% • 67W",
                            enabled = eventsEnabled,
                            onClick = {
                                selectedTestEvent = "charging"
                                stopDownload()

                                SuperIslandManager.showChargingTest(
                                    context = context,
                                    battery = 82,
                                    power = 67
                                )
                            }
                        )

                        TestEventPreference(
                            title = "Media",
                            summary = "KuroMix • HyperIsland Demo",
                            enabled = eventsEnabled,
                            onClick = {
                                selectedTestEvent = "media"
                                stopDownload()

                                SuperIslandManager.showMediaTest(
                                    context = context,
                                    artist = "KuroMix",
                                    title = "HyperIsland Demo"
                                )
                            }
                        )

                        TestEventPreference(
                            title = "Timer",
                            summary = "05:00 remaining",
                            enabled = eventsEnabled,
                            onClick = {
                                selectedTestEvent = "timer"
                                stopDownload()

                                SuperIslandManager.showTimerTest(
                                    context = context,
                                    remaining = "05:00"
                                )
                            }
                        )

                        TestEventPreference(
                            title = "Download",
                            summary = if (downloadTestRunning) {
                                "KuroMix.apk • Downloading"
                            } else {
                                "KuroMix.apk • 0% → 100%"
                            },
                            enabled = eventsEnabled,
                            onClick = {
                                selectedTestEvent = "download"

                                if (!downloadTestRunning) {
                                    SuperIslandManager.startDownloadTest(context)
                                    downloadTestRunning =
                                        SuperIslandManager.isDownloadTestRunning()
                                }
                            }
                        )

                        TestEventPreference(
                            title = "Network",
                            summary = "5G • 128 Mbps",
                            enabled = eventsEnabled,
                            onClick = {
                                selectedTestEvent = "network"
                                stopDownload()

                                SuperIslandManager.showNetworkTest(
                                    context = context,
                                    network = "5G",
                                    speed = "128 Mbps"
                                )
                            }
                        )

                        TestEventPreference(
                            title = "Gaming",
                            summary = "120 FPS • 34°C",
                            enabled = eventsEnabled,
                            onClick = {
                                selectedTestEvent = "gaming"
                                stopDownload()

                                SuperIslandManager.showGameTest(
                                    context = context,
                                    fps = 120,
                                    temperature = 34
                                )
                            }
                        )
                    }
                }

                item {
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    SmallTitle(
                        text = "DOWNLOAD"
                    )

                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        DebugPreference(
                            title = "Start Download Test",
                            summary = if (downloadTestRunning) {
                                "Downloading KuroMix.apk"
                            } else {
                                "Simulate KuroMix.apk download"
                            },
                            enabled =
                                hyperIslandHookEnabled &&
                                    testIslandEnabled &&
                                    !downloadTestRunning,
                            onClick = {
                                selectedTestEvent = "download"
                                SuperIslandManager.startDownloadTest(context)
                                downloadTestRunning =
                                    SuperIslandManager.isDownloadTestRunning()
                            }
                        )

                        DebugPreference(
                            title = "Stop Download Test",
                            summary = "Stop the current download simulation",
                            enabled = downloadTestRunning,
                            onClick = {
                                stopDownload()

                                if (selectedTestEvent == "download") {
                                    selectedTestEvent = null
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    SmallTitle(
                        text = "DEBUG"
                    )

                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        DebugPreference(
                            title = "Cancel Test Island",
                            summary = "Remove all KuroMix test notifications",
                            onClick = {
                                cancelTests()
                            }
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TestEventPreference(
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick
    )
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
