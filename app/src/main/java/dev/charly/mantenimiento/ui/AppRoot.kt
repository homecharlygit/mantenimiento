package dev.charly.mantenimiento.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import dev.charly.mantenimiento.ui.theme.AppTheme

enum class Screen { HOME, SETTINGS, ABOUT }

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings = vm.settings
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember { Haptics(context, view) }
    SideEffect {
        haptics.enabled = settings.haptics
        haptics.intensity = settings.hapticStrength
    }
    CompositionLocalProvider(
        LocalBlurType provides settings.blur,
        LocalHaptics provides haptics,
    ) {
        AppTheme(palette = settings.palette, dark = settings.dark) {
            var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
            // Si una notificación pide abrir un formulario, regresa al inicio
            LaunchedEffect(vm.pendingOpen) { if (vm.pendingOpen != null) screen = Screen.HOME }
            // ---------- Gesto de regreso predictivo (Android 14+) ----------
            // Mientras deslizas, la pantalla se encoge, se redondea y se corre hacia el lado del gesto.
            // En versiones anteriores funciona como un "atrás" normal.
            val backProgress = remember { Animatable(0f) }
            var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
            val scope = rememberCoroutineScope()
            PredictiveBackHandler(enabled = screen != Screen.HOME) { events ->
                var armed = false
                try {
                    events.collect { e ->
                        backEdge = e.swipeEdge
                        backProgress.snapTo(e.progress)
                        // Un tick al pasar el punto en que soltar ya regresa, y otro si te arrepientes
                        val nowArmed = e.progress > 0.35f
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.tick(if (nowArmed) 0.7f else 0.3f)
                        }
                    }
                    screen = Screen.HOME
                    scope.launch {
                        delay(500)
                        backProgress.snapTo(0f)
                    }
                } catch (e: CancellationException) {
                    scope.launch { backProgress.animateTo(0f, Motion.bouncy()) }
                    throw e
                }
            }
            // Avanzar se siente como "subir" y regresar como "bajar"
            var previous by remember { mutableStateOf(screen) }
            LaunchedEffect(screen) {
                if (screen != previous) {
                    if (screen.ordinal > previous.ordinal) haptics.rise() else haptics.fall()
                    previous = screen
                }
            }

            val backdrop = MaterialTheme.colorScheme.surfaceContainerHighest
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    // Detrás de la pantalla que se encoge se asoma un fondo más claro
                    .drawBehind {
                        val p = backProgress.value
                        if (p > 0f) drawRect(backdrop.copy(alpha = p.coerceIn(0f, 1f)))
                    },
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val slide = spring<IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                        val scale = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                        if (targetState.ordinal > initialState.ordinal) {
                            // Hacia adelante: la nueva pantalla entra desde la derecha creciendo
                            (slideInHorizontally(slide) { it / 2 } + scaleIn(scale, initialScale = 0.85f) + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(slide) { -it / 5 } + scaleOut(targetScale = 0.92f) + fadeOut(tween(180)))
                        } else {
                            // Regreso: la pantalla actual se va a la derecha encogiéndose
                            (slideInHorizontally(slide) { -it / 5 } + scaleIn(scale, initialScale = 0.92f) + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(slide) { it / 2 } + scaleOut(targetScale = 0.85f) + fadeOut(tween(180)))
                        }
                    },
                    label = "screens",
                ) { target ->
                    // La pantalla que entra se enfoca y la que sale se desenfoca
                    val blur by transition.animateDp(
                        transitionSpec = { spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) },
                        label = "screenBlur",
                    ) { state -> if (state == EnterExitState.Visible) 0.dp else 24.dp }
                    val predictive = if (target != Screen.HOME) {
                        Modifier.graphicsLayer {
                            val p = backProgress.value.coerceIn(0f, 1f)
                            val sc = 1f - 0.12f * p
                            scaleX = sc
                            scaleY = sc
                            val dir = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                            translationX = dir * size.width * 0.05f * p
                            shape = RoundedCornerShape((40 * p).dp)
                            clip = p > 0f
                        }
                    } else Modifier
                    Box(Modifier.fillMaxSize().then(predictive).appBlur(blur)) {
                    when (target) {
                        Screen.HOME -> HomeScreen(
                            vm = vm,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onOpenAbout = { screen = Screen.ABOUT },
                        )
                        Screen.SETTINGS -> SettingsScreen(vm = vm, onBack = { screen = Screen.HOME })
                        Screen.ABOUT -> AboutScreen(vm = vm, onBack = { screen = Screen.HOME })
                    }
                    }
                }
            }
        }
    }
}
