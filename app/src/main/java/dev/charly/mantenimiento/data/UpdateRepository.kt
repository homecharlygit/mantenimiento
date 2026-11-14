package dev.charly.mantenimiento.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
)

/** Revisa los releases del repo en GitHub y descarga/instala el APK nuevo. */
class UpdateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** Versión instalada sin el sufijo "-debug". */
    val currentVersion: String =
        (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0")
            .removeSuffix("-debug")

    val isDebugBuild: Boolean = context.packageName.endsWith(".debug")

    fun lastCheckMillis(): Long = prefs.getLong("last_check", 0L)

    /** Devuelve la actualización disponible o null si ya tienes la más reciente. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val j = JSONObject(Http.get(LATEST_URL, accept = "application/vnd.github+json"))
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        val version = j.getString("tag_name").removePrefix("v")
        if (compareVersions(version, currentVersion) <= 0) return@withContext null

        val assets = j.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.getString("name").endsWith(".apk") }
            .firstOrNull { it.getString("name").contains("-debug") == isDebugBuild }
            ?: return@withContext null

        UpdateInfo(
            version = version,
            notes = cleanMarkdown(j.optString("body")),
            apkUrl = apk.getString("browser_download_url"),
            pageUrl = j.optString("html_url", RELEASES_PAGE),
        )
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates")
        dir.listFiles()?.forEach { it.delete() } // borra descargas viejas
        val file = File(dir, "mantenimiento-${info.version}.apk")
        Http.download(info.apkUrl, file, onProgress)
        file
    }

    /** Puede instalar paquetes, o hay que mandar al usuario a darle el permiso. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Abre el instalador del sistema con el APK descargado. */
    fun install(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/homecharlygit/mantenimiento/releases/latest"
        const val RELEASES_PAGE = "https://github.com/homecharlygit/mantenimiento/releases"

        /** Compara "1.10" vs "1.9" por partes numéricas. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }

        /** Quita la sintaxis Markdown más común para mostrar las notas como texto. */
        private fun cleanMarkdown(md: String): String = md.lines().joinToString("\n") { line ->
            line.replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace("**", "")
                .replace("`", "")
        }.trim()
    }
}
