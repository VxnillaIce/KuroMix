package com.kuromify.kuromix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlurColors
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
    Box(
        modifier = if (backdrop != null && blurEnabled) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 36f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurColors()
            )
        } else {
            Modifier
        }
    ) {
        content()
    }
}
