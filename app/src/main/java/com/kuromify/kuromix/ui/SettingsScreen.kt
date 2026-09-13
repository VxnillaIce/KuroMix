package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.ui.component.OS3GradientBanner
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { WhitelistManager(context) }
    val darkModePref by whitelistManager.darkModePrefFlow.collectAsState(initial = 0)
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
                        title = "Bypass Global Whitelist",
                        summary = "Allow any app to run on the rear display",
                        checked = true,
                        onCheckedChange = { }
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
