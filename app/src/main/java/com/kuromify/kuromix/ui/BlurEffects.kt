package com.kuromify.kuromix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

@Composable
internal fun rememberKuroMixBackdrop(): LayerBackdrop = rememberLayerBackdrop()

@Composable
internal fun rememberKuroMixBlurSupported(): Boolean = remember { isRuntimeShaderSupported() }

internal fun Modifier.kuroMixBackdrop(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

internal fun Modifier.kuroMixBlur(backdrop: LayerBackdrop, enabled: Boolean): Modifier =
    if (enabled) {
        textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 20f
        )
    } else {
        this
    }
