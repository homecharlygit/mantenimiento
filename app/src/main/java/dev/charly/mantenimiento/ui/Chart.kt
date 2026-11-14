package dev.charly.mantenimiento.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.charly.mantenimiento.data.EffUnit
import dev.charly.mantenimiento.data.Efficiency
import kotlin.math.roundToInt

/** Gráfica del rendimiento de las últimas cargas. Se dibuja con animación y se puede arrastrar. */
@Composable
fun EfficiencyChart(points: List<Efficiency>, unit: EffUnit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val shown = points.takeLast(20)
    val values = shown.map { unit.value(it.kmPerLiter) }
    var selected by remember(shown) { mutableStateOf<Int?>(null) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(shown.map { it.entryId }) {
        draw.snapTo(0f)
        draw.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow))
    }

    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Rendimiento por carga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val sel = selected?.let { shown.getOrNull(it) }
                    Text(
                        if (sel != null) "${formatDate(sel.date)} · ${unit.format(sel.kmPerLiter)} · ${formatKm(sel.distance)}"
                        else "Últimas ${shown.size} cargas — arrastra para ver cada una",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sel != null) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (values.size < 2) {
                Text(
                    "Necesitas al menos tres cargas de tanque lleno para ver la gráfica.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }
            val min = values.min()
            val max = values.max()
            val pad = ((max - min) * 0.2).coerceAtLeast(0.5)
            val lo = min - pad
            val hi = max + pad
            val line = colors.primary
            val fill = colors.primary.copy(alpha = 0.18f)
            val grid = colors.outlineVariant
            val dot = colors.tertiary

            fun indexAt(x: Float, width: Float): Int =
                ((x / width) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(values) {
                        detectTapGestures { o ->
                            selected = indexAt(o.x, size.width.toFloat())
                            haptics.tick(0.5f)
                        }
                    }
                    .pointerInput(values) {
                        detectDragGestures(
                            onDragEnd = { selected = null },
                            onDragCancel = { selected = null },
                        ) { change, _ ->
                            val i = indexAt(change.position.x, size.width.toFloat())
                            if (i != selected) {
                                selected = i
                                haptics.tick(0.35f)
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                fun pt(i: Int) = Offset(
                    w * i / (values.size - 1),
                    (h - (values[i] - lo) / (hi - lo) * h).toFloat(),
                )
                // Líneas guía
                for (k in 0..3) {
                    val y = h * k / 3
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }
                // Curva suave (bezier entre puntos)
                val path = Path().apply {
                    moveTo(pt(0).x, pt(0).y)
                    for (i in 1 until values.size) {
                        val a = pt(i - 1)
                        val b = pt(i)
                        val mx = (a.x + b.x) / 2
                        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                    }
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * draw.value, partial, true)
                val area = Path().apply {
                    addPath(partial)
                    val end = measure.getPosition(measure.length * draw.value)
                    lineTo(end.x, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0f))))
                drawPath(partial, line, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                selected?.let { i ->
                    val p = pt(i)
                    drawLine(dot.copy(alpha = 0.5f), Offset(p.x, 0f), Offset(p.x, h), strokeWidth = 2.dp.toPx())
                    drawCircle(dot, radius = 8.dp.toPx(), center = p)
                    drawCircle(colors.surface, radius = 4.dp.toPx(), center = p)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(formatShortDate(shown.first().date), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatNum(min)} – ${formatNum(max)} ${unit.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(formatShortDate(shown.last().date), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
