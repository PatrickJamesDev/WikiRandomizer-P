package com.wikirandomizerp.app.ui.settingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.common.ToolbarTools
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsAction
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.bottomListItemShape
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.middleListItemShape
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.topListItemShape

/** Search bar, utility bar behaviour and the choice of utility bar tools (Appearance). */
@Composable
fun ToolbarSettingsSection(
    preferencesState: PreferencesState,
    onAction: (SettingsAction) -> Unit
) {
    val showSearchBar = preferencesState.showSearchBar
    val active = ToolbarTools.effective(preferencesState.toolbarItems, showSearchBar)
    val byId = ToolbarTools.ALL.associateBy { it.id }
    // Shown tools first, in bar order; then the ones that are off, in the default order.
    val rows = active.mapNotNull { byId[it] } +
        ToolbarTools.ALL.filter {
            it.id !in active && (!it.needsHiddenSearchBar || !showSearchBar)
        }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settingShowSearchBar)) },
            supportingContent = { Text(stringResource(R.string.settingShowSearchBarDesc)) },
            trailingContent = {
                Switch(
                    checked = showSearchBar,
                    onCheckedChange = { onAction(SettingsAction.SaveShowSearchBar(it)) }
                )
            },
            colors = listItemColors,
            modifier = Modifier.clip(topListItemShape)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settingToolbarAutoHide)) },
            supportingContent = { Text(stringResource(R.string.settingToolbarAutoHideDesc)) },
            trailingContent = {
                Switch(
                    checked = preferencesState.toolbarAutoHide,
                    onCheckedChange = { onAction(SettingsAction.SaveToolbarAutoHide(it)) }
                )
            },
            colors = listItemColors,
            modifier = Modifier.clip(middleListItemShape)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settingToolbarTools)) },
            supportingContent = { Text(stringResource(R.string.settingToolbarToolsDesc)) },
            colors = listItemColors,
            modifier = Modifier.clip(middleListItemShape)
        )
        rows.forEachIndexed { index, tool ->
            // With the top search bar hidden the menu button stays: it is the only way to the drawer.
            val locked = tool.id == "menu" && !showSearchBar
            val position = active.indexOf(tool.id)
            val isOn = position >= 0
            ListItem(
                leadingContent = {
                    Icon(painterResource(tool.iconRes), contentDescription = null)
                },
                headlineContent = { Text(stringResource(tool.labelRes)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOn) {
                            IconButton(
                                enabled = position > 0,
                                onClick = {
                                    onAction(
                                        SettingsAction.SaveToolbarItems(
                                            ToolbarTools.move(preferencesState.toolbarItems, tool.id, -1, showSearchBar)
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    painterResource(R.drawable.keyboard_arrow_up),
                                    contentDescription = stringResource(R.string.toolbarMoveUp)
                                )
                            }
                            IconButton(
                                enabled = position < active.lastIndex,
                                onClick = {
                                    onAction(
                                        SettingsAction.SaveToolbarItems(
                                            ToolbarTools.move(preferencesState.toolbarItems, tool.id, 1, showSearchBar)
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    painterResource(R.drawable.keyboard_arrow_down),
                                    contentDescription = stringResource(R.string.toolbarMoveDown)
                                )
                            }
                        }
                        Switch(
                            checked = isOn,
                            enabled = !locked,
                            onCheckedChange = {
                                onAction(
                                    SettingsAction.SaveToolbarItems(
                                        ToolbarTools.toggle(preferencesState.toolbarItems, tool.id, it, showSearchBar)
                                    )
                                )
                            }
                        )
                    }
                },
                colors = listItemColors,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(if (index == rows.lastIndex) bottomListItemShape else middleListItemShape)
            )
        }
    }
}
