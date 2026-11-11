package com.wikirandomizerp.app.ui.image

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.DebugLog
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Saves a full-resolution article image to the phone's gallery.
 *  - Android 10+: Pictures/WikiRandomizer-P (SVG and other non-bitmap files: Download/WikiRandomizer-P),
 *    through MediaStore, so no storage permission is needed.
 *  - Older Android: the app's own Pictures folder (also permission-free).
 */
object ImageDownloader {
    private val client by lazy { OkHttpClient.Builder().followRedirects(true).build() }
    private const val USER_AGENT =
        "WikiRandomizer-P (Android; image download) okhttp/5 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"

    /** @return a human-readable location of the saved file */
    suspend fun download(context: Context, url: String, suggestedName: String?): String =
        withContext(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                    ?.takeIf { it.startsWith("image/") } ?: guessMime(url)
                val fileName = fileNameFor(url, suggestedName, mime)
                val input = response.body?.byteStream() ?: throw IOException("empty response")

                val where = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bitmap = mime != "image/svg+xml"
                    val collection = if (bitmap) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val folder = (if (bitmap) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/WikiRandomizer-P"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(collection, values) ?: throw IOException("could not create the file")
                    try {
                        resolver.openOutputStream(uri)?.use { out -> input.use { it.copyTo(out) } }
                            ?: throw IOException("could not open the file")
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        throw e
                    }
                    "$folder/$fileName"
                } else {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "WikiRandomizer-P")
                    dir.mkdirs()
                    val file = File(dir, fileName)
                    file.outputStream().use { out -> input.use { it.copyTo(out) } }
                    file.absolutePath
                }
                DebugLog.i("Download", "saved $url -> $where in ${(System.nanoTime() - t0) / 1_000_000} ms")
                where
            }
        }

    private fun guessMime(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "tif", "tiff" -> "image/tiff"
            else -> "image/jpeg"
        }
    }

    private fun fileNameFor(url: String, suggested: String?, mime: String): String {
        val fromUrl = try {
            URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), "UTF-8")
        } catch (e: Exception) {
            "image"
        }
        var name = fromUrl.ifBlank { suggested ?: "image" }
        name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        if (!name.contains('.')) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            name = "$name.$ext"
        }
        return name
    }
}

/**
 * Returns a click handler that downloads [url] and tells the user where it went (or why it
 * failed); null when there is no image to download.
 */
@Composable
fun rememberImageDownload(url: String?, description: String?): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (url == null) return null
    return remember(url, description) {
        val handler: () -> Unit = {
            scope.launch {
                Toast.makeText(context, context.getString(R.string.imageDownloading), Toast.LENGTH_SHORT).show()
                try {
                    val where = ImageDownloader.download(context, url, description)
                    Toast.makeText(context, context.getString(R.string.imageSavedTo, where), Toast.LENGTH_LONG).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.e("Download", "image download failed: $url", e)
                    Toast.makeText(
                        context,
                        context.getString(R.string.imageDownloadFailed, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        handler
    }
}


/** Open/closed state of the small menu that appears when a picture is held. */
class ImageMenuState {
    var open by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
fun rememberImageMenuState(): ImageMenuState = remember { ImageMenuState() }

/**
 * The menu shown after holding a picture in an article: download it without opening the full-screen
 * viewer. Place it next to the picture inside the same Box (it anchors to its parent) and set
 * [ImageMenuState.open] from the picture's long-click handler.
 *
 * @param url full-size address of the picture to save
 */
@Composable
fun ImageDownloadMenu(state: ImageMenuState, url: String?, description: String?) {
    val download = rememberImageDownload(url, description)
    androidx.compose.material3.DropdownMenu(
        expanded = state.open && download != null,
        onDismissRequest = { state.open = false }
    ) {
        androidx.compose.material3.DropdownMenuItem(
            text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.downloadImage)) },
            leadingIcon = {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                state.open = false
                download?.invoke()
            }
        )
    }
}
