package org.nsh07.wikireader.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * A release as returned by the GitHub REST API. Only the fields the updater needs are declared;
 * unknown keys are ignored so a future GitHub change cannot break the check.
 */
@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0
)

/** A newer release that can be downloaded and installed from inside the app. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/**
 * Shared, process-wide update state. The home screen checks once in the background; About reads the
 * result so the reader does not have to wait for a second network round-trip.
 */
object UpdateState {
    val available = MutableStateFlow<UpdateInfo?>(null)
    val checking = MutableStateFlow(false)
    val lastCheckedAt = MutableStateFlow(0L)
    val promptedThisSession = MutableStateFlow(false)
}

/**
 * Checks the public GitHub releases feed, downloads the APK into app-private storage and hands it
 * to the system installer through the existing FileProvider. No third-party update library is
 * needed, and the download never touches shared storage.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val context: Context,
    private val releasesApiUrl: String =
        "https://api.github.com/repos/PatrickJamesDev/WikiRandomizer-P/releases/latest"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(releasesApiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // No release has been published yet (or the repository has no releases).
                    // That is not an error the reader needs to see.
                    DebugLog.i("Update", "no published release (HTTP 404)")
                    return@withContext UpdateResult.UpToDate
                }
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error("GitHub returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext UpdateResult.Error("GitHub returned an empty response")
                }
                val release = json.decodeFromString<GitHubRelease>(body)
                if (release.tagName.isBlank()) {
                    return@withContext UpdateResult.Error("The latest release has no version tag")
                }
                if (!isNewer(release.tagName, currentVersion)) {
                    DebugLog.i(
                        "Update",
                        "up to date: local=$currentVersion remote=${release.tagName}"
                    )
                    return@withContext UpdateResult.UpToDate
                }
                val apk = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank()
                }
                val info = UpdateInfo(
                    version = release.tagName.removePrefix("v").removePrefix("V"),
                    notes = release.body.trim(),
                    releaseUrl = release.htmlUrl.ifBlank {
                        "https://github.com/PatrickJamesDev/WikiRandomizer-P/releases"
                    },
                    apkUrl = apk?.downloadUrl,
                    apkName = apk?.name,
                    apkSize = apk?.size ?: 0L
                )
                DebugLog.i(
                    "Update",
                    "update available: local=$currentVersion remote=${release.tagName} " +
                            "apk=${apk?.name ?: "none"} (${apk?.size ?: 0} bytes)"
                )
                UpdateResult.Available(info)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Update", "check failed: ${e.message}")
            UpdateResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Downloads the APK to `filesDir/updates/`. [onProgress] receives 0..1 and is throttled to whole
     * percent steps so a fast connection does not flood the main thread with recompositions.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val url = info.apkUrl ?: throw IOException("This release has no APK asset")
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val safeName = (info.apkName ?: "WikiRandomizer-P-${info.version}.apk")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val file = File(dir, safeName)
            // Never install a half-written file from a previous interrupted attempt.
            if (file.exists()) file.delete()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                var lastPercent = -1
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val percent = ((read * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress((percent / 100f).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                if (total > 0 && file.length() != total) {
                    file.delete()
                    throw IOException("Download was incomplete")
                }
            }
            onProgress(1f)
            DebugLog.i("Update", "downloaded ${file.length()} bytes to ${file.absolutePath}")
            file
        }

    /** Opens the system package installer for a downloaded APK. */
    fun install(file: File) {
        // Back up the user's data right before the installer takes over.
        BackupManager(context).createAutoBackupNow("before-update")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Compares the numeric components of two tags. `v1.0.1` is newer than `1.0 beta`, while
     * `v1.0.0` and `1.0` are treated as the same release. Text suffixes (alpha/beta) are ignored;
     * a higher numeric build is always offered, which is the safe direction for an updater.
     */
    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = numericParts(remote)
        val localParts = numericParts(local)
        if (remoteParts.isEmpty()) return false
        val size = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until size) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun numericParts(value: String): List<Int> =
        Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()
}
