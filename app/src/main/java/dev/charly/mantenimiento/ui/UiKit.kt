package dev.charly.mantenimiento.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Resortes con más rebote que los del sistema, para animaciones más marcadas. */
object Motion {
    fun <T> bouncy() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    fun <T> wobbly() = spring<T>(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow)
    fun <T> snappy() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
}

/** Escala que se "aplasta" al presionar y rebota al soltar. */
@Composable
fun rememberPressScale(
    source: MutableInteractionSource,
    pressedScale: Float = 0.9f,
    haptic: Boolean = true,
): Float {
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    LaunchedEffect(pressed) { if (pressed && haptic) haptics.press() }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.bouncy(),
        label = "press",
    )
    return scale
}

/** Entrada escalonada: cada elemento sube, crece y aparece con rebote. */
@Composable
fun Modifier.staggeredEnter(index: Int, enabled: Boolean = true): Modifier {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 65L)
            progress.animateTo(1f, Motion.bouncy())
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 72.dp.toPx()
        val s = 0.88f + 0.12f * p
        scaleX = s
        scaleY = s
    }
}

/** Número que "rueda" con resorte hasta su nuevo valor. */
@Composable
fun animatedNumber(target: Double, onRoll: ((remaining: Float) -> Unit)? = null): Double {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableDoubleStateOf(target) }
    var to by remember { mutableDoubleStateOf(target) }
    LaunchedEffect(target) {
        if (target != to) {
            from += (to - from) * progress.value
            to = target
            progress.snapTo(0f)
            var lastStep = 0
            progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) {
                // 10 "dientes" por animación: el tick se va apagando conforme el número frena
                val step = (value * 10).toInt()
                if (step != lastStep && value < 1f) {
                    lastStep = step
                    onRoll?.invoke(1f - value)
                }
            }
        }
    }
    return from + (to - from) * progress.value
}

/** Esquinas tipo "lista segmentada" de M3 Expressive: grandes en los extremos, chicas en medio. */
fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(big, big, small, small)
        index == count - 1 -> RoundedCornerShape(small, small, big, big)
        else -> RoundedCornerShape(small)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 24.dp, bottom = 10.dp),
    )
}
