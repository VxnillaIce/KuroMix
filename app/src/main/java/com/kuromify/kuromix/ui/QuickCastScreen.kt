package com.kuromify.kuromix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.kuromify.kuromix.data.AppConfig
import com.kuromify.kuromix.data.AppInfo
import com.kuromify.kuromix.data.AppRepository
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun QuickCastScreen(bottomPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { WhitelistManager(context) }
    val rearDisplays = remember { RearDisplayManager(context) }

    var isEditMode by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val perAppConfig by whitelistManager.perAppConfigFlow.collectAsState(initial = emptyMap())
    val lensOptimizationGlobal by whitelistManager.lensOptimizationFlow.collectAsState(initial = false)

    var selectedAppForConfig by remember { mutableStateOf<AppInfo?>(null) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            AppRepository.launchableApps(context).filter {
                it.packageName != context.packageName
            }
        }
        isLoading = false
    }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Quick Cast",
                largeTitle = "Quick Cast",
                scrollBehavior = scrollBehavior,
                actions = {
                    if (isEditMode) {
                        val allEnabled = apps.all {
                            perAppConfig[it.packageName]?.enabled ?: true
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    whitelistManager.updateBatchAppConfig(
                                        apps.map { it.packageName },
                                        !allEnabled
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (allEnabled) {
                                    Icons.Default.RemoveDone
                                } else {
                                    Icons.Default.DoneAll
                                },
                                contentDescription = if (allEnabled) {
                                    "Deselect All"
                                } else {
                                    "Select All"
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isEditMode = !isEditMode
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditMode) {
                                MiuixIcons.Ok
                            } else {
                                MiuixIcons.Edit
                            },
                            contentDescription = "Edit"
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
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        },
                        label = "Search applications...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    )
                }

                item {
                    SmallTitle(
                        text = "MIRRORABLE APPLICATIONS"
                    )

                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (filteredApps.isEmpty()) {
                            BasicComponent(
                                title = if (searchQuery.isEmpty()) {
                                    "No apps to show"
                                } else {
                                    "No results for \"$searchQuery\""
                                },
                                summary = if (searchQuery.isEmpty()) {
                                    "Scanning for launchable apps…"
                                } else {
                                    "Try a different search term"
                                }
                            )
                        } else {
                            filteredApps
                                .filter { app ->
                                    if (isEditMode) {
                                        true
                                    } else {
                                        perAppConfig[app.packageName]?.enabled ?: true
                                    }
                                }
                                .forEach { app ->
                                    val config = perAppConfig[app.packageName] ?: AppConfig()

                                    AppItem(
                                        app = app,
                                        isEditMode = isEditMode,
                                        isEnabled = config.enabled,
                                        onToggle = { enabled ->
                                            scope.launch {
                                                whitelistManager.updateAppConfig(
                                                    app.packageName,
                                                    config.copy(enabled = enabled)
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (!isEditMode) {
                                                selectedAppForConfig = app
                                            }
                                        }
                                    )
                                }
                        }
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
                            Text(
                                text = status,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            selectedAppForConfig?.let { app ->
                val initialConfig = perAppConfig[app.packageName] ?: AppConfig()

                AppConfigBottomSheet(
                    app = app,
                    initialConfig = initialConfig,
                    lensOptimizationGlobal = lensOptimizationGlobal,
                    onDismiss = {
                        selectedAppForConfig = null
                    },
                    onSave = { config ->
                        scope.launch {
                            whitelistManager.updateAppConfig(
                                app.packageName,
                                config
                            )

                            val targetDisplay =
                                rearDisplays.primaryRearDisplayId() ?: 1

                            val taskId = withContext(Dispatchers.IO) {
                                RootShell.getTaskIdForPackage(app.packageName)
                            }

                            if (taskId != null) {
                                val offset = if (lensOptimizationGlobal) {
                                    config.lensOffset
                                } else {
                                    0
                                }

                                RootShell.applyDisplayOffset(
                                    targetDisplay,
                                    offset
                                )

                                RootShell.setDisplayDpi(
                                    targetDisplay,
                                    config.dpi
                                )

                                status = "Settings applied in real-time"
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppConfigBottomSheet(
    app: AppInfo,
    initialConfig: AppConfig,
    lensOptimizationGlobal: Boolean,
    onDismiss: () -> Unit,
    onSave: (AppConfig) -> Unit
) {
    var config by remember(app.packageName) {
        mutableStateOf(initialConfig)
    }

    var showSheet by remember(app.packageName) {
        mutableStateOf(true)
    }

    OverlayBottomSheet(
        show = showSheet,
        title = "Config: ${app.label}",
        startAction = {
            IconButton(
                onClick = {
                    showSheet = false
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "Close"
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    onSave(config)
                    showSheet = false
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "Save"
                )
            }
        },
        onDismissRequest = {
            showSheet = false
        },
        onDismissFinished = onDismiss
    ) {
        SmallTitle(
            text = "DISPLAY SETTINGS"
        )

        Card(
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer
            )
        ) {
            SliderPreference(
                title = "DPI",
                summary = "${config.dpi} DPI",
                value = config.dpi.toFloat(),
                onValueChange = {
                    config = config.copy(
                        dpi = it.toInt()
                    )
                },
                valueRange = 200f..600f,
                steps = 40
            )

            BasicComponent(
                title = "Reset DPI to Default",
                summary = "Revert to 320 DPI",
                onClick = {
                    config = config.copy(
                        dpi = 320
                    )
                }
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Card(
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer
            )
        ) {
            SliderPreference(
                title = "Lens Offset",
                summary = "${config.lensOffset} px",
                value = config.lensOffset.toFloat(),
                onValueChange = {
                    config = config.copy(
                        lensOffset = it.toInt()
                    )
                },
                valueRange = 0f..800f,
                steps = 80,
                enabled = lensOptimizationGlobal
            )

            BasicComponent(
                title = "Reset Offset to Default",
                summary = "Revert to 450px",
                enabled = lensOptimizationGlobal,
                onClick = {
                    config = config.copy(
                        lensOffset = 450
                    )
                }
            )
        }
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    isEditMode: Boolean,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val icon =
                    context.packageManager.getApplicationIcon(app.packageName)

                iconBitmap =
                    icon.toBitmap().asImageBitmap()
            } catch (_: Exception) {
            }
        }
    }

    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = app.label,
        summary = app.packageName,
        startAction = {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        endActions = {
            if (isEditMode) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
        },
        onClick = onClick
    )
}
