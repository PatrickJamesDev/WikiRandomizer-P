package com.wikirandomizerp.app.ui.settingsScreen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.CustomColors.topBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    title: Int = R.string.settings
) {
    // Deliberately a normal (small) app bar: the large flexible bar laid out extra title styles on
    // the first frame and was the main source of the 40-60 ms Settings launch stall.
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            FilledTonalIconButton(
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = listItemColors.containerColor),
                onClick = onBack
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        colors = topBarColors,
        scrollBehavior = scrollBehavior
    )
}