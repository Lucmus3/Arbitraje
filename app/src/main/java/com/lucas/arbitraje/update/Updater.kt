package com.lucas.arbitraje.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.lucas.arbitraje.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Auto-actualización desde GitHub: usa el último release ("v1.0.N", N = versionCode, con el APK adjunto)
 * o, si no hay releases, el manifiesto updates/update.json de la rama main. Gana la versión más alta.
 */
object Updater {

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        listOfNotNull(fromRelease(), fromManifest())
            .filter { it.versionCode > BuildConfig.VERSION_CODE }
            .maxByOrNull { it.versionCode }
    }

    private fun getJson(url: String) = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        if (conn.responseCode != 200) null
        else JsonParser.parseString(conn.inputStream.bufferedReader().readText()).asJsonObject
    } catch (e: Exception) {
        null
    }

    /** Último release de GitHub con tag "v1.0.N" y el APK adjunto. */
    private fun fromRelease(): UpdateInfo? = try {
        getJson("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")?.let { json ->
            val tag = json.get("tag_name").asString
            val asset = json.getAsJsonArray("assets")
                .map { it.asJsonObject }
                .firstOrNull { it.get("name").asString.endsWith(".apk") }
            val code = tag.substringAfterLast('.').toIntOrNull()
            if (asset == null || code == null) null
            else UpdateInfo(
                versionCode = code,
                versionName = tag.removePrefix("v"),
                apkUrl = asset.get("browser_download_url").asString,
                notes = json.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            )
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Alternativa sin releases: updates/update.json en la rama main del repo, con
     * {"versionCode": N, "versionName": "1.0.N", "apk": "updates/arbitraje.apk", "notes": "..."}.
     */
    private fun fromManifest(): UpdateInfo? = try {
        val base = "https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/"
        getJson(base + "updates/update.json")?.let { json ->
            val apk = json.get("apk").asString
            UpdateInfo(
                versionCode = json.get("versionCode").asInt,
                versionName = json.get("versionName").asString,
                apkUrl = if (apk.startsWith("http")) apk else base + apk,
                notes = json.get("notes")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            )
        }
    } catch (e: Exception) {
        null
    }

    /** Descarga el APK informando el progreso (0..1). */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "arbitraje-${info.versionName}.apk")
            var url = URL(info.apkUrl)
            var conn: HttpURLConnection
            // GitHub redirige a otro host; seguimos las redirecciones a mano.
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                } else break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            out
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
