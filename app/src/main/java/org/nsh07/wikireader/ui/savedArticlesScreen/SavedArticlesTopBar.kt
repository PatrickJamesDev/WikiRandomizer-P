package org.nsh07.wikireader.ui.savedArticlesScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.nsh07.wikireader.R
import org.nsh07.wikireader.ui.common.CollectionLayout
import org.nsh07.wikireader.ui.common.CollectionSort
import org.nsh07.wikireader.ui.theme.CustomColors.listItemColors
import org.nsh07.wikireader.ui.theme.CustomColors.topBarColors

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun SavedArticlesTopBar(
    articlesInfo: String,
    scrollBehavior: TopAppBarScrollBehavior,
    deleteEnabled: Boolean,
    sort: CollectionSort,
    layout: CollectionLayout,
    onSortChange: (CollectionSort) -> Unit,
    onLayoutChange: (CollectionLayout) -> Unit,
    onBack: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.savedArticles)) },
        subtitle = { Text(articlesInfo) },
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
        actions = {
            Box {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { sortMenu = true }
                ) {
                    Icon(
                        painterResource(R.drawable.sort),
                        contentDescription = stringResource(R.string.sort)
                    )
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf(
                        CollectionSort.DATE_NEW to R.string.sortDateNewest,
                        CollectionSort.DATE_OLD to R.string.sortDateOldest,
                        CollectionSort.TOPIC_AZ to R.string.sortTopicAZ,
                        CollectionSort.TOPIC_ZA to R.string.sortTopicZA
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                sortMenu = false
                                onSortChange(mode)
                            },
                            trailingIcon = {
                                if (sort == mode) Icon(
                                    painterResource(R.drawable.check),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { onLayoutChange(layout.next()) }
            ) {
                Icon(
                    painterResource(
                        if (layout.isGrid) R.drawable.grid_view else R.drawable.view_list
                    ),
                    contentDescription = stringResource(
                        when (layout) {
                            CollectionLayout.LIST -> R.string.layoutList
                            CollectionLayout.GRID_2 -> R.string.layoutGrid2
                            CollectionLayout.GRID_3 -> R.string.layoutGrid3
                            CollectionLayout.GRID_4 -> R.string.layoutGrid4
                        }
                    )
                )
            }
            IconButton(
                enabled = deleteEnabled,
                shapes = IconButtonDefaults.shapes(),
                onClick = onDeleteAll
            ) {
                Icon(
                    painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.deleteAll)
                )
            }
        },
        colors = topBarColors,
        scrollBehavior = scrollBehavior
    )
}
