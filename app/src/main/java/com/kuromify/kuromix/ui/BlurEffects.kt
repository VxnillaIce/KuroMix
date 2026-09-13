package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

@Composable
internal fun rememberKuroMixBlurSupported(): Boolean = remember { isRuntimeShaderSupported() }

@Composable
internal fun rememberKuroMixBackdrop(enabled: Boolean): LayerBackdrop? =
    if (enabled) rememberLayerBackdrop() else null

internal fun Modifier.kuroMixBackdrop(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) layerBackdrop(backdrop) else this

@Composable
internal fun KuroMixBlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean = backdrop != null,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val blendColors = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker)
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight)
            )
        }
    }

    Box(
        modifier = if (backdrop != null && blurEnabled) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 60f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurDefaults.blurColors(
                    blendColors = blendColors
                )
            )
        } else {
            Modifier
        }
    ) {
        content()
    }
}
