package com.wikirandomizerp.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class DriveUnauthorizedException : Exception("Google Drive authorization expired")

@Serializable
private data class DriveFile(
    val id: String,
    val name: String? = null,
    val modifiedTime: String? = null,
    val size: String? = null
)

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

/**
 * Minimal Drive v3 client for one gzip-compressed JSON file in the app's hidden app-data folder.
 * All calls are short and bounded; the existing sync mutex prevents overlapping uploads.
 */
class DriveApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun read(token: String): DriveSnapshot? = withContext(Dispatchers.IO) {
        val file = findFile(token) ?: return@withContext null
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${file.id}?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            checkAuthorized(response)
            if (!response.isSuccessful) throw SyncException(response.errorMessage())
            val bytes = (response.body ?: throw SyncException("Google Drive returned no data"))
                .byteStream().use { it.readBytes() }
            if (bytes.isEmpty()) return@withContext null
            val text = runCatching {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
            }.getOrElse { throw SyncException("The Google Drive sync file is damaged") }
            runCatching { json.decodeFromString(DriveSnapshot.serializer(), text) }
                .getOrElse { throw SyncException("The Google Drive sync file is damaged") }
        }
    }

    suspend fun write(token: String, snapshot: DriveSnapshot) = withContext(Dispatchers.IO) {
        val payload = gzip(json.encodeToString(DriveSnapshot.serializer(), snapshot))
        val existing = findFile(token)
        if (existing == null) {
            create(token, payload)
        } else {
            update(token, existing.id, payload)
        }
        Unit
    }

    suspend fun deleteCloudCopy(token: String) = withContext(Dispatchers.IO) {
        listFiles(token).forEach { file ->
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/${file.id}")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                checkAuthorized(response)
                if (!response.isSuccessful && response.code != 404) {
                    throw SyncException(response.errorMessage())
                }
            }
        }
        Unit
    }

    private fun findFile(token: String): DriveFile? = listFiles(token).firstOrNull()

    private fun listFiles(token: String): List<DriveFile> {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id,name,modifiedTime,size)")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "10")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            checkAuthorized(response)
            if (!response.isSuccessful) throw SyncException(response.errorMessage())
            val body = response.body?.string().orEmpty()
            return runCatching { json.decodeFromString(DriveFileList.serializer(), body) }
                .getOrElse { throw SyncException("Could not read the Google Drive file list") }
                .files
        }
    }

    private fun create(token: String, payload: ByteArray) {
        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"],"mimeType":"application/gzip"}"""
        val create = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(metadata.toRequestBody(JSON_MEDIA))
            .build()
        val id = client.newCall(create).execute().use { response ->
            checkAuthorized(response)
            if (!response.isSuccessful) throw SyncException(response.errorMessage())
            val body = response.body?.string().orEmpty()
            runCatching { json.decodeFromString(DriveId.serializer(), body).id }
                .getOrElse { throw SyncException("Google Drive did not return a file id") }
        }
        update(token, id, payload)
    }

    private fun update(token: String, id: String, payload: ByteArray) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(payload.toRequestBody(GZIP_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            checkAuthorized(response)
            if (!response.isSuccessful) throw SyncException(response.errorMessage())
        }
    }

    private fun checkAuthorized(response: Response) {
        if (response.code == 401) throw DriveUnauthorizedException()
    }

    private fun Response.errorMessage(): String {
        val text = runCatching { body?.string().orEmpty() }.getOrDefault("")
        return when {
            code == 403 -> "Google Drive refused the request (quota or permission denied)"
            text.isBlank() -> "Google Drive request failed with HTTP $code"
            else -> "Google Drive request failed with HTTP $code: ${text.take(300)}"
        }
    }

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray()) }
        return bytes.toByteArray()
    }

    private companion object {
        const val FILE_NAME = "wikireader-sync.json.gz"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val GZIP_MEDIA = "application/gzip".toMediaType()
    }
}

@Serializable
private data class DriveId(val id: String)
