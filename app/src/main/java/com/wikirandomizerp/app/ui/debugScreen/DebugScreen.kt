package com.wikirandomizerp.app.ui.debugScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.billing.DonationState
import com.wikirandomizerp.app.ui.settingsScreen.DebugLogDialog
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.CustomColors.topBarColors

/**
 * Hidden developer menu, reachable only after ten taps on the About header.
 *
 * It lets a maintainer preview the support prompt and the billing sheet, simulate a purchase and
 * read the on-device debug log without a debug build or a connected Play Console account.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DebugScreen(
    donationState: DonationState,
    onBack: () -> Unit,
    onShowDonationPrompt: () -> Unit,
    onOpenDonationDialog: () -> Unit,
    onSimulatePurchase: () -> Unit,
    onResetDonation: () -> Unit,
    onDisableDeveloperMode: () -> Unit
) {
    var showLog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debugMenuTitle)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = listItemColors.containerColor
                        ),
                        onClick = onBack
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.debugMenuDesc),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                SectionHeader(stringResource(R.string.debugDonationSection))
            }
            item {
                val status = stringResource(
                    if (donationState.connected) R.string.debugBillingConnected
                    else R.string.debugBillingDisconnected
                )
                val products = if (donationState.products.isNotEmpty()) {
                    " · " + stringResource(
                        R.string.debugBillingProducts,
                        donationState.products.size
                    )
                } else {
                    ""
                }
                DebugRow(
                    title = stringResource(R.string.debugBillingStatus),
                    subtitle = status + products
                )
            }
            item {
                DebugRow(
                    title = stringResource(R.string.debugTestDonationPrompt),
                    subtitle = stringResource(R.string.debugTestDonationPromptDesc),
                    onClick = onShowDonationPrompt
                )
            }
            item {
                DebugRow(
                    title = stringResource(R.string.debugTestDonationDialog),
                    subtitle = stringResource(R.string.debugTestDonationDialogDesc),
                    onClick = onOpenDonationDialog
                )
            }
            item {
                DebugRow(
                    title = stringResource(R.string.debugSimulatePurchase),
                    subtitle = stringResource(R.string.debugSimulatePurchaseDesc),
                    onClick = onSimulatePurchase
                )
            }
            item {
                DebugRow(
                    title = stringResource(R.string.debugResetDonation),
                    subtitle = stringResource(R.string.debugResetDonationDesc),
                    onClick = onResetDonation
                )
            }

            item {
                SectionHeader(stringResource(R.string.debugDiagnosticsSection))
            }
            item {
                DebugRow(
                    title = stringResource(R.string.debugOpenLog),
                    subtitle = stringResource(R.string.debugOpenLogDesc),
                    onClick = { showLog = true }
                )
            }

            item {
                OutlinedButton(
                    onClick = onDisableDeveloperMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.debugDisable))
                }
            }
            item {
                Text(
                    text = stringResource(R.string.debugDisableDesc),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showLog) {
        DebugLogDialog(onDismiss = { showLog = false })
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DebugRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (subtitle != null) {
                { Text(subtitle) }
            } else null,
            colors = listItemColors
        )
    }
}
