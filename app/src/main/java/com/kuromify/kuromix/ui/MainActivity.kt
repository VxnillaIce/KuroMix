package com.kuromify.kuromix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.kuromify.kuromix.data.WhitelistManager
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val whitelistManager = WhitelistManager(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val backStack = remember { mutableStateListOf<Any>(0) }
            var isSettingsOpen by remember { mutableStateOf(false) }
            
            val darkModePref by whitelistManager.darkModePrefFlow.collectAsState(initial = 0)
            
            val themeController = remember(darkModePref) {
                ThemeController(
                    colorSchemeMode = when (darkModePref) {
                        1 -> ColorSchemeMode.Light
                        2 -> ColorSchemeMode.Dark
                        else -> ColorSchemeMode.System
                    }
                )
            }

            BackHandler(enabled = isSettingsOpen || backStack.size > 1 || (backStack.firstOrNull() as? Int != 0)) {
                if (isSettingsOpen) {
                    isSettingsOpen = false
                } else if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                } else {
                    backStack.clear()
                    backStack.add(0)
                }
            }

            MiuixTheme(controller = themeController) {
                if (isSettingsOpen) {
                    SettingsScreen(onBack = { isSettingsOpen = false })
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                val selectedTab = backStack.lastOrNull() as? Int ?: 0
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { 
                                        if (selectedTab != 0) {
                                            backStack.clear()
                                            backStack.add(0)
                                        }
                                    },
                                    icon = MiuixIcons.GridView,
                                    label = "Dashboard"
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { 
                                        if (selectedTab != 1) {
                                            backStack.clear()
                                            backStack.add(1)
                                        }
                                    },
                                    icon = MiuixIcons.ScreenMirroring,
                                    label = "Quick Cast"
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { 
                                        if (selectedTab != 2) {
                                            backStack.clear()
                                            backStack.add(2)
                                        }
                                    },
                                    icon = MiuixIcons.Info,
                                    label = "About"
                                )
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                            NavDisplay(
                                modifier = Modifier.fillMaxSize(),
                                backStack = backStack,
                                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                                entryProvider = { screen ->
                                    NavEntry(screen) {
                                        when (screen as? Int) {
                                            0 -> KuroMixDashboard(onNavigateToSettings = { isSettingsOpen = true })
                                            1 -> QuickCastScreen()
                                            2 -> AboutScreen()
                                            else -> {}
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
