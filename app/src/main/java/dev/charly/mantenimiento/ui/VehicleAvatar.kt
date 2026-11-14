package dev.charly.mantenimiento.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.charly.mantenimiento.R
import dev.charly.mantenimiento.data.VehicleImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carga la imagen del vehículo en segundo plano (null mientras carga o si no tiene). */
@Composable
fun rememberVehicleImage(name: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, name) {
        value = name?.let { withContext(Dispatchers.IO) { VehicleImages.load(context, it)?.asImageBitmap() } }
    }
    return image
}

/**
 * Distintivo del vehículo: la imagen que subiste o, si no hay, el ícono de carro sobre su color.
 * [contentRotation] contrarresta la rotación de la forma para que la imagen no gire con ella.
 */
@Composable
fun VehicleAvatar(
    image: String?,
    fit: Boolean,
    color: Color,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentRotation: Float = 0f,
) {
    val bmp = rememberVehicleImage(image)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (bmp != null) MaterialTheme.colorScheme.surfaceContainerHighest else color),
    ) {
        Crossfade(bmp, label = "avatar") { b ->
            if (b != null) {
                Image(
                    bitmap = b,
                    contentDescription = null,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = contentRotation }
                        .padding(if (fit) size * 0.14f else size * 0f),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_car), null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(size * 0.55f).graphicsLayer { rotationZ = contentRotation },
                    )
                }
            }
        }
    }
}
