package dev.charly.mantenimiento.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.charly.mantenimiento.R

/** Tarjeta en el inicio cuando hay una versión nueva en GitHub. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateBanner(
    update: UpdateUi,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = update.info ?: return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var showNotes by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.tertiary),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        tint = colors.onTertiary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Versión ${info.version} disponible", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            update.needsPermission -> "Permite que Mantenimiento instale apps y vuelve a tocar Instalar."
                            update.error -> "No se pudo buscar o descargar la actualización. Revisa tu conexión."
                            else -> "Toca Actualizar para descargarla e instalarla"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val progress = update.progress
            AnimatedContent(
                targetState = progress != null,
                transitionSpec = { (fadeIn() + scaleIn(Motion.bouncy(), initialScale = 0.9f)) togetherWith fadeOut() },
                label = "updateActions",
                modifier = Modifier.padding(top = 14.dp),
            ) { downloading ->
                if (downloading && progress != null) {
                    Column {
                        Text(
                            if (progress < 0) "Descargando…" else "Descargando… ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        if (progress < 0) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.tertiary)
                        } else {
                            val shown by animateFloatAsState(progress, Motion.snappy(), label = "progress")
                            LinearProgressIndicator(
                                progress = { shown },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.tertiary,
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { haptics.rise(); onUpdate() }) {
                            Text(if (update.file != null) "Instalar" else "Actualizar")
                        }
                        TextButton(onClick = { haptics.click(0.6f); showNotes = true }) { Text("Novedades") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { haptics.fall(0.5f); onLater() }) { Text("Después") }
                    }
                }
            }
        }
    }

    if (showNotes) {
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text("Versión ${info.version} disponible") },
            text = {
                Text(
                    info.notes.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                Button(onClick = { showNotes = false; haptics.rise(); onUpdate() }) {
                    Text(if (update.file != null) "Instalar" else "Actualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotes = false }) { Text("Cerrar") }
            },
        )
    }
}
