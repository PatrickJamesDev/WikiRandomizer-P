package com.wikirandomizerp.app.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.wikirandomizerp.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DriveReconnectRequiredException :
    Exception("Open Google Drive sync settings and reconnect your Google account")

sealed interface DriveAuthorization {
    data class Authorized(val accessToken: String) : DriveAuthorization
    data class Resolution(val pendingIntent: PendingIntent) : DriveAuthorization
    data class Failed(val message: String) : DriveAuthorization
}

/**
 * Obtains an access token for the drive.appdata scope through Google Play services.
 * Authorization is intentionally separate from sign-in: only this app's hidden Drive folder is
 * available, and no name, email or other Drive files are requested.
 */
class DriveAuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val configured: Boolean
        get() = BuildConfig.GOOGLE_DRIVE_CLIENT_ID.isNotBlank()

    val connected: Boolean
        get() = configured && prefs.getBoolean(KEY_CONNECTED, false)

    fun cachedToken(): String? {
        if (!connected) return null
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt > System.currentTimeMillis() + 60_000L) return token
        return null
    }

    /** User-initiated connection; Play services may return a consent screen to launch. */
    suspend fun connect(context: Context): DriveAuthorization {
        if (!configured) {
            return DriveAuthorization.Failed(
                "Set GOOGLE_DRIVE_CLIENT_ID when building to enable Google Drive sync"
            )
        }
        return request(context)
    }

    /** Background use. If consent is required it must be completed from Settings. */
    suspend fun accessToken(context: Context): String {
        cachedToken()?.let { return it }
        return when (val result = request(context)) {
            is DriveAuthorization.Authorized -> result.accessToken
            is DriveAuthorization.Resolution -> throw DriveReconnectRequiredException()
            is DriveAuthorization.Failed -> throw SyncException(result.message)
        }
    }

    /** Parses the result returned by the consent activity instead of authorizing a second time. */
    fun completeConnect(context: Context, data: Intent?): DriveAuthorization {
        if (!configured) {
            return DriveAuthorization.Failed(
                "Set GOOGLE_DRIVE_CLIENT_ID when building to enable Google Drive sync"
            )
        }
        if (data == null) return DriveAuthorization.Failed("Google authorization was cancelled")
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
                ?: return DriveAuthorization.Failed("Google did not return an access token")
            rememberToken(token)
            DriveAuthorization.Authorized(token)
        } catch (t: Throwable) {
            DriveAuthorization.Failed(t.message ?: "Google authorization failed")
        }
    }

    private fun rememberToken(token: String) {
        prefs.edit()
            .putBoolean(KEY_CONNECTED, true)
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
            .apply()
    }

    fun disconnect() {
        prefs.edit()
            .remove(KEY_CONNECTED)
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun invalidateToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    private suspend fun request(context: Context): DriveAuthorization {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_APPDATA)))
            .build()
        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request).awaitTask()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                    ?: return DriveAuthorization.Failed("Google authorization did not start")
                DriveAuthorization.Resolution(pendingIntent)
            } else {
                val token = result.accessToken
                    ?: return DriveAuthorization.Failed("Google did not return an access token")
                rememberToken(token)
                DriveAuthorization.Authorized(token)
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            DriveAuthorization.Failed(t.message ?: "Google authorization failed")
        }
    }

    private companion object {
        const val PREFS = "drive_sync"
        const val KEY_CONNECTED = "connected"
        const val KEY_TOKEN = "access_token"
        const val KEY_EXPIRES_AT = "access_token_expires_at"
        const val TOKEN_LIFETIME_MS = 55L * 60L * 1000L
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            continuation.resume(task.result as T)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Google Play services request failed")
            )
        }
    }
}
