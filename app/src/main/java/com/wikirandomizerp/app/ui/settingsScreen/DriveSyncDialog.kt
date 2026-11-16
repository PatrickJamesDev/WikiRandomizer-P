package com.wikirandomizerp.app.ui.settingsScreen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.sync.DriveConnectResult
import com.wikirandomizerp.app.data.sync.DriveReconnectRequiredException
import com.wikirandomizerp.app.data.sync.DriveSyncStore
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
fun DriveSyncDialog(
    store: DriveSyncStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(store.connected) }
    var autoSync by remember { mutableStateOf(store.autoSync) }
    var lastSyncAt by remember { mutableStateOf(store.lastSyncAt) }
    var busy by remember { mutableStateOf(false) }
    var reconnectNeeded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun handleConnectResult(result: DriveConnectResult) {
        when (result) {
            DriveConnectResult.Connected -> {
                connected = true
                reconnectNeeded = false
                message = context.getString(R.string.settingDriveConnected)
            }
            is DriveConnectResult.Resolution -> Unit
            is DriveConnectResult.Failed -> error = result.message
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        busy = false
        if (result.resultCode == Activity.RESULT_OK) {
            handleConnectResult(store.completeConnect(context, result.data))
        } else {
            error = context.getString(R.string.settingDriveAuthCancelled)
        }
    }

    fun connect() {
        if (busy) return
        val currentActivity = activity
        if (currentActivity == null) {
            error = context.getString(R.string.settingDriveNeedsActivity)
            return
        }
        busy = true
        error = null
        message = null
        scope.launch {
            val result = runCatching { store.connect(currentActivity) }
                .getOrElse {
                    DriveConnectResult.Failed(
                        it.message ?: context.getString(R.string.settingSyncFailed)
                    )
                }
            if (result is DriveConnectResult.Resolution) {
                runCatching {
                    launcher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent).build()
                    )
                }.onFailure {
                    busy = false
                    error = context.getString(
                        R.string.settingDriveAuthOpenFailed,
                        it.message ?: context.getString(R.string.settingSyncFailed)
                    )
                }
            } else {
                busy = false
                handleConnectResult(result)
            }
        }
    }

    fun syncNow() {
        if (busy) return
        busy = true
        reconnectNeeded = false
        error = null
        message = null
        scope.launch {
            val result = runCatching { store.syncNow() }
            busy = false
            result.onSuccess {
                lastSyncAt = it.lastSyncAt
                message = context.getString(
                    if (it.settingsChanged) {
                        R.string.settingDriveSyncCompleteRestart
                    } else {
                        R.string.settingDriveSyncComplete
                    },
                    it.recordCount
                )
            }.onFailure {
                if (it is DriveReconnectRequiredException) {
                    reconnectNeeded = true
                    error = context.getString(R.string.settingDriveReconnectRequired)
                } else {
                    error = context.getString(
                        R.string.settingDriveFailed,
                        it.message ?: context.getString(R.string.settingSyncFailed)
                    )
                }
            }
        }
    }

    fun deleteCloudCopy() {
        if (busy) return
        busy = true
        error = null
        message = null
        scope.launch {
            val result = runCatching { store.deleteCloudData() }
            busy = false
            result.onSuccess {
                autoSync = false
                lastSyncAt = null
                confirmDelete = false
                message = context.getString(R.string.settingDriveDeleted)
            }.onFailure {
                error = context.getString(
                    R.string.settingDriveFailed,
                    it.message ?: context.getString(R.string.settingSyncFailed)
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.settingDriveSync)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!store.configured) {
                    Text(stringResource(R.string.settingDriveNotConfigured))
                } else {
                    Text(
                        if (connected) {
                            stringResource(R.string.settingDriveConnected)
                        } else {
                            stringResource(R.string.settingDriveNotConnected)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (connected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.settingDriveAutoSync))
                            Switch(
                                checked = autoSync,
                                onCheckedChange = { enabled ->
                                    autoSync = enabled
                                    store.setAutoSync(enabled)
                                    if (enabled) syncNow()
                                }
                            )
                        }
                        Text(
                            lastSyncAt?.let {
                                stringResource(
                                    R.string.settingDriveLastSync,
                                    DateUtils.getRelativeTimeSpanString(
                                        it,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString()
                                )
                            } ?: stringResource(R.string.settingDriveNeverSynced),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                    } else if (!connected) {
                        FilledTonalButton(
                            onClick = { connect() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settingDriveConnect))
                        }
                    } else {
                        if (reconnectNeeded) {
                            FilledTonalButton(
                                onClick = { connect() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settingDriveReconnect))
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { syncNow() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settingDriveSyncNow))
                            }
                        }
                        if (confirmDelete) {
                            Text(
                                stringResource(R.string.settingDriveDeleteDesc),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmDelete = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                TextButton(onClick = { deleteCloudCopy() }) {
                                    Text(stringResource(R.string.settingDriveDeleteConfirm))
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settingDriveDelete))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                store.disconnect()
                                connected = false
                                autoSync = false
                                reconnectNeeded = false
                                lastSyncAt = null
                                confirmDelete = false
                                message = null
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settingDriveDisconnect))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
