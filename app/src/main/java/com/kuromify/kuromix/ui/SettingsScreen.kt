package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.ui.component.OS3GradientBanner
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { WhitelistManager(context) }

    val darkModePref by whitelistManager.darkModePrefFlow.collectAsState(initial = 0)

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "Settings",
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
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                OS3GradientBanner {
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
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "UI SETTINGS")
                Card {
                    BasicComponent(
                        title = "Dark Mode",
                        summary = when (darkModePref) {
                            1 -> "Light"
                            2 -> "Dark"
                            else -> "Follow System"
                        },
                        onClick = {
                            scope.launch {
                                val next = (darkModePref + 1) % 3
                                whitelistManager.setDarkModePref(next)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "ADVANCED HACKS")
                Card {
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
