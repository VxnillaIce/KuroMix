package com.kuromify.kuromix.ui.component

import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.sin

private const val OS3_BG_FRAG = """
uniform vec2 uResolution;
uniform float uAnimTime;
uniform vec4 uBound;
uniform float uTranslateY;
uniform vec3 uPoints[4];
uniform vec2 uPointsAnim[4];
uniform vec4 uColors[4];
uniform float uAlphaMulti;
uniform float uNoiseScale;
uniform float uPointRadiusMulti;
uniform float uSaturateOffset;
uniform float uLightOffset;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.13);
    p3 += dot(p3, p3.yzx + 3.333);
    return fract((p3.x + p3.y) * p3.z);
}

float perlin(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float gradientNoise(in vec2 uv) {
    return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
}

vec4 main(vec2 fragCoord) {
    vec2 vUv = fragCoord/uResolution;
    vUv.y = 1.0-vUv.y;
    vec2 uv = vUv;
    uv -= vec2(0., uTranslateY);
    uv.xy -= uBound.xy;
    uv.xy /= uBound.zw;

    vec4 color = vec4(0.0);
    float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime * 0.1, -uAnimTime * 0.1));

    for (int i = 0; i < 4; i++) {
        vec4 pointColor = uColors[i];
        pointColor.rgb *= pointColor.a;
        vec2 point = uPointsAnim[i];
        float rad = uPoints[i].z * uPointRadiusMulti;
        float d = distance(uv, point);
        float pct = smoothstep(rad, 0., d);
        color.rgb = mix(color.rgb, pointColor.rgb, pct);
        color.a = mix(color.a, pointColor.a, pct);
    }

    float oppositeNoise = smoothstep(0., 1., noiseValue);
    color.rgb /= color.a;
    vec3 hsv = rgb2hsv(color.rgb);
    hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
    color.rgb = hsv2rgb(hsv);
    color.rgb += oppositeNoise * uLightOffset;
    color.a = clamp(color.a, 0., 1.);
    color.a *= uAlphaMulti;
    color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);

    return vec4(color.rgb * color.a, color.a);
}
"""

class OS3GradientPainter(private val isDark: Boolean) {
    private val shader = RuntimeShader(OS3_BG_FRAG)
    private val brush = ShaderBrush(shader)

    private val colorsLight = listOf(
        floatArrayOf(1.0f, 0.9f, 0.94f, 1.0f, 1.0f, 0.84f, 0.89f, 1.0f, 0.97f, 0.73f, 0.82f, 1.0f, 0.64f, 0.65f, 0.98f, 1.0f),
        floatArrayOf(0.58f, 0.74f, 1.0f, 1.0f, 1.0f, 0.9f, 0.93f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
        floatArrayOf(0.98f, 0.86f, 0.9f, 1.0f, 0.6f, 0.73f, 0.98f, 1.0f, 0.92f, 0.93f, 1.0f, 1.0f, 0.56f, 0.69f, 1.0f, 1.0f)
    )

    private val colorsDark = listOf(
        floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
        floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
        floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f)
    )

    private val pointsPreset = floatArrayOf(
        0.8f, 0.2f, 1.0f,
        0.8f, 0.9f, 1.0f,
        0.2f, 0.9f, 1.0f,
        0.2f, 0.2f, 1.0f
    )

    init {
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uAlphaMulti", 1f)
        shader.setFloatUniform("uBound", 0f, 0f, 1f, 1f)
        shader.setFloatUniform("uPoints", pointsPreset)

        val (lightOffset, saturateOffset) = if (isDark) 0.0f to 0.17f else 0.1f to 0.2f
        shader.setFloatUniform("uLightOffset", lightOffset)
        shader.setFloatUniform("uSaturateOffset", saturateOffset)
    }

    fun update(time: Float, width: Float, height: Float) {
        shader.setFloatUniform("uResolution", width, height)
        shader.setFloatUniform("uAnimTime", time)
        val pointOffset = if (isDark) 0.4f else 0.2f
        val pointsAnim = FloatArray(8)
        for (i in 0 until 4) {
            val srcX = pointsPreset[i * 3]
            val srcY = pointsPreset[i * 3 + 1]
            pointsAnim[i * 2] = srcX + sin(time * 0.15f + srcY) * pointOffset
            pointsAnim[i * 2 + 1] = srcY + cos(time * 0.15f + srcX) * pointOffset
        }
        shader.setFloatUniform("uPointsAnim", pointsAnim)
        val colorSets = if (isDark) colorsDark else colorsLight
        val period = if (isDark) 20.0f else 15.0f
        val totalProgress = (time / period) % 3.0f
        val currentSetIdx = totalProgress.toInt()
        val nextSetIdx = (currentSetIdx + 1) % 3
        val stage = totalProgress - currentSetIdx

        val interpolatedColors = FloatArray(16)
        val currentSet = colorSets[currentSetIdx]
        val nextSet = colorSets[nextSetIdx]
        for (i in 0 until 16) {
            interpolatedColors[i] = currentSet[i] + (nextSet[i] - currentSet[i]) * stage
        }
        shader.setFloatUniform("uColors", interpolatedColors)
    }

    fun getBrush(): Brush = brush
}

@Composable
fun OS3GradientBanner(
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val isDark = MiuixTheme.colorSchemeMode?.let { it == top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark } ?: false
    val painter = remember(isDark) { OS3GradientPainter(isDark) }

    val infiniteTransition = rememberInfiniteTransition(label = "os3_anim")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            painter.update(time, size.width, size.height)
            drawRect(painter.getBrush())
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun OS3GradientBackground(
    modifier: Modifier = Modifier
) {
    val isDark =
        MiuixTheme.colorSchemeMode ==
            top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark

    val painter = remember(isDark) {
        OS3GradientPainter(isDark)
    }

    val infiniteTransition =
        rememberInfiniteTransition(
            label = "os3_background_anim"
        )

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "background_time"
    )

    Canvas(
        modifier = modifier
    ) {
        painter.update(
            time = time,
            width = size.width,
            height = size.height
        )

        drawRect(
            brush = painter.getBrush()
        )
    }
}
