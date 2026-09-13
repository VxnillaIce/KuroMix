package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.root.RootShell
import com.kuromify.kuromix.ui.component.OS3GradientBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { WhitelistManager(context) }
    val rearManager = remember { RearDisplayManager(context) }

    val darkModePref by whitelistManager.darkModePrefFlow.collectAsState(initial = 0)
    var status by remember { mutableStateOf("") }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "Settings",
                largeTitle = "Settings",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item {
                OS3GradientBanner(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Global Preferences",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                SmallTitle(text = "MIRRORING TOOLS")
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    BasicComponent(
                        title = "Test Mirror (Foreground)",
                        summary = "Test your current global display area config",
                        onClick = {
                            val targetDisplay = rearManager.primaryRearDisplayId() ?: 1
                            scope.launch {
                                status = "Testing mirror on display $targetDisplay…"
                                val result = withContext(Dispatchers.IO) {
                                    RootShell.moveCurrentTaskToDisplay(targetDisplay)
                                }
                                status = if (result.ok) "Test successful" else "Test failed"
                            }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "UI SETTINGS")
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    OverlayDropdownPreference(
                        title = "Dark Mode",
                        items = listOf("Follow System", "Light", "Dark"),
                        selectedIndex = darkModePref.coerceIn(0, 2),
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                whitelistManager.setDarkModePref(index)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "ADVANCED HACKS")
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    SwitchPreference(
                        title = "Keep Alive on AOD",
                        summary = "Prevent apps from closing when device locks",
                        checked = true,
                        onCheckedChange = { }
                    )
                    SwitchPreference(
                        title = "Bypass Global Whitelist",
                        summary = "Allow any app to run on the rear display",
                        checked = true,
                        onCheckedChange = { }
                    )
                }
                
                if (status.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 16.dp)
                    ) {
                        Text(status, modifier = Modifier.padding(12.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
