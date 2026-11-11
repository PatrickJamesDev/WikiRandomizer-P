package com.wikirandomizerp.app.ui.aboutScreen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.CustomColors.topBarColors

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun AboutTopAppBar(scrollBehavior: TopAppBarScrollBehavior, onBack: () -> Unit) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.about)) },
        subtitle = { Text(stringResource(R.string.app_name)) },
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