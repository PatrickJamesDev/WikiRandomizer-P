package com.wikirandomizerp.app.data.sync

data class FirebaseSyncConfig(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val authDomain: String
)

sealed interface SyncAuthState {
    data object NotConfigured : SyncAuthState
    data object SignedOut : SyncAuthState
    data class SignedIn(val uid: String, val email: String?) : SyncAuthState
}

data class RemoteMeta(
    val version: Long,
    val schema: Int,
    val updatedAt: Long,
    val payloadBytes: Long
)

data class LocalSyncResult(
    val recordCount: Int,
    val lastSyncAt: Long
)
