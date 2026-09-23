package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.launch
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
fun RearScreen(
    bottomPadding: Dp = 0.dp,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { WhitelistManager(context) }
    val rearDisplays = remember { RearDisplayManager(context) }
    var rearDisplayId by remember { mutableStateOf(rearDisplays.primaryRearDisplayId()) }
    val mirrorModuleEnabled by whitelistManager.mirrorModuleEnabledFlow.collectAsState(initial = false)

    // Read once on enter; refreshed when the screen is recomposed after
    // the user toggles HyperIsland elsewhere.
    var hyperIslandEnabled by remember {
        mutableStateOf(SuperIslandManager.isHyperIslandHookEnabled(context))
    }

    LaunchedEffect(Unit) {
        rearDisplayId = rearDisplays.primaryRearDisplayId()
        hyperIslandEnabled = SuperIslandManager.isHyperIslandHookEnabled(context)
    }

    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Rear Screen Mirroring",
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
                item {
                    SmallTitle(text = "MIRRORING")
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        SwitchPreference(
                            title = "Enable Mirroring",
                            summary = "Mirror this device's display to the rear screen",
                            checked = mirrorModuleEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    whitelistManager.setMirrorModuleEnabled(enabled)

                                    // These two properties are the entire
                                    // persistence mechanism now. The LSPosed
                                    // hook in KuroMixHook reacts to them in
                                    // system_server, keeping the rear display
                                    // awake and the mirror process alive.
                                    // No foreground service is needed.
                                    RootShell.setKeepAwakeProp(enabled)
                                    RootShell.setAntiKillProp(enabled)
                                }
                            }
                        )
                    }
                }

                item {
                    SmallTitle(text = "NOTIFICATIONS")
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = if (hyperIslandEnabled) {
                                "HyperIsland is enabled"
                            } else {
                                "HyperIsland is disabled"
                            },
                            summary = if (hyperIslandEnabled) {
                                "A HyperIsland card will appear for the " +
                                        "mirrored app, showing its name and " +
                                        "a Stop action."
                            } else {
                                "Enable HyperIsland in the HyperIsland " +
                                        "screen to show a card for the " +
                                        "mirrored app. Until then, no " +
                                        "notification will appear."
                            },
                            onClick = {
                                // Refresh the state when the user taps,
                                // in case they toggled it in the other screen.
                                hyperIslandEnabled =
                                    SuperIslandManager.isHyperIslandHookEnabled(context)
                            }
                        )
                    }
                }

                item {
                    SmallTitle(text = "DISPLAY STATUS")
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = "Rear Display",
                            summary = if (rearDisplayId != null) {
                                "Detected (ID: $rearDisplayId)"
                            } else {
                                "Not Detected"
                            },
                            onClick = {
                                rearDisplayId = rearDisplays.primaryRearDisplayId()
                            }
                        )
                    }
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