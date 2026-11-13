package com.wikirandomizerp.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.FeatureFlags
import com.wikirandomizerp.app.data.TrackRecompositions
import com.wikirandomizerp.app.data.perfMeasure
import com.wikirandomizerp.app.R.drawable
import com.wikirandomizerp.app.R.string
import com.wikirandomizerp.app.ui.homeScreen.viewModel.FeedSection
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen
import kotlin.reflect.KClass


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigationDrawer(
    state: WideNavigationRailState,
    drawerState: DrawerState,
    homeBackStackEntry: HomeSubscreen,
    windowSizeClass: WindowSizeClass,
    backStackEntry: NavBackStackEntry?,
    historyEnabled: Boolean,
    isImageView: Boolean,
    onHistoryClick: () -> Unit,
    onLibrarySearchClick: () -> Unit = {},
    onHomeClick: () -> Unit,
    onSavedArticlesClick: () -> Unit,
    onSavedImagesClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCuratorsClick: () -> Unit,
    onVerticalFeedClick: () -> Unit = {},
    onTabsClick: () -> Unit = {},
    menuOnRight: Boolean = false,
    /** The TikTok-style feed is a developer preview and stays out of the normal menu. */
    developerMode: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    TrackRecompositions("AppNavigationDrawer") {
        "drawer=${drawerState.targetValue} menuRight=$menuOnRight developer=$developerMode"
    }
    // Keep the menu model stable across AppScreen recompositions (article scroll-position saves
    // recompose this whole subtree). The callbacks read the latest lambdas through state, so the
    // list and its PainterResource lookups are not rebuilt every time.
    val latestHomeClick by rememberUpdatedState(onHomeClick)
    val latestVerticalFeedClick by rememberUpdatedState(onVerticalFeedClick)
    val latestSavedClick by rememberUpdatedState(onSavedArticlesClick)
    val latestSavedImagesClick by rememberUpdatedState(onSavedImagesClick)
    val latestHighlightsClick by rememberUpdatedState(onHighlightsClick)
    val latestHistoryClick by rememberUpdatedState(onHistoryClick)
    val latestLibrarySearchClick by rememberUpdatedState(onLibrarySearchClick)
    val latestCuratorsClick by rememberUpdatedState(onCuratorsClick)
    val latestTabsClick by rememberUpdatedState(onTabsClick)
    val latestSettingsClick by rememberUpdatedState(onSettingsClick)
    // painterResource is composable, so resolve the painters before the remember block.
    val homeOutlined = painterResource(drawable.outline_home)
    val homeFilled = painterResource(drawable.filled_home)
    val feedIcon = painterResource(drawable.feed)
    val savedOutlined = painterResource(drawable.download_done)
    val savedFilled = painterResource(drawable.filled_download_done)
    val savedImagesIcon = painterResource(drawable.image)
    val highlightIcon = painterResource(drawable.highlight)
    val historyIcon = painterResource(drawable.history)
    val historySelected = painterResource(drawable.history_selected)
    val searchIcon = painterResource(drawable.search)
    val groupsIcon = painterResource(drawable.groups)
    val tabIcon = painterResource(drawable.tab)
    val settingsOutlined = painterResource(drawable.outline_settings)
    val settingsFilled = painterResource(drawable.filled_settings)
    val items = remember(developerMode, historyEnabled) { buildList {
        add(
            Item(
                string.home,
                homeOutlined,
                homeFilled,
                HomeScreen::class,
                { latestHomeClick() }
            )
        )
        // Developer preview: hidden for everyone until developer mode is switched on.
        if (developerMode) {
            add(
                Item(
                    string.verticalFeed,
                    feedIcon,
                    feedIcon,
                    VerticalFeedRoute::class,
                    { latestVerticalFeedClick() }
                )
            )
        }
        add(
            Item(
                string.saved,
                savedOutlined,
                savedFilled,
                SavedArticlesScreen::class,
                { latestSavedClick() }
            )
        )
        add(
            Item(
                string.savedImages,
                savedImagesIcon,
                savedImagesIcon,
                SavedImagesRoute::class,
                { latestSavedImagesClick() }
            )
        )
        add(
            Item(
                string.savedHighlights,
                highlightIcon,
                highlightIcon,
                HighlightsScreen::class,
                { latestHighlightsClick() }
            )
        )
        add(
            Item(
                string.history,
                historyIcon,
                historySelected,
                HistoryScreen::class,
                { latestHistoryClick() },
                historyEnabled
            )
        )
        add(
            Item(
                string.librarySearch,
                searchIcon,
                searchIcon,
                LibrarySearchRoute::class,
                { latestLibrarySearchClick() },
                closeBeforeNavigate = true
            )
        )
        // Curators stay fully wired but hidden while the feature is not ready for release.
        if (FeatureFlags.CURATORS_ENABLED) {
            add(
                Item(
                    string.curators,
                    groupsIcon,
                    groupsIcon,
                    CuratorsScreen::class,
                    { latestCuratorsClick() }
                )
            )
        }
        add(
            Item(
                string.tabs,
                tabIcon,
                tabIcon,
                null,
                { latestTabsClick() }
            )
        )
        add(
            Item(
                string.settings,
                settingsOutlined,
                settingsFilled,
                SettingsScreen::class,
                { latestSettingsClick() }
            )
        )
    } }

    val compactScreen =
        remember { !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) }
    val expandedScreen =
        remember { windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) }

    val boxWidth = remember { 360.dp }
    val appLayoutDirection = LocalLayoutDirection.current

    LaunchedEffect(expandedScreen) {
        if (expandedScreen) state.expand() else state.collapse()
    }
    // The modal drawer only exists on compact widths. Leaving that width while it is open must
    // not leave a stale open state that reappears when the device rotates back.
    LaunchedEffect(compactScreen) {
        if (!compactScreen) drawerState.close()
    }

    if (compactScreen) {
        // A real modal drawer (instead of a modal wide rail) is the piece that makes the phone
        // menu track the finger. It also lets the sheet be narrower than 360 dp: labels fill the
        // width instead of leaving a large empty area.
        CompactNavigationDrawer(
            drawerState = drawerState,
            visible = !isImageView,
            menuOnRight = menuOnRight,
            homeBackStackEntry = homeBackStackEntry,
            backStackEntry = backStackEntry,
            navItems = items,
            appLayoutDirection = appLayoutDirection,
            scope = scope,
            modifier = modifier,
            content = content
        )
    } else {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                // Physical left/right must not flip with an RTL article language: the setting names a
                // screen side, not a start/end side. Each rail restores the app direction for its content.
                modifier = modifier
            ) {
                if (!menuOnRight) {
                    AppNavigationRailHost(
                        visible = !isImageView,
                        menuOnRight = false,
                        expandedScreen = expandedScreen,
                        state = state,
                        homeBackStackEntry = homeBackStackEntry,
                        backStackEntry = backStackEntry,
                        navItems = items,
                        boxWidth = boxWidth,
                        appLayoutDirection = appLayoutDirection,
                        scope = scope
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides appLayoutDirection) {
                    content()
                }

                if (menuOnRight) {
                    AppNavigationRailHost(
                        visible = !isImageView,
                        menuOnRight = true,
                        expandedScreen = expandedScreen,
                        state = state,
                        homeBackStackEntry = homeBackStackEntry,
                        backStackEntry = backStackEntry,
                        navItems = items,
                        boxWidth = boxWidth,
                        appLayoutDirection = appLayoutDirection,
                        scope = scope
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactNavigationDrawer(
    drawerState: DrawerState,
    visible: Boolean,
    menuOnRight: Boolean,
    homeBackStackEntry: HomeSubscreen,
    backStackEntry: NavBackStackEntry?,
    navItems: List<Item>,
    appLayoutDirection: LayoutDirection,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    LaunchedEffect(visible) {
        if (!visible && drawerState.isOpen) drawerState.close()
    }
    val drawerDirection = if (menuOnRight) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides drawerDirection) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = visible,
            modifier = modifier,
            drawerContent = {
                ModalDrawerSheet(
                    // Same container colour, corner radius and elevation as the article tab panel
                    // on the right, so the two side panels no longer look like different apps.
                    drawerContainerColor = colorScheme.surfaceContainer,
                    drawerContentColor = colorScheme.onSurface,
                    drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    drawerTonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                ) {
                    AppDrawerContent(
                        drawerState = drawerState,
                        homeBackStackEntry = homeBackStackEntry,
                        backStackEntry = backStackEntry,
                        navItems = navItems,
                        scope = scope
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides appLayoutDirection) {
                content()
            }
        }
    }
}

@Composable
private fun AppDrawerContent(
    drawerState: DrawerState,
    homeBackStackEntry: HomeSubscreen,
    backStackEntry: NavBackStackEntry?,
    navItems: List<Item>,
    scope: CoroutineScope
) {
    // The drawer content stays composed while the drawer is closed. Building every section row
    // during startup (and then again whenever an article/feed update recomposes this subtree)
    // showed up as jank in the startup trace. Sections are only needed once the drawer is opening;
    // keep them while it closes so the rows do not vanish during the close animation.
    val showingHome =
        (drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open) &&
                backStackEntry?.destination?.hasRoute(HomeScreen::class) == true
    // Do not read the feed/article scroll state while the drawer is closed. The drawer content is
    // still composed then, and reading it on every scroll frame was a source of visible jank.
    // Key the remember on the LazyListState identity, not on the whole Article: data-class
    // hashCode walks every parsed AnnotatedString and showed up in jank traces.
    val homeListState = when (homeBackStackEntry) {
        is HomeSubscreen.Feed -> homeBackStackEntry.listState
        is HomeSubscreen.Article -> homeBackStackEntry.listState
        else -> null
    }
    val firstVisibleItem = remember(homeListState, drawerState.targetValue) {
        derivedStateOf {
            if (drawerState.targetValue != DrawerValue.Open) -1
            else homeListState?.firstVisibleItemIndex ?: -1
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().perfMeasure("drawer list")
    ) {
        item(key = "drawer-title") {
            Text(
                stringResource(string.app_name),
                style = typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
            )
        }
        items(navItems, key = { it.labelId }) { item ->
            val selected = item.route?.let { backStackEntry.isShowing(it) } == true
            NavigationDrawerItem(
                label = {
                    Text(
                        stringResource(item.labelId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = selected,
                onClick = {
                    if (item.enabled) {
                        // Screens replace the current destination anyway. Items without a route
                        // (Tabs) close the drawer themselves before opening their panel, so the
                        // two animations run in sequence instead of on top of each other.
                        if (item.closeBeforeNavigate) {
                            // The full-screen library search also opens the keyboard. Wait for
                            // the drawer animation to finish so both transitions do not overlap.
                            scope.launch {
                                drawerState.close()
                                item.onClick()
                            }
                        } else {
                            if (item.route != null) scope.launch { drawerState.close() }
                            item.onClick()
                        }
                    }
                },
                icon = {
                    Crossfade(selected) {
                        when (it) {
                            true -> Icon(item.filledIcon, null)
                            else -> Icon(item.outlinedIcon, null)
                        }
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .alpha(if (item.enabled) 1f else 0.38f)
            )
        }
        if (showingHome) {
            item(key = "drawer-sections-title") {
                Text(
                    stringResource(string.sections),
                    style = typography.titleSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            when (homeBackStackEntry) {
                is HomeSubscreen.Feed -> {
                    items(homeBackStackEntry.sections, key = { it.first }) { section ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    feedSectionName(section.second),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = firstVisibleItem.value == section.first,
                            onClick = {
                                scope.launch {
                                    homeBackStackEntry.listState.scrollToItem(section.first)
                                    drawerState.close()
                                }
                            },
                            icon = {
                                Icon(
                                    painterResource(drawable.keyboard_arrow_right),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                is HomeSubscreen.Article -> {
                    items(homeBackStackEntry.sections, key = { it.first }) { section ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    section.second,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = firstVisibleItem.value == section.first ||
                                    firstVisibleItem.value == section.first + 1,
                            onClick = {
                                scope.launch {
                                    homeBackStackEntry.listState.scrollToItem(section.first)
                                    drawerState.close()
                                }
                            },
                            icon = {
                                Icon(
                                    painterResource(drawable.keyboard_arrow_right),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                else -> null
            }
        }
        item(key = "drawer-bottom-spacer") { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * Tablet/desktop rail. It is always present, so the compact modal drawer above is only used on
 * phones. The rail content follows RTL on a right-side menu, which right-aligns its labels.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationRailHost(
    visible: Boolean,
    menuOnRight: Boolean,
    expandedScreen: Boolean,
    state: WideNavigationRailState,
    homeBackStackEntry: HomeSubscreen,
    backStackEntry: NavBackStackEntry?,
    navItems: List<Item>,
    boxWidth: Dp,
    appLayoutDirection: LayoutDirection,
    scope: CoroutineScope
) {
    val motionScheme = motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec(),
            expandFrom = if (menuOnRight) Alignment.End else Alignment.Start
        ),
        exit = shrinkHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec(),
            shrinkTowards = if (menuOnRight) Alignment.End else Alignment.Start
        )
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides
                    if (menuOnRight) LayoutDirection.Rtl else appLayoutDirection
        ) {
            WideNavigationRail(
                state = state,
                header = {
                    val expanded = state.targetValue == WideNavigationRailValue.Expanded
                    val rotation by animateFloatAsState(
                        if (expanded) 0f else -180f
                    )
                    Box(Modifier.fillMaxWidth()) {
                        IconButton(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            onClick = {
                                scope.launch {
                                    if (expanded)
                                        state.collapse()
                                    else state.expand()
                                }
                            }
                        ) {
                            Crossfade(
                                expanded,
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = rotation
                                }
                            ) {
                                when (it) {
                                    true -> Icon(
                                        painterResource(drawable.menu_open),
                                        "Collapse rail",
                                    )

                                    else -> Icon(
                                        painterResource(drawable.menu),
                                        "Expand rail"
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                AppNavigationRailContent(
                    backStackEntry = backStackEntry,
                    homeBackStackEntry = homeBackStackEntry,
                    navItems = navItems,
                    state = state,
                    scope = scope,
                    expandedScreen = expandedScreen,
                    boxWidth = boxWidth
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationRailContent(
    state: WideNavigationRailState,
    scope: CoroutineScope,
    homeBackStackEntry: HomeSubscreen,
    backStackEntry: NavBackStackEntry?,
    expandedScreen: Boolean,
    boxWidth: Dp,
    navItems: List<Item>
) {
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    val itemSpacing by animateDpAsState(
        if (!expanded) 16.dp else 0.dp,
        animationSpec = motionScheme.defaultSpatialSpec()
    )

    val navigationItemSelectedTextColor by animateColorAsState(
        if (!expanded) colorScheme.secondary else colorScheme.onSecondaryContainer,
        animationSpec = motionScheme.defaultSpatialSpec(),
    )
    val navigationColors = WideNavigationRailItemDefaults.colors(
        unselectedIconColor = colorScheme.onSurfaceVariant,
        unselectedTextColor = colorScheme.onSurfaceVariant,
        selectedIconColor = colorScheme.onSecondaryContainer,
        selectedTextColor = navigationItemSelectedTextColor,
        selectedIndicatorColor = colorScheme.secondaryContainer,
    )

    val sectionColors = WideNavigationRailItemDefaults.colors(
        unselectedIconColor = colorScheme.onSurfaceVariant,
        unselectedTextColor = colorScheme.onSurfaceVariant,
        selectedIconColor = colorScheme.onSurface,
        selectedTextColor = colorScheme.onSurface,
        selectedIndicatorColor = colorScheme.surfaceContainerHighest,
    )

    val showingHome = backStackEntry?.destination?.hasRoute(HomeScreen::class) == true
    // Read the article/feed scroll state only while the rail is open, and only inside the lazy
    // item that needs it. When it is collapsed this used to recompose every section row on every
    // scroll frame (even on large screens where the collapsed rail is always visible), which is
    // exactly the lag reported while scrolling.
    // Same as the drawer: key on the scroll-state identity, not the whole Article data class.
    val homeListState = when (homeBackStackEntry) {
        is HomeSubscreen.Feed -> homeBackStackEntry.listState
        is HomeSubscreen.Article -> homeBackStackEntry.listState
        else -> null
    }
    val firstVisibleItem = remember(homeListState, expanded) {
        derivedStateOf {
            if (!expanded) -1
            else homeListState?.firstVisibleItemIndex ?: -1
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(navItems, key = { it.labelId }) { item ->
            val selected = item.route?.let { backStackEntry.isShowing(it) } == true
            WideNavigationRailItem(
                selected = selected,
                enabled = item.enabled,
                onClick = {
                    scope.launch {
                        if (!expandedScreen) state.collapse()
                        item.onClick()
                    }
                },
                icon = {
                    Crossfade(selected) {
                        when (it) {
                            true -> Icon(item.filledIcon, null)
                            else -> Icon(item.outlinedIcon, null)
                        }
                    }
                },
                label = { Text(stringResource(item.labelId)) },
                railExpanded = expanded,
                colors = navigationColors
            )
        }
        if (expanded && showingHome) {
            item(key = "sections-title") {
                Text(
                    stringResource(string.sections),
                    style = typography.titleSmall,
                    modifier = Modifier
                        .padding(start = 36.dp, top = 12.dp, bottom = 8.dp)
                )
            }
            when (homeBackStackEntry) {
                is HomeSubscreen.Feed -> {
                    items(homeBackStackEntry.sections, key = { it.first }) { section ->
                        WideNavigationRailItem(
                            railExpanded = true,
                            label = {
                                Text(
                                    feedSectionName(section.second),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = boxWidth - 96.dp)
                                )
                            },
                            selected = firstVisibleItem.value == section.first,
                            onClick = {
                                scope.launch {
                                    if (!expandedScreen) state.collapse()
                                    homeBackStackEntry.listState.scrollToItem(section.first)
                                }
                            },
                            icon = {
                                Icon(
                                    painterResource(drawable.keyboard_arrow_right),
                                    contentDescription = null
                                )
                            },
                            colors = sectionColors
                        )
                    }
                }

                is HomeSubscreen.Article -> {
                    items(homeBackStackEntry.sections, key = { it.first }) { section ->
                        WideNavigationRailItem(
                            railExpanded = true,
                            label = {
                                Text(
                                    section.second,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = boxWidth - 96.dp)
                                )
                            },
                            selected = firstVisibleItem.value == section.first ||
                                    firstVisibleItem.value == section.first + 1,
                            onClick = {
                                scope.launch {
                                    if (!expandedScreen) state.collapse()
                                    homeBackStackEntry.listState.scrollToItem(section.first)
                                }
                            },
                            icon = {
                                Icon(
                                    painterResource(drawable.keyboard_arrow_right),
                                    contentDescription = null
                                )
                            },
                            colors = sectionColors
                        )
                    }
                }

                else -> null
            }
        }
        item(key = "bottom-spacer") {
            Spacer(
                Modifier.height(
                    WideNavigationRailDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
fun feedSectionName(section: FeedSection): String {
    return when (section) {
        FeedSection.TFA -> stringResource(string.featuredArticle)
        FeedSection.MOST_READ -> stringResource(string.trendingArticles)
        FeedSection.IMAGE -> stringResource(string.picOfTheDay)
        FeedSection.NEWS -> stringResource(string.inTheNews)
        FeedSection.ON_THIS_DAY -> stringResource(string.onThisDay)
    }
}

private data class Item(
    val labelId: Int,
    val outlinedIcon: Painter,
    val filledIcon: Painter,
    /** Null for actions that do not correspond to a navigation destination (e.g. Tabs). */
    val route: KClass<out Any>?,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    /** Waits for the drawer to close before running [onClick] (for screens with their own animation). */
    val closeBeforeNavigate: Boolean = false
)

/**
 * True when [route] is on screen — including the submenus that belong to it. The Reading &
 * topics page is reached from Settings, so the drawer keeps "Settings" selected there instead of
 * leaving the whole drawer without a current item.
 */
private fun NavBackStackEntry?.isShowing(route: KClass<out Any>): Boolean {
    val destination = this?.destination ?: return false
    return destination.hasRoute(route) ||
        (route == SettingsScreen::class && destination.hasRoute(ReadingSettingsScreen::class))
}