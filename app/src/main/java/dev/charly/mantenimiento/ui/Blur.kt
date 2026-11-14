package dev.charly.mantenimiento.ui

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import android.graphics.RenderEffect as PlatformRenderEffect

enum class BlurType(val code: String) {
    KAWASE("kawase"),
    GAUSSIAN("gaussian");

    companion object {
        fun from(code: String?): BlurType = entries.firstOrNull { it.code == code } ?: KAWASE
    }
}

object BlurSupport {
    /** RenderEffect (Gaussiano) existe desde Android 12. */
    val gaussian: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    /** RuntimeShader (AGSL, para Kawase) existe desde Android 13. */
    val kawase: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

val LocalBlurType = staticCompositionLocalOf { BlurType.KAWASE }

/**
 * Desenfoca el contenido con el tipo elegido en Ajustes.
 * Kawase cae a Gaussiano en Android 12; en Android 11 o menos no hace nada.
 */
@Composable
fun Modifier.appBlur(radius: Dp, type: BlurType = LocalBlurType.current): Modifier {
    if (!BlurSupport.gaussian) return this
    val kawase = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) KawaseBlur() else null
    }
    return this.graphicsLayer {
        val r = radius.toPx()
        val sdk = Build.VERSION.SDK_INT
        renderEffect = when {
            r < 0.5f -> null
            sdk >= Build.VERSION_CODES.TIRAMISU && type == BlurType.KAWASE && kawase != null ->
                kawase.effect(r, size.width, size.height)
            sdk >= Build.VERSION_CODES.S -> gaussian(r)
            else -> null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun gaussian(radius: Float): RenderEffect =
    PlatformRenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()

/**
 * Dual Kawase: 3 pasadas "down" con offset creciente y 3 "up" con offset decreciente.
 * Como RenderEffect no cambia la resolución, el offset se duplica en cada pasada para
 * imitar la reducción de tamaño del algoritmo original.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class KawaseBlur {
    private val down = List(PASSES) { RuntimeShader(DOWN_SHADER) }
    private val up = List(PASSES) { RuntimeShader(UP_SHADER) }

    fun effect(radius: Float, width: Float, height: Float): RenderEffect {
        val base = (radius / 7f).coerceAtLeast(0.5f)
        val steps = down.mapIndexed { i, s -> s to base * (1 shl i) } +
            up.mapIndexed { i, s -> s to base * (1 shl (PASSES - 1 - i)) }

        var chain: PlatformRenderEffect? = null
        for ((shader, offset) in steps) {
            shader.setFloatUniform("offset", offset)
            shader.setFloatUniform("size", width, height)
            val pass = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
            chain = if (chain == null) pass else PlatformRenderEffect.createChainEffect(pass, chain)
        }
        return chain!!.asComposeRenderEffect()
    }

    private companion object {
        const val PASSES = 3

        const val COMMON = """
            uniform shader content;
            uniform float offset;
            uniform float2 size;
            half4 s(float2 p) { return content.eval(clamp(p, float2(0.5), size - float2(0.5))); }
        """

        const val DOWN_SHADER = COMMON + """
            half4 main(float2 p) {
                float o = offset;
                half4 sum = s(p) * 4.0;
                sum += s(p + float2(-o, -o));
                sum += s(p + float2( o,  o));
                sum += s(p + float2( o, -o));
                sum += s(p + float2(-o,  o));
                return sum / 8.0;
            }
        """

        const val UP_SHADER = COMMON + """
            half4 main(float2 p) {
                float o = offset;
                half4 sum = s(p + float2(-2.0 * o, 0.0));
                sum += s(p + float2(-o,  o)) * 2.0;
                sum += s(p + float2(0.0, 2.0 * o));
                sum += s(p + float2( o,  o)) * 2.0;
                sum += s(p + float2(2.0 * o, 0.0));
                sum += s(p + float2( o, -o)) * 2.0;
                sum += s(p + float2(0.0, -2.0 * o));
                sum += s(p + float2(-o, -o)) * 2.0;
                return sum / 12.0;
            }
        """
    }
}
