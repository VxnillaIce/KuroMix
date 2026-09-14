package com.kuromify.kuromix.ui

import android.content.Intent
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
import com.kuromify.kuromix.root.RootShell
import com.kuromify.kuromix.service.RearDisplayService
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

    LaunchedEffect(Unit) {
        rearDisplayId = rearDisplays.primaryRearDisplayId()
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
                                    RootShell.setKeepAwakeProp(enabled)
                                    RootShell.setAntiKillProp(enabled)
                                    val intent = Intent(context, RearDisplayService::class.java)
                                    if (enabled) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.stopService(intent)
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    SmallTitle(text = "DISPLAY STATUS")
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = "Rear Display",
                            summary = if (rearDisplayId != null) "Detected (ID: $rearDisplayId)" else "Not Detected",
                            onClick = { rearDisplayId = rearDisplays.primaryRearDisplayId() }
                        )
                    }
                }

                // TODO: break Keep Awake / Anti-Kill into individual toggles here
                // if WhitelistManager exposes separate flows for them.
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