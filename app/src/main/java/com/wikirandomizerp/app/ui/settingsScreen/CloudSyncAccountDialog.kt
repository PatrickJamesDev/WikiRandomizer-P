package com.wikirandomizerp.app.ui.settingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.sync.LocalSyncStore
import com.wikirandomizerp.app.data.sync.SyncAuthState
import kotlinx.coroutines.launch

@Composable
fun CloudSyncAccountDialog(
    store: LocalSyncStore,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val auth by store.authState.collectAsStateWithLifecycle(initialValue = store.currentAuth)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun submit(create: Boolean) {
        if (busy) return
        if (email.isBlank() || password.length < 6) {
            error = "Enter an email and a password with at least 6 characters"
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = if (create) {
                store.createAccount(email, password)
            } else {
                store.signIn(email, password)
            }
            busy = false
            result.onSuccess { onDismiss() }.onFailure {
                error = it.message ?: "Authentication failed"
            }
        }
    }

    fun deleteCloudCopy() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = store.deleteCloudData()
            busy = false
            result.onSuccess { onDismiss() }.onFailure {
                error = it.message ?: "Could not delete the cloud copy"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.settingCloudSignInTitle)) },
        text = {
            when (val state = auth) {
                SyncAuthState.NotConfigured -> Text(stringResource(R.string.settingCloudNotConfiguredDesc))
                is SyncAuthState.SignedIn -> Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.settingCloudSignedIn,
                            state.email ?: state.uid
                        )
                    )
                    Text(
                        stringResource(R.string.settingSyncDesc),
                        style = typography.bodySmall
                    )
                    error?.let {
                        Text(
                            text = stringResource(R.string.settingCloudDeleteFailed, it),
                            style = typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                    } else if (confirmDelete) {
                        Text(
                            stringResource(R.string.settingCloudDeleteDataDesc),
                            style = typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(onClick = { deleteCloudCopy() }) {
                                Text(stringResource(R.string.settingCloudDeleteDataConfirm))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settingCloudDeleteData))
                        }
                    }
                }
                SyncAuthState.SignedOut -> Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.settingCloudSignedOut),
                        style = typography.bodySmall
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text(stringResource(R.string.settingCloudEmail)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(stringResource(R.string.settingCloudPassword)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = stringResource(R.string.settingCloudAuthFailed, it),
                            style = typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                    } else {
                        FilledTonalButton(
                            onClick = { submit(create = false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settingCloudSignIn))
                        }
                        OutlinedButton(
                            onClick = { submit(create = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settingCloudCreateAccount))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
        confirmButton = {
            if (auth is SyncAuthState.SignedIn) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        store.signOut()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.settingCloudSignOut))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}
