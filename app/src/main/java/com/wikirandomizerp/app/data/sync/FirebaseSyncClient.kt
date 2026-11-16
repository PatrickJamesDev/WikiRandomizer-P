package com.wikirandomizerp.app.data.sync

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.wikirandomizerp.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Small Firebase Auth + Firestore client used only for cross-device reading history.
 *
 * The app deliberately initializes Firebase manually from BuildConfig values instead of requiring
 * google-services.json. This keeps forks and sideloaded builds working: when the three build
 * properties are absent, [configured] is false and every sync entry point reports a clear error.
 */
class FirebaseSyncClient(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val config: FirebaseSyncConfig? = readConfig()

    private val app: FirebaseApp? = config?.let { cfg ->
        runCatching {
            FirebaseApp.getApps(appContext).firstOrNull {
                it.name == FirebaseApp.DEFAULT_APP_NAME
            } ?: FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId(cfg.applicationId)
                    .setApiKey(cfg.apiKey)
                    .setProjectId(cfg.projectId)
                    .build()
            )
        }.getOrNull()
    }

    private val auth: FirebaseAuth? = app?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    private val firestore: FirebaseFirestore? =
        app?.let { runCatching { FirebaseFirestore.getInstance(it) }.getOrNull() }

    val configured: Boolean = app != null && auth != null && firestore != null

    private val _authState = MutableStateFlow<SyncAuthState>(
        when {
            !configured || auth == null -> SyncAuthState.NotConfigured
            else -> auth.currentUser?.toSyncAuthState() ?: SyncAuthState.SignedOut
        }
    )
    val authState: StateFlow<SyncAuthState> = _authState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _authState.value = if (!configured || auth == null) {
            SyncAuthState.NotConfigured
        } else {
            firebaseAuth.currentUser?.toSyncAuthState() ?: SyncAuthState.SignedOut
        }
    }

    init {
        auth?.addAuthStateListener(authListener)
    }

    val currentUid: String?
        get() = auth?.currentUser?.uid

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val firebaseAuth = auth ?: error("Cloud sync is not configured in this build")
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password).awaitTask()
        Unit
    }

    suspend fun createAccount(email: String, password: String): Result<Unit> = runCatching {
        val firebaseAuth = auth ?: error("Cloud sync is not configured in this build")
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
        Unit
    }

    fun signOut() {
        auth?.signOut()
    }

    suspend fun deleteCloudData() {
        val db = firestore ?: error("Cloud sync is not configured in this build")
        val uid = currentUid ?: error("Sign in to use cloud sync")
        val user = db.collection("users").document(uid).collection("sync")
        val batch = db.batch()
        batch.delete(user.document("data"))
        batch.delete(user.document("meta"))
        batch.commit().awaitTask()
        Unit
    }

    suspend fun readMeta(): RemoteMeta? {
        val db = firestore ?: error("Cloud sync is not configured in this build")
        val uid = currentUid ?: error("Sign in to use cloud sync")
        val document = db.collection("users").document(uid)
            .collection("sync").document("meta").get().awaitTask()
        if (!document.exists()) return null
        return RemoteMeta(
            version = document.getLong("version") ?: 0L,
            schema = document.getLong("schema")?.toInt() ?: 1,
            updatedAt = document.getLong("updatedAt") ?: 0L,
            payloadBytes = document.getLong("payloadBytes") ?: 0L
        )
    }

    suspend fun readSnapshot(): SyncSnapshot? {
        val db = firestore ?: error("Cloud sync is not configured in this build")
        val uid = currentUid ?: error("Sign in to use cloud sync")
        val document = db.collection("users").document(uid)
            .collection("sync").document("data").get().awaitTask()
        if (!document.exists()) return null
        val payload = document.getString("payload") ?: return null
        return runCatching {
            json.decodeFromString(SyncSnapshot.serializer(), decompress(payload))
        }.getOrNull()
    }

    /**
     * Merges [snapshot] with the current cloud copy inside a Firestore transaction and writes the
     * result. The transaction retries if another device writes at the same time, so a phone and a
     * tablet can never blindly overwrite each other. Oldest records are trimmed only if a document
     * would approach Firestore's 1 MiB cap.
     */
    suspend fun writeSnapshot(snapshot: SyncSnapshot): SyncSnapshot {
        val db = firestore ?: error("Cloud sync is not configured in this build")
        val uid = currentUid ?: error("Sign in to use cloud sync")
        val user = db.collection("users").document(uid).collection("sync")
        val dataRef = user.document("data")
        val metaRef = user.document("meta")
        return db.runTransaction { transaction ->
            val remoteDocument = transaction.get(dataRef)
            val remote = if (remoteDocument.exists()) {
                remoteDocument.getString("payload")?.let { payload ->
                    runCatching {
                        json.decodeFromString(SyncSnapshot.serializer(), decompress(payload))
                    }.getOrNull()
                }
            } else null
            val (merged, payload) = encodeAndFit(mergeSyncSnapshots(snapshot, remote, System.currentTimeMillis()))
            val now = System.currentTimeMillis()
            transaction.set(
                dataRef,
                mapOf(
                    "schema" to merged.schema,
                    "version" to merged.version,
                    "updatedAt" to now,
                    "payload" to payload
                )
            )
            transaction.set(
                metaRef,
                mapOf(
                    "schema" to merged.schema,
                    "version" to merged.version,
                    "updatedAt" to now,
                    "payloadBytes" to payload.length
                )
            )
            merged
        }.awaitTask()
    }

    private fun encodeAndFit(snapshot: SyncSnapshot): Pair<SyncSnapshot, String> {
        var current = snapshot
        var payload = compress(current)
        while (payload.length > MAX_PAYLOAD_CHARS && current.records.size > 1) {
            val keep = maxOf(1, current.records.size * 9 / 10)
            val tombstones = current.records.filter { it.deleted }.take(keep)
            val live = current.records.filter { !it.deleted }
                .take((keep - tombstones.size).coerceAtLeast(0))
            current = current.copy(records = live + tombstones)
            payload = compress(current)
        }
        if (payload.length > MAX_PAYLOAD_CHARS) {
            error("Reading history is too large to sync")
        }
        return current to payload
    }

    private fun compress(snapshot: SyncSnapshot): String {
        val raw = json.encodeToString(SyncSnapshot.serializer(), snapshot).toByteArray()
        val bytes = ByteArrayOutputStream().use { buffer ->
            GZIPOutputStream(buffer).use { gzip -> gzip.write(raw) }
            buffer.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decompress(payload: String): String {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        return GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
    }

    private fun FirebaseUser.toSyncAuthState(): SyncAuthState =
        SyncAuthState.SignedIn(uid, email)

    private fun readConfig(): FirebaseSyncConfig? {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() ||
            BuildConfig.FIREBASE_APPLICATION_ID.isBlank() ||
            BuildConfig.FIREBASE_API_KEY.isBlank()
        ) return null
        return FirebaseSyncConfig(
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            applicationId = BuildConfig.FIREBASE_APPLICATION_ID,
            apiKey = BuildConfig.FIREBASE_API_KEY,
            authDomain = BuildConfig.FIREBASE_AUTH_DOMAIN
        )
    }

    private companion object {
        /** Base64 is ~4/3 of the binary size; stay safely below Firestore's 1 MiB document limit. */
        const val MAX_PAYLOAD_CHARS = 700_000
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
                task.exception ?: IllegalStateException("Firebase request failed")
            )
        }
    }
}
