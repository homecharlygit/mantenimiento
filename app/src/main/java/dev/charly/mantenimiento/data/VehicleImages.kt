package dev.charly.mantenimiento.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Imágenes de los vehículos (logo, foto, lo que elijas). Se copian al almacenamiento privado
 * de la app reducidas a 512 px por lado (PNG para conservar la transparencia de los logos),
 * así no dependen de que la imagen original siga en la galería.
 */
object VehicleImages {
    private const val MAX_SIDE = 512

    private fun dir(c: Context) = File(c.filesDir, "vehicle_images").apply { mkdirs() }

    fun file(c: Context, name: String): File = File(dir(c), name)

    /** Copia y reduce la imagen elegida. Devuelve el nombre del archivo o null si no se pudo leer. */
    fun import(c: Context, uri: Uri): String? = runCatching {
        val bmp = decode(c, uri) ?: return null
        val name = "v_${System.currentTimeMillis()}.png"
        file(c, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }.getOrNull()

    private fun decode(c: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(c.contentResolver, uri)
            // ImageDecoder respeta la rotación EXIF de las fotos
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_SIDE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / max(raw.width, raw.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).roundToInt(), (raw.height * scale).roundToInt(), true) else raw
    }

    fun load(c: Context, name: String): Bitmap? =
        runCatching { BitmapFactory.decodeFile(file(c, name).absolutePath) }.getOrNull()

    fun delete(c: Context, name: String?) {
        if (name != null) runCatching { file(c, name).delete() }
    }

    /** Borra imágenes que ya no usa ningún vehículo (p. ej. si cerraste el formulario sin guardar). */
    fun cleanup(c: Context, inUse: Set<String>) {
        dir(c).listFiles()?.forEach { if (it.name !in inUse) it.delete() }
    }
}
