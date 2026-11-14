package dev.charly.mantenimiento.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object Http {
    private const val USER_AGENT = "Mantenimiento-Android (github.com/homecharlygit/mantenimiento)"

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
        }

    fun get(url: String, accept: String = "application/json"): String {
        val conn = open(url, accept)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga a [dest] reportando el progreso (0..1, o -1 si no se conoce el tamaño). */
    fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = open(url, "application/octet-stream")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 128 * 1024 || done == total) {
                            lastReport = done
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
            if (!tmp.renameTo(dest)) throw IOException("No se pudo guardar el archivo")
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }
}
