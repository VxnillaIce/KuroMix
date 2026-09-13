package com.kuromify.kuromix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromify.kuromix.R
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.root.RootShell
import com.kuromify.kuromix.ui.component.OS3GradientBanner
import com.kuromify.kuromix.service.RearDisplayService
import com.topjohnwu.superuser.Shell
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

enum class ModuleStatus {
    ACTIVE,
    RESTART_REQUIRED,
    DISABLED
}

@Composable
fun KuroMixDashboard(onNavigateToSettings: () -> Unit, bottomPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { WhitelistManager(context) }

    val rearDisplays = remember { RearDisplayManager(context) }
    var rearDisplayId by remember { mutableStateOf(rearDisplays.primaryRearDisplayId()) }
    var rootReady by remember { mutableStateOf<Boolean?>(null) }
    var status by remember { mutableStateOf("") }
    
    val lensOptimization by whitelistManager.lensOptimizationFlow.collectAsState(initial = false)
    val mirrorModuleEnabled by whitelistManager.mirrorModuleEnabledFlow.collectAsState(initial = true)
    val keepAwakeEnabled by whitelistManager.keepAwakeEnabledFlow.collectAsState(initial = false)
    val antiKillEnabled by whitelistManager.antiKillEnabledFlow.collectAsState(initial = false)
    val replaceMipayEnabled by whitelistManager.replaceMipayEnabledFlow.collectAsState(initial = false)

    val isModuleActive = remember { RootShell.isModuleActive() }

    fun checkRoot() {
        scope.launch {
            rootReady = withContext(kotlinx.coroutines.Dispatchers.IO) { 
                val cached = Shell.getCachedShell()
                if ((cached != null) && !cached.isRoot) {
                    android.util.Log.d("KuroMixDashboard", "[KUROMIX_LOG] Closing non-root cached shell")
                    cached.close()
                }
                RootShell.isRootAvailable() 
            }
        }
    }

    LaunchedEffect(Unit) {
        checkRoot()
    }

    val scrollBehavior = MiuixScrollBehavior()
    val blurSupported = rememberKuroMixBlurSupported()
    val backdrop = rememberKuroMixBackdrop(blurSupported)

    Scaffold(
        topBar = {
            KuroMixBlurredBar(
                backdrop = backdrop,
                blurEnabled = blurSupported
            ) {
                TopAppBar(
                    title = "KuroMix",
                    color = if (blurSupported) Color.Transparent else MiuixTheme.colorScheme.surface,
                    largeTitle = "KuroMix",
                    scrollBehavior = scrollBehavior,
                    actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val scopeApps = context.resources.getStringArray(R.array.xposed_scope)
                                    scopeApps.forEach { pkg ->
                                        if (pkg != context.packageName) {
                                            RootShell.forceStopPackage(pkg)
                                        }
                                    }
                                }
                                status = "Scoped apps restarted"
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart Scoped Apps")
                    }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(imageVector = MiuixIcons.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .kuroMixBackdrop(backdrop)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding + 16.dp
            )
        ) {
            item {
                ActivationStatusCard(
                    status = if (isModuleActive) ModuleStatus.ACTIVE else ModuleStatus.DISABLED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                OS3GradientBanner(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Kuromify The World",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                SmallTitle(text = "DEVICE STATUS")
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    BasicComponent(
                        title = "Root Access",
                        summary = when (rootReady) {
                            null -> "Checking…"
                            true -> "Granted"
                            false -> "Denied - Tap to retry"
                        },
                        onClick = {
                            if (rootReady != true) checkRoot()
                        }
                    )
                    BasicComponent(
                        title = "Rear Display",
                        summary = if (rearDisplayId != null) "Detected (ID: $rearDisplayId)" else "Not Detected"
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = "MODULES")
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    SwitchPreference(
                        title = "Rear Screen Mirror",
                        summary = "Enable/Disable global mirroring features",
                        checked = mirrorModuleEnabled,
                        onCheckedChange = { 
                            scope.launch { whitelistManager.setMirrorModuleEnabled(it) }
                        }
                    )
                    SwitchPreference(
                        title = "Keep Screen Awake",
                        summary = "Prevent sleep on power button or double-tap",
                        checked = keepAwakeEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                whitelistManager.setKeepAwakeEnabled(it)
                                RootShell.setKeepAwakeProp(it)
                                
                                val intent = Intent(context, RearDisplayService::class.java)
                                if (it) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.stopService(intent)
                                }
                            }
                        }
                    )
                    SwitchPreference(
                        title = "Aggressive Anti-Kill",
                        summary = "Prevent process freezing and launcher interference",
                        checked = antiKillEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                whitelistManager.setAntiKillEnabled(it)
                                RootShell.setAntiKillProp(it)
                            }
                        }
                    )
                    SwitchPreference(
                        title = "Replace Mi Pay with GPay",
                        summary = "Remap Mi Pay double-click to Google Wallet",
                        checked = replaceMipayEnabled,
                        onCheckedChange = {
                            scope.launch {
                                whitelistManager.setReplaceMipayEnabled(it)
                                RootShell.setReplaceMipayProp(it)
                            }
                        }
                    )
                    SwitchPreference(
                        title = "Optimize for Lenses",
                        summary = "Push content to the right side",
                        checked = lensOptimization,
                        enabled = mirrorModuleEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                whitelistManager.setLensOptimization(it)
                            }
                        }
                    )
                }
            }

            if (status.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 16.dp)
                    ) {
                        Text(status, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ActivationStatusCard(
    status: ModuleStatus,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val versionText = remember(context) {
        runCatching {
            val packageInfo = context.packageManager
                .getPackageInfo(context.packageName, 0)

            "${packageInfo.versionName.orEmpty()} (${packageInfo.longVersionCode})"
        }.getOrDefault("")
    }

    val cardColor = when (status) {
        ModuleStatus.ACTIVE -> if (darkTheme) Color(0xFF173923) else Color(0xFFDFFAE4)
        ModuleStatus.RESTART_REQUIRED -> if (darkTheme) Color(0xFF463907) else Color(0xFFFFF0C7)
        ModuleStatus.DISABLED -> if (darkTheme) Color(0xFF430D11) else Color(0xFFF8E2E2)
    }

    val accentColor = when (status) {
        ModuleStatus.ACTIVE -> if (darkTheme) Color(0xFF62D783) else Color(0xFF36D167)
        ModuleStatus.RESTART_REQUIRED -> if (darkTheme) Color(0xFFFFB83E) else Color(0xFFE89900)
        ModuleStatus.DISABLED -> if (darkTheme) Color(0xFFFF4D57) else Color(0xFFD93643)
    }

    val title = when (status) {
        ModuleStatus.ACTIVE -> stringResource(R.string.status_activated)
        ModuleStatus.RESTART_REQUIRED -> stringResource(R.string.status_restart_scope)
        ModuleStatus.DISABLED -> stringResource(R.string.status_not_activated)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 14.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )

            if (versionText.isNotEmpty()) {
                Text(
                    text = versionText,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 43.dp),
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = stringResource(R.string.xposed_api_version, RootShell.getXposedApiLevel()),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp),
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )

            StatusSymbol(
                status = status,
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 27.dp, y = 31.dp)
                    .size(110.dp),
            )
        }
    }
}

@Composable
private fun StatusSymbol(
    status: ModuleStatus,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.075f

        when (status) {
            ModuleStatus.ACTIVE -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.34f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = stroke),
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.31f, h * 0.51f),
                    end = Offset(w * 0.44f, h * 0.63f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.44f, h * 0.63f),
                    end = Offset(w * 0.70f, h * 0.36f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            ModuleStatus.RESTART_REQUIRED -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.17f)
                    lineTo(w * 0.84f, h * 0.78f)
                    lineTo(w * 0.16f, h * 0.78f)
                    close()
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = stroke,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.37f),
                    end = Offset(w * 0.50f, h * 0.56f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )

                drawCircle(
                    color = color,
                    radius = stroke * 0.55f,
                    center = Offset(w * 0.50f, h * 0.68f),
                )
            }

            ModuleStatus.DISABLED -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.34f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = stroke),
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.28f, h * 0.72f),
                    end = Offset(w * 0.72f, h * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
