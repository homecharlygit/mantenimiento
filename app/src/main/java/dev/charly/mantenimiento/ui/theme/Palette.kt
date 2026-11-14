package dev.charly.mantenimiento.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Índice especial: colores del wallpaper (Material You, Android 12+). */
const val PALETTE_DYNAMIC = -1

/** Tonos (hue CIELCh) de las paletas, en el mismo orden que Strings.colorNames. */
val PALETTE_HUES = listOf(30f, 55f, 80f, 140f, 195f, 265f, 305f, 350f)

const val DEFAULT_PALETTE = 6 // Morado

/**
 * Generador de paletas tonales estilo Material: "tone" es la luminosidad L* (0–100),
 * igual que en HCT, y el croma se reduce hasta que el color cabe en sRGB.
 */
object Tonal {
    fun color(hue: Float, chroma: Float, tone: Float): Color {
        var c = chroma
        while (c > 0f) {
            lchToColor(tone, c, hue)?.let { return it }
            c -= 1f
        }
        return lchToColor(tone, 0f, hue) ?: Color.Gray
    }

    private fun lchToColor(l: Float, c: Float, hueDeg: Float): Color? {
        val h = Math.toRadians(hueDeg.toDouble())
        val a = c * cos(h)
        val b = c * sin(h)
        val fy = (l + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        fun inv(t: Double) = if (t * t * t > 0.008856) t * t * t else (116.0 * t - 16.0) / 903.3
        val x = 0.95047 * inv(fx)
        val y = if (l > 8f) fy * fy * fy else l / 903.3
        val z = 1.08883 * inv(fz)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        if (r < -1e-4 || r > 1.0001 || g < -1e-4 || g > 1.0001 || bl < -1e-4 || bl > 1.0001) return null
        fun gamma(u: Double) =
            (if (u <= 0.0031308) 12.92 * u else 1.055 * u.pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0).toFloat()
        return Color(gamma(r), gamma(g), gamma(bl))
    }
}

/** Color de muestra de una paleta (lo que se ve en el selector). */
fun swatchColor(hue: Float, dark: Boolean): Color = Tonal.color(hue, 48f, if (dark) 80f else 40f)

/** Esquema de color completo generado a partir de un tono. */
fun schemeFor(hue: Float, dark: Boolean): ColorScheme {
    fun p(t: Int) = Tonal.color(hue, 48f, t.toFloat())
    fun s(t: Int) = Tonal.color(hue, 16f, t.toFloat())
    fun tr(t: Int) = Tonal.color(hue + 60f, 32f, t.toFloat())
    fun n(t: Int) = Tonal.color(hue, 4f, t.toFloat())
    fun nv(t: Int) = Tonal.color(hue, 8f, t.toFloat())
    fun e(t: Int) = Tonal.color(25f, 60f, t.toFloat())

    return if (dark) darkColorScheme(
        primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
        inversePrimary = p(40),
        secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
        tertiary = tr(80), onTertiary = tr(20), tertiaryContainer = tr(30), onTertiaryContainer = tr(90),
        background = n(6), onBackground = n(90),
        surface = n(6), onSurface = n(90),
        surfaceVariant = nv(30), onSurfaceVariant = nv(80), surfaceTint = p(80),
        inverseSurface = n(90), inverseOnSurface = n(20),
        error = e(80), onError = e(20), errorContainer = e(30), onErrorContainer = e(90),
        outline = nv(60), outlineVariant = nv(30), scrim = Color.Black,
        surfaceBright = n(24), surfaceDim = n(6),
        surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
        surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
    ) else lightColorScheme(
        primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
        inversePrimary = p(80),
        secondary = s(40), onSecondary = s(100), secondaryContainer = s(90), onSecondaryContainer = s(10),
        tertiary = tr(40), onTertiary = tr(100), tertiaryContainer = tr(90), onTertiaryContainer = tr(10),
        background = n(98), onBackground = n(10),
        surface = n(98), onSurface = n(10),
        surfaceVariant = nv(90), onSurfaceVariant = nv(30), surfaceTint = p(40),
        inverseSurface = n(20), inverseOnSurface = n(95),
        error = e(40), onError = e(100), errorContainer = e(90), onErrorContainer = e(10),
        outline = nv(50), outlineVariant = nv(80), scrim = Color.Black,
        surfaceBright = n(98), surfaceDim = n(87),
        surfaceContainerLowest = n(100), surfaceContainerLow = n(96), surfaceContainer = n(94),
        surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
    )
}
