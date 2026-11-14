package dev.charly.mantenimiento.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Haptics dinámicos: combina primitivas de VibrationEffect.Composition (Android 11+) con
 * intensidad variable. Si el motor no soporta una primitiva se usa una parecida, y si no
 * soporta ninguna cae a View.performHapticFeedback (respeta el ajuste del sistema).
 */
class Haptics(context: Context, private val view: View) {

    /** IDs de VibrationEffect.Composition.PRIMITIVE_* y el API en que aparecieron. */
    private enum class Prim(val id: Int, val minSdk: Int) {
        CLICK(1, 30), THUD(2, 31), SPIN(3, 31), QUICK_RISE(4, 30),
        SLOW_RISE(5, 30), QUICK_FALL(6, 30), TICK(7, 30), LOW_TICK(8, 31),
    }

    /** Si el equipo no tiene una primitiva, se prueba con estas en orden. */
    private val substitutes = mapOf(
        Prim.THUD to listOf(Prim.CLICK),
        Prim.SPIN to listOf(Prim.SLOW_RISE, Prim.QUICK_RISE),
        Prim.LOW_TICK to listOf(Prim.TICK),
        Prim.SLOW_RISE to listOf(Prim.QUICK_RISE),
        Prim.QUICK_FALL to listOf(Prim.TICK),
    )

    private class Step(val prim: Prim, val scale: Float, val delayMs: Int = 0)

    var enabled = true
    /** 0..1, multiplica la fuerza de cada primitiva. */
    var intensity = 0.7f

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val supported: Set<Prim> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.hasVibrator() == true) {
            Prim.entries.filter { p ->
                p.minSdk <= Build.VERSION.SDK_INT && vibrator.arePrimitivesSupported(p.id).all { it }
            }.toSet()
        } else emptySet()

    /** true si el equipo soporta las vibraciones avanzadas (primitivas). */
    val rich: Boolean get() = supported.isNotEmpty()

    private var lastTickAt = 0L

    // ---------------- Eventos de la app ----------------

    /** Dedo abajo sobre algo presionable. */
    fun press() = throttled { play(HapticFeedbackConstants.CLOCK_TICK, Step(Prim.LOW_TICK, 0.45f)) }

    /** Tick ligero; [strength] 0..1. */
    fun tick(strength: Float = 0.4f) = throttled { play(HapticFeedbackConstants.CLOCK_TICK, Step(Prim.TICK, strength)) }

    /** Tick de "rueda": más fuerte al inicio y se apaga conforme [remaining] baja a 0. */
    fun roll(remaining: Float) = throttled(gapMs = 28) {
        play(HapticFeedbackConstants.CLOCK_TICK, Step(Prim.TICK, 0.12f + 0.55f * remaining.coerceIn(0f, 1f)))
    }

    /** Tecleo: más firme al escribir que al borrar. */
    fun key(deleting: Boolean) = throttled {
        if (deleting) play(HapticFeedbackConstants.CLOCK_TICK, Step(Prim.LOW_TICK, 0.5f))
        else play(HapticFeedbackConstants.KEYBOARD_TAP, Step(Prim.CLICK, 0.35f))
    }

    fun click(strength: Float = 0.8f) = play(HapticFeedbackConstants.VIRTUAL_KEY, Step(Prim.CLICK, strength))

    /** Algo que se abre o avanza. */
    fun rise(strength: Float = 0.6f) =
        play(HapticFeedbackConstants.CONTEXT_CLICK, Step(Prim.QUICK_RISE, strength), Step(Prim.CLICK, strength * 0.6f, 10))

    /** Algo que se cierra o regresa. */
    fun fall(strength: Float = 0.6f) =
        play(HapticFeedbackConstants.CONTEXT_CLICK, Step(Prim.QUICK_FALL, strength), Step(Prim.LOW_TICK, strength * 0.7f, 10))

    /** Intercambio: sube, gira y "aterriza" al ritmo del resorte del botón. */
    fun swap() = play(
        HapticFeedbackConstants.VIRTUAL_KEY,
        Step(Prim.QUICK_RISE, 0.5f),
        Step(Prim.SPIN, 0.85f, 30),
        Step(Prim.THUD, 1f, 110),
        Step(Prim.LOW_TICK, 0.4f, 90),
    )

    /** Elegir una opción (moneda, color, idioma). */
    fun select() = play(HapticFeedbackConstants.VIRTUAL_KEY, Step(Prim.THUD, 0.5f), Step(Prim.CLICK, 0.9f, 40))

    fun spin() = play(HapticFeedbackConstants.CONTEXT_CLICK, Step(Prim.SPIN, 0.8f), Step(Prim.TICK, 0.5f, 60))

    fun success() = play(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        Step(Prim.CLICK, 0.5f),
        Step(Prim.CLICK, 1f, 70),
    )

    fun error() = play(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        Step(Prim.THUD, 0.8f),
        Step(Prim.THUD, 0.8f, 90),
    )

    /** Modo oscuro: "hundirse" al prender, "subir" al apagar. */
    fun toggle(on: Boolean) =
        if (on) play(HapticFeedbackConstants.VIRTUAL_KEY, Step(Prim.QUICK_FALL, 0.7f), Step(Prim.LOW_TICK, 1f, 20))
        else play(HapticFeedbackConstants.VIRTUAL_KEY, Step(Prim.QUICK_RISE, 0.7f), Step(Prim.CLICK, 0.9f, 20))

    // ---------------- Motor ----------------

    private inline fun throttled(gapMs: Long = 35, block: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt < gapMs) return
        lastTickAt = now
        block()
    }

    private fun resolve(p: Prim): Prim? =
        if (p in supported) p else substitutes[p]?.firstOrNull { it in supported }

    private fun play(fallback: Int, vararg steps: Step) {
        if (!enabled || intensity <= 0.02f) return
        val v = vibrator
        val resolved = steps.map { resolve(it.prim) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v != null && resolved.all { it != null }) {
            val composition = VibrationEffect.startComposition()
            steps.forEachIndexed { i, step ->
                composition.addPrimitive(resolved[i]!!.id, (step.scale * intensity).coerceIn(0f, 1f), step.delayMs)
            }
            val effect = composition.compose()
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                } else {
                    v.vibrate(effect)
                }
            }
        } else {
            view.performHapticFeedback(fallback)
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics no inicializado") }
