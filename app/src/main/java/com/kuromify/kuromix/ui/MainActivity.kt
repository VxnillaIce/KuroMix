package com.kuromify.kuromix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val whitelistManager = WhitelistManager(this)

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val pagerState = rememberPagerState(pageCount = { 3 })
            val coroutineScope = rememberCoroutineScope()
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
                AnimatedContent(
                    targetState = isSettingsOpen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (targetState) {
                            slideInHorizontally { it } togetherWith
                                slideOutHorizontally { -it / 3 }
                        } else {
                            slideInHorizontally { -it / 3 } togetherWith
                                slideOutHorizontally { it }
                        }
                    },
                    label = "settingsTransition"
                ) { settingsOpen ->
                    if (settingsOpen) {
                        SettingsScreen(onBack = { isSettingsOpen = false })
                    } else {
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = pagerState.currentPage == 0,
                                        onClick = {
                                            if (pagerState.currentPage != 0) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(0)
                                                }
                                            }
                                        },
                                        icon = MiuixIcons.GridView,
                                        label = "Dashboard"
                                    )
                                    NavigationBarItem(
                                        selected = pagerState.currentPage == 1,
                                        onClick = {
                                            if (pagerState.currentPage != 1) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(1)
                                                }
                                            }
                                        },
                                        icon = MiuixIcons.ScreenMirroring,
                                        label = "Quick Cast"
                                    )
                                    NavigationBarItem(
                                        selected = pagerState.currentPage == 2,
                                        onClick = {
                                            if (pagerState.currentPage != 2) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(2)
                                                }
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
                                modifier = Modifier.fillMaxSize()
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
}
