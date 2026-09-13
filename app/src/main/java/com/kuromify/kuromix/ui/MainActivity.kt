package com.kuromify.kuromix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.kuromify.kuromix.data.WhitelistManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
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
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val pagerState = rememberPagerState(pageCount = { 3 })
            val coroutineScope = rememberCoroutineScope()
            var isSettingsOpen by remember { mutableStateOf(false) }
            val bottomBackdrop = rememberKuroMixBackdrop()
            val blurSupported = rememberKuroMixBlurSupported()
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

            BackHandler(enabled = isSettingsOpen || pagerState.currentPage != 0) {
                if (isSettingsOpen) {
                    isSettingsOpen = false
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }
            }

            MiuixTheme(controller = themeController) {
                if (isSettingsOpen) {
                    SettingsScreen(onBack = { isSettingsOpen = false })
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.kuroMixBlur(bottomBackdrop, blurSupported),
                                color = if (blurSupported) Color.Transparent else MiuixTheme.colorScheme.surface,
                                showDivider = !blurSupported
                            ) {
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 0,
                                    onClick = {
                                        if (pagerState.currentPage != 0) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                        }
                                    },
                                    icon = MiuixIcons.GridView,
                                    label = "Dashboard"
                                )
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 1,
                                    onClick = {
                                        if (pagerState.currentPage != 1) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                        }
                                    },
                                    icon = MiuixIcons.ScreenMirroring,
                                    label = "Quick Cast"
                                )
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 2,
                                    onClick = {
                                        if (pagerState.currentPage != 2) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                        }
                                    },
                                    icon = MiuixIcons.Info,
                                    label = "About"
                                )
                            }
                        }
                    ) { padding ->
                        val bottomPadding = padding.calculateBottomPadding()
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .kuroMixBackdrop(bottomBackdrop)
                        ) { page ->
                            when (page) {
                                0 -> KuroMixDashboard(
                                    onNavigateToSettings = { isSettingsOpen = true },
                                    bottomPadding = bottomPadding
                                )
                                1 -> QuickCastScreen(bottomPadding = bottomPadding)
                                2 -> AboutScreen(bottomPadding = bottomPadding)
                            }
                        }
                    }
                }
            }
        }
    }
}
