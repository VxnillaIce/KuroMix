package com.kuromify.kuromix.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kuromify.kuromix.effect.BgEffectBackground
import com.kuromify.kuromix.root.RootShell
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.core.net.toUri

// ── Reference URLs ─────────────────────────────────────────────
private const val URL_VXNILLA = "https://github.com/VxnillaIce"
private const val URL_DINOPIG = "https://github.com/dinopig1219"
private const val URL_YUKONGA = "https://github.com/YuKongA"
private const val URL_HYPERCEILER = "https://github.com/ReChronoRain/HyperCeiler"
private const val URL_HYPERISLAND_KIT = "https://github.com/D4vidDf"
private const val URL_KUROMIX_REPO = "https://github.com/VxnillaIce/KuroMix"

@Composable
fun AboutScreen(
    bottomPadding: Dp = 0.dp,
    onBack: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoSpacerHeightPx by remember { mutableStateOf(0) }
    val blurSupported = remember { isRuntimeShaderSupported() }
    val topBarBackdrop = rememberAboutBackdrop(blurSupported)

    val scrollProgress by remember {
        derivedStateOf {
            if (logoSpacerHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset

                if (index > 0) {
                    1f
                } else {
                    (offset.toFloat() / logoSpacerHeightPx)
                        .coerceIn(0f, 1f)
                }
            }
        }
    }

    val collapsed by remember {
        derivedStateOf {
            scrollProgress >= 0.999f
        }
    }

    val blurActive by remember(topBarBackdrop) {
        derivedStateOf {
            topBarBackdrop != null &&
                    scrollProgress >= 0.999f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val barColor = if (blurActive) {
                    Color.Transparent
                } else if (collapsed) {
                    MiuixTheme.colorScheme.surface
                } else {
                    Color.Transparent
                }

                AboutBlurredBar(
                    backdrop = topBarBackdrop,
                    blurEnabled = blurActive,
                ) {
                    TopAppBar(
                        title = "About",
                        largeTitle = "",
                        scrollBehavior = scrollBehavior,
                        color = barColor,
                        titleColor =
                            MiuixTheme.colorScheme.onSurface.copy(
                                alpha = scrollProgress,
                            ),
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "Back",
                                    tint =
                                        MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = if (topBarBackdrop != null) {
                    Modifier.layerBackdrop(
                        topBarBackdrop,
                    )
                } else {
                    Modifier
                },
            ) {
                KuroMixAboutContent(
                    scrollBehavior = scrollBehavior,
                    padding = padding,
                    lazyListState = lazyListState,
                    scrollProgress = scrollProgress,
                    onLogoSpacerHeightChanged = {
                        logoSpacerHeightPx = it
                    },
                    blurSupported = blurSupported,
                    bottomPadding = bottomPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun KuroMixAboutContent(
    scrollBehavior: ScrollBehavior,
    padding: PaddingValues,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoSpacerHeightChanged: (Int) -> Unit,
    blurSupported: Boolean,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val packageInfo = remember(context) {
        context.packageManager.getPackageInfo(
            context.packageName,
            0,
        )
    }
    val versionName = packageInfo.versionName.orEmpty()
    val versionCode = packageInfo.longVersionCode

    val appIconBitmap = remember(context) {
        context.packageManager
            .getApplicationIcon(context.packageName)
            .toBitmap(width = 192, height = 192)
            .asImageBitmap()
    }

    var hyperOSVersion by remember { mutableStateOf("Checking…") }
    var deviceName by remember { mutableStateOf("Checking…") }

    LaunchedEffect(Unit) {
        hyperOSVersion = RootShell.getHyperOSVersion()
        deviceName = RootShell.getMarketName()
    }

    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val backdrop = rememberAboutBackdrop(blurSupported)

    val cardBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
            )
        }
    }

    var logoHeightDp by remember { mutableStateOf(0.dp) }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (backdrop != null) {
            Modifier.layerBackdrop(backdrop)
        } else {
            Modifier
        },
        alpha = { 1f - scrollProgress },
    ) { }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = padding.calculateTopPadding() + 40.dp)
            .onSizeChanged { size ->
                with(density) {
                    logoHeightDp = size.height.toDp()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val iconProgress =
                        ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                    clip = true
                    shape = RoundedCornerShape(24.dp)
                    alpha = 1f - iconProgress
                    scaleX = 1f - iconProgress * 0.05f
                    scaleY = 1f - iconProgress * 0.05f
                },
        ) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = "KuroMix",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            fontSize = 35.sp,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 5.dp)
                .graphicsLayer {
                    val projectNameProgress =
                        ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - projectNameProgress
                    scaleX = 1f - projectNameProgress * 0.05f
                    scaleY = 1f - projectNameProgress * 0.05f
                }
                .then(
                    if (backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val versionProgress =
                        ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                    alpha = 1f - versionProgress
                    scaleX = 1f - versionProgress * 0.05f
                    scaleY = 1f - versionProgress * 0.05f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$versionName ($versionCode)",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = bottomPadding + 16.dp,
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(logoHeightDp + 52.dp + 40.dp + 82.dp)
                        .onSizeChanged { size ->
                            onLogoSpacerHeightChanged(size.height)
                        },
                )
            }

            item(key = "aboutContent") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    SmallTitle(text = "DEVICE INFO")

                    PigLauncherGlassCard(
                        backdrop = backdrop,
                        cardBlend = cardBlend,
                    ) {
                        BasicComponent(
                            title = "Device Model",
                            summary = deviceName,
                        )
                        BasicComponent(
                            title = "System Version",
                            summary = "HyperOS $hyperOSVersion",
                        )
                        BasicComponent(
                            title = "Android Version",
                            summary = Build.VERSION.RELEASE,
                        )
                    }

                    SmallTitle(text = "ABOUT APP")

                    PigLauncherGlassCard(
                        backdrop = backdrop,
                        cardBlend = cardBlend,
                    ) {
                        ArrowPreference(
                            title = "Developer",
                            summary = "@VxnillaIce",
                            onClick = { openUrl(context, URL_VXNILLA) },
                        )
                        ArrowPreference(
                            title = "Contributors",
                            summary = "@dinopig1219",
                            onClick = { openUrl(context, URL_DINOPIG) },
                        )
                        ArrowPreference(
                            title = "GitHub",
                            summary = "KuroMix Official GitHub page",
                            onClick = { openUrl(context, URL_KUROMIX_REPO) },
                        )
                    }

                    SmallTitle(text = "REFERENCES")

                    PigLauncherGlassCard(
                        backdrop = backdrop,
                        cardBlend = cardBlend,
                    ) {
                        ArrowPreference(
                            title = "UI Toolkit",
                            summary = "MIUIX Library by YukongA",
                            onClick = { openUrl(context, URL_YUKONGA) },
                        )
                        ArrowPreference(
                            title = "Visual Effects",
                            summary = "HyperCeiler Module",
                            onClick = { openUrl(context, URL_HYPERCEILER) },
                        )
                        ArrowPreference(
                            title = "HyperIsland",
                            summary = "HyperIsland-Kit by D4vidDf",
                            onClick = { openUrl(context, URL_HYPERISLAND_KIT) },
                        )
                        BasicComponent(
                            title = "Inspiration",
                            summary = "ZHITool and REAREye Github",
                        )
                    }
                }
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

// ── URL helper ─────────────────────────────────────────────────
private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun PigLauncherGlassCard(
    backdrop: LayerBackdrop?,
    cardBlend: List<BlendColorEntry>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurDefaults.blurColors(blendColors = cardBlend),
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.defaultColors(
            color = if (backdrop != null) {
                Color.Transparent
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            },
            contentColor = Color.Transparent,
        ),
        content = content,
    )
}

@Composable
private fun rememberAboutBackdrop(
    blurSupported: Boolean,
): LayerBackdrop? =
    if (blurSupported) {
        rememberLayerBackdrop()
    } else {
        null
    }

@Composable
private fun AboutBlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            if (backdrop != null && blurEnabled) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = BlurDefaults.BlurRadius,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurColors(),
                )
            } else {
                Modifier
            },
    ) {
        content()
    }
}