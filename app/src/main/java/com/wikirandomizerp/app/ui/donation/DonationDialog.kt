package com.wikirandomizerp.app.ui.donation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.billing.DonationState

/**
 * Optional support dialog. It shows live Play Billing prices when available and always offers the
 * project's GitHub page as a fallback, so the app is usable before the Play Console products exist.
 *
 * This dialog deliberately states that donations go to the app's developer, not to Wikimedia.
 */
@Composable
fun DonationDialog(
    state: DonationState,
    onDismiss: () -> Unit,
    onDonate: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenGithub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.heart), contentDescription = null) },
        title = { Text(stringResource(R.string.donationDialogTitle)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.donationDialogDesc),
                    style = typography.bodyMedium
                )

                if (state.supporter) {
                    Surface(
                        color = colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.donationSupporterBadge),
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.donationSupporterDesc),
                                style = typography.bodySmall,
                                color = colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (state.subscription) {
                    Surface(
                        color = colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.donationSyncActive),
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.donationSyncDesc),
                                style = typography.bodySmall,
                                color = colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                when {
                    state.connecting -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.donationLoading),
                            style = typography.bodyMedium
                        )
                    }

                    state.products.isNotEmpty() || state.subscriptionProduct != null -> {
                        state.products.forEach { product ->
                            FilledTonalButton(
                                onClick = { onDonate(product.id) },
                                enabled = state.busyProductId == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = product.title.ifBlank { product.id },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (product.price.isNotBlank()) {
                                        Text(
                                            text = product.price,
                                            style = typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        if (state.products.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.donationOneTime),
                                style = typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        state.subscriptionProduct?.let { product ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.donationSyncTitle),
                                style = typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.donationSyncDesc),
                                style = typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            if (!state.subscription) {
                                FilledTonalButton(
                                    onClick = { onDonate(product.id) },
                                    enabled = state.busyProductId == null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = product.title.ifBlank { product.id },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (product.price.isNotBlank()) {
                                            Text(
                                                text = stringResource(
                                                    R.string.donationPerMonth,
                                                    product.price
                                                ),
                                                style = typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = state.error
                                ?: stringResource(R.string.donationNotConfigured),
                            style = typography.bodyMedium,
                            color = colorScheme.error
                        )
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.donationRetry))
                        }
                    }
                }

                state.message?.let {
                    Text(
                        text = it,
                        style = typography.bodyMedium,
                        color = colorScheme.primary
                    )
                }
                if (state.products.isNotEmpty() || state.subscriptionProduct != null) {
                    state.error?.let { errorText ->
                        Text(
                            text = errorText,
                            style = typography.bodySmall,
                            color = colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.donationIndependence),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.donationClose))
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenGithub) {
                Text(stringResource(R.string.donationViaGithub))
            }
        }
    )
}
