package dev.charly.mantenimiento.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    palette: Int,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val target = remember(palette, dark) {
        if (palette == PALETTE_DYNAMIC && supportsDynamicColor) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            schemeFor(PALETTE_HUES[palette.coerceIn(PALETTE_HUES.indices)], dark)
        }
    }

    MaterialExpressiveTheme(
        colorScheme = animateColorScheme(target),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Transición suave de todos los colores al cambiar de paleta o de modo claro/oscuro. */
@Composable
private fun animateColorScheme(t: ColorScheme): ColorScheme = t.copy(
    primary = t.primary.anim(),
    onPrimary = t.onPrimary.anim(),
    primaryContainer = t.primaryContainer.anim(),
    onPrimaryContainer = t.onPrimaryContainer.anim(),
    inversePrimary = t.inversePrimary.anim(),
    secondary = t.secondary.anim(),
    onSecondary = t.onSecondary.anim(),
    secondaryContainer = t.secondaryContainer.anim(),
    onSecondaryContainer = t.onSecondaryContainer.anim(),
    tertiary = t.tertiary.anim(),
    onTertiary = t.onTertiary.anim(),
    tertiaryContainer = t.tertiaryContainer.anim(),
    onTertiaryContainer = t.onTertiaryContainer.anim(),
    background = t.background.anim(),
    onBackground = t.onBackground.anim(),
    surface = t.surface.anim(),
    onSurface = t.onSurface.anim(),
    surfaceVariant = t.surfaceVariant.anim(),
    onSurfaceVariant = t.onSurfaceVariant.anim(),
    surfaceTint = t.surfaceTint.anim(),
    inverseSurface = t.inverseSurface.anim(),
    inverseOnSurface = t.inverseOnSurface.anim(),
    error = t.error.anim(),
    onError = t.onError.anim(),
    errorContainer = t.errorContainer.anim(),
    onErrorContainer = t.onErrorContainer.anim(),
    outline = t.outline.anim(),
    outlineVariant = t.outlineVariant.anim(),
    surfaceBright = t.surfaceBright.anim(),
    surfaceDim = t.surfaceDim.anim(),
    surfaceContainerLowest = t.surfaceContainerLowest.anim(),
    surfaceContainerLow = t.surfaceContainerLow.anim(),
    surfaceContainer = t.surfaceContainer.anim(),
    surfaceContainerHigh = t.surfaceContainerHigh.anim(),
    surfaceContainerHighest = t.surfaceContainerHighest.anim(),
)

@Composable
private fun Color.anim(): Color =
    animateColorAsState(this, spring(stiffness = Spring.StiffnessLow), label = "color").value
