package com.wikirandomizerp.app.ui

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateTo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import com.wikirandomizerp.app.ui.backupScreen.BackupScreen
import com.wikirandomizerp.app.ui.image.ImagePrefetcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.R.string
import com.wikirandomizerp.app.BuildConfig
import com.wikirandomizerp.app.FeatureFlags
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.DeveloperMode
import com.wikirandomizerp.app.data.billing.DonationState
import com.wikirandomizerp.app.data.sync.SyncAuthState
import com.wikirandomizerp.app.data.SearchHistoryItem
import com.wikirandomizerp.app.data.UpdateResult
import com.wikirandomizerp.app.data.UpdateState
import com.wikirandomizerp.app.data.UserLanguage
import com.wikirandomizerp.app.ui.debugScreen.DebugScreen
import com.wikirandomizerp.app.ui.donation.DonationDialog
import com.wikirandomizerp.app.ui.curatorsScreen.CuratorsScreenRoot
import com.wikirandomizerp.app.ui.historyScreen.HistoryScreenRoot
import com.wikirandomizerp.app.ui.highlightsScreen.HighlightsScreenRoot
import com.wikirandomizerp.app.ui.homeScreen.AppHomeScreen
import com.wikirandomizerp.app.ui.homeScreen.search.AppSearchBar
import com.wikirandomizerp.app.ui.homeScreen.viewModel.ArticleTab
import com.wikirandomizerp.app.ui.homeScreen.VerticalFeedScreen
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeAction
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeScreenViewModel
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen
import com.wikirandomizerp.app.ui.savedArticlesScreen.SavedArticlesScreenRoot
import com.wikirandomizerp.app.ui.searchScreen.LibrarySearchScreenRoot
import com.wikirandomizerp.app.ui.commonsScreen.CommonsGalleryScreen
import com.wikirandomizerp.app.ui.dictionaryScreen.DictionaryDialog
import com.wikirandomizerp.app.ui.dictionaryScreen.VocabularyScreen
import com.wikirandomizerp.app.ui.settingsScreen.BugReportDialog
import com.wikirandomizerp.app.ui.settingsScreen.ReadingSettingsScreenRoot
import com.wikirandomizerp.app.ui.statsScreen.StatisticsScreen
import com.wikirandomizerp.app.ui.settingsScreen.SettingsScreenRoot
import com.wikirandomizerp.app.ui.kiwix.KiwixScreen
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsAction
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsViewModel
import com.wikirandomizerp.app.ui.randomTopicsScreen.RandomTopicsScreen
import com.wikirandomizerp.app.ui.tutorial.WelcomeTutorial


/** Finds the owning Activity through Compose/themed ContextWrapper layers. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppScreen(
    viewModel: HomeScreenViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val preferencesState by settingsViewModel.preferencesState.collectAsStateWithLifecycle()

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val appSearchBarState by viewModel.appSearchBarState.collectAsStateWithLifecycle()
    val homeScreenState by viewModel.homeScreenState.collectAsStateWithLifecycle()
    val searchListState by viewModel.searchListState.collectAsStateWithLifecycle()
    val showTutorial by viewModel.showTutorial.collectAsStateWithLifecycle()
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()
    val topicExhausted by viewModel.topicExhausted.collectAsStateWithLifecycle()
    val donationPromptAt by viewModel.donationPromptAt.collectAsStateWithLifecycle()
    val application = remember(context) {
        context.applicationContext as? WikiRandomizerPApplication
    }
    val donationBilling = application?.container?.donationBilling
    val localSyncStore = application?.container?.localSyncStore
    val syncAuth = if (localSyncStore != null) {
        localSyncStore.authState.collectAsStateWithLifecycle(
            initialValue = localSyncStore.currentAuth
        ).value
    } else {
        SyncAuthState.NotConfigured
    }
    var donationState by remember(donationBilling) {
        mutableStateOf(donationBilling?.state?.value ?: DonationState())
    }
    LaunchedEffect(donationBilling) {
        donationBilling?.state?.collect { donationState = it }
    }
    LaunchedEffect(donationBilling, localSyncStore) {
        if (localSyncStore?.cloudConfigured == true) donationBilling?.start()
    }
    // A completed purchase unlocks the same supporter features the debug switch does. This only
    // ever turns the flag on: a temporary Billing disconnect must not revoke it.
    LaunchedEffect(donationState.supporter, donationState.subscription) {
        if ((donationState.supporter || donationState.subscription) && !preferencesState.supporterMode) {
            settingsViewModel.onAction(SettingsAction.SaveSupporterMode(true))
        }
    }
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }
    var showFeatureRequestDialog by rememberSaveable { mutableStateOf(false) }
    val hostActivity = remember(context) { context.findActivity() }
    val dictionaryLookup by viewModel.dictionaryLookup.collectAsStateWithLifecycle()

    val backStack = viewModel.backStack

    val searchHistory by viewModel.searchHistoryFlow.collectAsState(emptyList())
    val recentLangs by viewModel.recentLangsFlow.collectAsStateWithLifecycle(emptyList())
    val userLangs by viewModel.userLangsFlow.collectAsState(
        listOf(
            UserLanguage(
                "en",
                "English",
                true
            )
        )
    )

    val searchBarState = rememberSearchBarState()
    val railState = rememberWideNavigationRailState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Immersive mode must be re-applied after the window gains focus or the activity resumes.
    // LocalView.context is often a ContextThemeWrapper rather than the Activity itself, so unwrap
    // it instead of relying on a fragile direct cast. The gallery keeps control of its own bars.
    val view = LocalView.current
    val imageOpen = backStack.lastOrNull() is com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen.Image
    val activityWindow = remember(view) { view.context.findActivity()?.window }
    val immersiveReading = preferencesState.immersiveMode && !imageOpen

    DisposableEffect(activityWindow, view, immersiveReading, imageOpen) {
        val window = activityWindow
        if (window == null || imageOpen) {
            return@DisposableEffect onDispose { }
        }

        val controller = WindowCompat.getInsetsController(window, view)
        fun syncSystemBars() {
            // Wait until the Compose view is attached and the Activity owns window focus. The
            // focus listener below retries this as soon as Android grants focus.
            if (!view.isAttachedToWindow || !window.decorView.hasWindowFocus()) return
            if (immersiveReading) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) view.post { syncSystemBars() }
        }
        var registeredFocusObserver: ViewTreeObserver? = null
        fun registerFocusListener() {
            val observer = view.viewTreeObserver
            if (observer.isAlive && observer !== registeredFocusObserver) {
                registeredFocusObserver?.let { old ->
                    if (old.isAlive) old.removeOnWindowFocusChangeListener(focusListener)
                }
                observer.addOnWindowFocusChangeListener(focusListener)
                registeredFocusObserver = observer
            }
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                registerFocusListener()
                v.post { syncSystemBars() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(attachListener)
        registerFocusListener()
        view.post { syncSystemBars() }

        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
            registeredFocusObserver?.let { observer ->
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }

    // LaunchedEffect alone only runs when its keys change. Re-apply the preference when returning
    // from another app, the lock screen, or a permission/system window as Android may restore bars.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val window = activityWindow
        if (window != null && !imageOpen) {
            view.post {
                if (view.isAttachedToWindow && window.decorView.hasWindowFocus()) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    if (preferencesState.immersiveMode) {
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.statusBars())
                    } else {
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                        controller.show(WindowInsetsCompat.Type.statusBars())
                    }
                }
            }
        }
    }
    val languageSearchStr by viewModel.languageSearchStr.collectAsStateWithLifecycle()
    val languageSearchQuery by viewModel.languageSearchQuery.collectAsState("")
    val motionScheme = motionScheme
    var showArticleLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var deepLinkHandled by rememberSaveable { mutableStateOf(false) }
    var tabPanelOpen by rememberSaveable { mutableStateOf(false) }

    val searchBarScrollBehavior =
        if (
            !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ||
            preferencesState.immersiveMode
        ) TopAppBarDefaults.enterAlwaysScrollBehavior()
        else TopAppBarDefaults.pinnedScrollBehavior()
    val floatingToolbarScrollBehaviour =
        if (
            !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ||
            preferencesState.immersiveMode
        ) FloatingToolbarDefaults.exitAlwaysScrollBehavior(
            exitDirection = FloatingToolbarExitDirection.Bottom,
            snapAnimationSpec = motionScheme.defaultSpatialSpec()
        )
        else null
    val textFieldState = viewModel.textFieldState

    val snackBarHostState = viewModel.snackBarHostState

    // Holding a link adds its article to the tab list in the background. Every time: a short
    // animation at the right edge (where the tab panel lives). The first time ever: one message
    // that says what happened and offers to open the panel. Never a message after that.
    var tabAddedPulse by remember { mutableIntStateOf(0) }
    var tabAddedCount by remember { mutableIntStateOf(0) }
    var tabAddedAlreadyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.tabAdded.collect { event ->
            tabAddedCount = event.tabCount
            tabAddedAlreadyOpen = event.alreadyOpen
            tabAddedPulse++
            if (event.first) {
                launch {
                    val result = snackBarHostState.showSnackbar(
                        message = context.getString(R.string.addedToTabsFirst),
                        actionLabel = context.getString(R.string.tabs),
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) tabPanelOpen = true
                }
            }
        }
    }

    val (showDeleteDialog, setShowDeleteDialog) = remember { mutableStateOf(false) }
    var historyItem: SearchHistoryItem? by remember { mutableStateOf(null) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val kiwixStatus by viewModel.kiwixStatus.collectAsStateWithLifecycle()

    // When the user opted into Kiwix, the Random button (toolbar, pull-to-refresh and tutorial)
    // opens a random article from the ZIM instead of Wikipedia. Everything else keeps working.
    val onHomeAction: (HomeAction) -> Unit = { action ->
        if (action is HomeAction.LoadRandom && kiwixStatus.ready && kiwixStatus.useForRandom) {
            navController.navigate(KiwixReader(random = true)) {
                launchSingleTop = true
            }
        } else {
            viewModel.onAction(action)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    val navigateTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Schedule the hourly sync and try once at start when the user opted in and is signed in.
    LaunchedEffect(localSyncStore, syncAuth) {
        val store = localSyncStore ?: return@LaunchedEffect
        store.applyAutoSyncSchedule()
        if (store.autoSync && store.hasCloudAccess() && syncAuth is SyncAuthState.SignedIn) {
            runCatching { store.syncNowIfAllowed() }
        }
    }

    DisposableEffect(localSyncStore) {
        val store = localSyncStore
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        if (store == null || connectivity == null) return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!store.autoSync || !store.hasCloudAccess()) return
                scope.launch(Dispatchers.IO) {
                    runCatching { store.syncNowIfAllowed() }
                }
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    // One background release check per process. The result is shared with Settings, so opening the
    // update entry never repeats the network round-trip. A failed check stays silent: it must never
    // interrupt reading or look like an app error.
    LaunchedEffect(Unit) {
        val app = context.applicationContext as? WikiRandomizerPApplication
            ?: return@LaunchedEffect
        if (UpdateState.checking.value) return@LaunchedEffect
        UpdateState.checking.value = true
        val result = app.container.updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
        UpdateState.checking.value = false
        UpdateState.lastCheckedAt.value = System.currentTimeMillis()
        when (result) {
            is UpdateResult.Available -> {
                UpdateState.available.value = result.info
                if (!UpdateState.promptedThisSession.value) {
                    UpdateState.promptedThisSession.value = true
                    val snackbar = snackBarHostState.showSnackbar(
                        message = context.getString(
                            R.string.updateAvailable,
                            result.info.version
                        ),
                        actionLabel = context.getString(R.string.viewUpdate),
                        withDismissAction = true
                    )
                    if (snackbar == SnackbarResult.ActionPerformed) navigateTab(SettingsScreen)
                }
            }

            is UpdateResult.UpToDate -> DebugLog.i("Update", "app is up to date")
            is UpdateResult.Error -> DebugLog.w(
                "Update",
                "background update check failed: ${result.message}"
            )
        }
    }

    AppNavigationDrawer(
        state = railState,
        drawerState = drawerState,
        homeBackStackEntry = backStack.last(),
        windowSizeClass = windowSizeClass,
        backStackEntry = navBackStackEntry,
        historyEnabled = preferencesState.browsingHistory,
        isImageView = backStack.last() is HomeSubscreen.Image,
        onVerticalFeedClick = {
            navController.navigate(VerticalFeedRoute) { launchSingleTop = true }
        },
        onHistoryClick = {
            navController.navigate(HistoryScreen) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onLibrarySearchClick = {
            navController.navigate(LibrarySearchRoute) { launchSingleTop = true }
        },
        onHomeClick = {
            if (navBackStackEntry?.destination?.hasRoute(HomeScreen::class) == true) {
                viewModel.onAction(HomeAction.LoadFeed(true))
            } else {
                navController.navigate(HomeScreen()) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        onSavedArticlesClick = {
            navController.navigate(SavedArticlesScreen) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onHighlightsClick = {
            navController.navigate(HighlightsScreen) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onSettingsClick = {
            navController.navigate(SettingsScreen) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onCuratorsClick = {
            navController.navigate(CuratorsScreen) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onTabsClick = {
            // Opening both side panels at once animated two heavy sheets and made the transition
            // stutter. Close the drawer first, then reveal the tabs panel.
            scope.launch {
                drawerState.close()
                tabPanelOpen = true
            }
        },
        menuOnRight = preferencesState.menuSide == "right",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val compactWindow =
            !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        NavHost(
            navController = navController,
            startDestination = HomeScreen(),
            enterTransition = {
                if (compactWindow)
                    slideInHorizontally(initialOffsetX = { it })
                else
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(220, delayMillis = 90)
                            )
            },
            exitTransition = {
                if (compactWindow)
                    slideOutHorizontally(targetOffsetX = { -it / 4 })
                else
                    fadeOut(animationSpec = tween(90))
            },
            popEnterTransition = {
                if (compactWindow)
                    slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                else
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(220, delayMillis = 90)
                            )
            },
            popExitTransition = {
                if (compactWindow)
                    slideOutHorizontally(targetOffsetX = { it })
                else
                    fadeOut(animationSpec = tween(90))
            }
        ) {
            composable<HomeScreen>(
                deepLinks = listOf(
                    navDeepLink { uriPattern = "{lang}.m.wikipedia.org/wiki/{query}" },
                    navDeepLink { uriPattern = "{lang}.wikipedia.org/wiki/{query}" }
                )
            ) { backStackEntry ->
                val uriQuery = remember { backStackEntry.arguments?.getString("query") }
                LaunchedEffect(uriQuery) {
                    if (uriQuery != null && !deepLinkHandled) {
                        deepLinkHandled = true
                        val lang = backStackEntry.arguments?.getString("lang")
                        viewModel.onAction(HomeAction.StopAll)
                        delay(500) // Avoids a race condition where the hostname might not get updated in time
                        viewModel.onAction(HomeAction.LoadPage(uriQuery, lang = lang))
                    }
                }

                if (showDeleteDialog)
                    DeleteHistoryItemDialog(
                        historyItem,
                        setShowDeleteDialog,
                        viewModel::removeSearchHistoryItem
                    )

                Scaffold(
                    topBar = {
                        AnimatedVisibility(
                            backStack.last() !is HomeSubscreen.Image,
                            enter = slideInVertically(
                                motionScheme.defaultSpatialSpec(),
                                initialOffsetY = { -it }
                            ) + expandVertically(motionScheme.defaultSpatialSpec()),
                            exit = fadeOut()
                        ) {
                            AppSearchBar(
                                appSearchBarState = appSearchBarState,
                                searchBarState = searchBarState,
                                preferencesState = preferencesState,
                                textFieldState = textFieldState,
                                userLangs = userLangs,
                                recentLangs = recentLangs,
                                searchHistory = searchHistory,
                                scrollBehavior = searchBarScrollBehavior,
                                searchBarEnabled = !showArticleLanguageSheet,
                                searchListState = searchListState,
                                windowSizeClass = windowSizeClass,
                                languageSearchStr = languageSearchStr,
                                languageSearchQuery = languageSearchQuery,
                                onAction = onHomeAction,
                                onSettingsAction = settingsViewModel::onAction,
                                onSearchBarExpandedChange = {
                                    scope.launch {
                                        if (it) searchBarState.animateToExpanded()
                                        else searchBarState.animateToCollapsed()
                                    }
                                },
                                onNavigateToRandomTopics = {
                                    navController.navigate(RandomTopicsScreen) {
                                        launchSingleTop = true
                                    }
                                },
                                clearHistory = {
                                    historyItem = null
                                    setShowDeleteDialog(true)
                                },
                                removeHistoryItem = {
                                    historyItem = it
                                    setShowDeleteDialog(true)
                                },
                                onMenuIconClicked = {
                                    scope.launch {
                                        if (compactWindow) drawerState.open()
                                        else railState.expand()
                                    }
                                }
                            )
                        }
                    },
                    // Keep snackbars above the floating reading toolbar instead of covering it.
                    snackbarHost = {
                        SnackbarHost(
                            snackBarHostState,
                            modifier = Modifier.padding(bottom = 88.dp)
                        )
                    },
                    contentWindowInsets =
                        if (!compactWindow)
                            ScaffoldDefaults.contentWindowInsets
                                .only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End)
                        else ScaffoldDefaults.contentWindowInsets,
                    modifier = (if (floatingToolbarScrollBehaviour != null)
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                            .nestedScroll(floatingToolbarScrollBehaviour)
                    else
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                    )
                ) { insets ->
                    AppHomeScreen(
                        backStack = backStack,
                        homeScreenState = homeScreenState,
                        preferencesState = preferencesState,
                        recentLangs = recentLangs,
                        floatingToolbarScrollBehaviour = floatingToolbarScrollBehaviour,
                        languageSearchStr = languageSearchStr,
                        languageSearchQuery = languageSearchQuery,
                        showLanguageSheet = showArticleLanguageSheet,
                        deepLinkHandled = deepLinkHandled,
                        onAction = onHomeAction,
                        onSettingsAction = settingsViewModel::onAction,
                        onNavigateToRandomTopics = {
                            navController.navigate(RandomTopicsScreen) {
                                launchSingleTop = true
                            }
                        },
                        setShowArticleLanguageSheet = { showArticleLanguageSheet = it },
                        insets = insets,
                        windowSizeClass = windowSizeClass,
                        modifier = Modifier.fillMaxSize()
                    )

                    AnimatedVisibility(
                        backStack.last() !is HomeSubscreen.Image,
                        enter = fadeIn(motionScheme.slowEffectsSpec()),
                        exit = fadeOut(motionScheme.fastEffectsSpec())
                    ) {
                        StatusBarProtection()
                    }
                }

                // First-launch tour (and "Welcome tutorial" in Settings). It is a dialog, so it also
                // covers the search bar; it is only shown while the home screen is the current page.
                if (showTutorial && navBackStackEntry?.destination?.hasRoute(HomeScreen::class) == true) {
                    WelcomeTutorial(
                        step = tutorialStep,
                        onStepChange = viewModel::setTutorialStep,
                        onOpenTopics = {
                            navController.navigate(RandomTopicsScreen) {
                                launchSingleTop = true
                            }
                        },
                        onTryRandom = { onHomeAction(HomeAction.LoadRandom) },
                        onOpenSettings = {
                            navController.navigate(SettingsScreen) {
                                launchSingleTop = true
                            }
                        },
                        onFinish = viewModel::finishTutorial
                    )
                }

                // Every article in the selected topics has been read: offer a reset instead of
                // silently showing an error page.
                if (topicExhausted) {
                    BasicAlertDialog(onDismissRequest = viewModel::dismissTopicExhausted) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = AlertDialogDefaults.TonalElevation
                        ) {
                            Column(Modifier.padding(24.dp)) {
                                Text(
                                    text = stringResource(R.string.topicExhaustedTitle),
                                    style = typography.headlineSmall
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.topicExhaustedDesc),
                                    style = typography.bodyMedium
                                )
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = viewModel::dismissTopicExhausted) {
                                        Text(stringResource(R.string.no))
                                    }
                                    TextButton(onClick = viewModel::resetTopicHistoryAndReload) {
                                        Text(stringResource(R.string.yes))
                                    }
                                }
                            }
                        }
                    }
                }

                dictionaryLookup?.let { lookup ->
                    DictionaryDialog(
                        lookup = lookup,
                        onAddToVocabulary = {
                            viewModel.onAction(HomeAction.AddVocabularyWord(lookup.word))
                            viewModel.dismissDictionary()
                        },
                        onDismiss = viewModel::dismissDictionary
                    )
                }
            }

            composable<SavedArticlesScreen> {
                SavedArticlesScreenRoot(
                    imageBackground = preferencesState.imageBackground,
                    openSavedArticle = { pageId: Int, lang: String ->
                        navController.navigateUp()
                        viewModel.onAction(HomeAction.LoadSavedArticle(pageId, lang))
                    },
                    onBack = navController::navigateUp,
                    saveFolders = preferencesState.saveFolders,
                    saveNotes = preferencesState.saveNotes
                )
            }

            composable<HighlightsScreen> {
                HighlightsScreenRoot(
                    openArticle = { title, lang ->
                        viewModel.onAction(HomeAction.LoadPage(title, lang))
                        navController.navigate(HomeScreen()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onBack = navController::navigateUp
                )
            }

            composable<VerticalFeedRoute> {
                VerticalFeedScreen(
                    feed = backStack.getOrNull(0) as? HomeSubscreen.Feed,
                    onOpenArticle = { title, lang ->
                        viewModel.onAction(HomeAction.LoadPage(title, lang))
                        navController.navigateUp()
                    },
                    onBack = navController::navigateUp
                )
            }

            composable<HistoryScreen> {
                HistoryScreenRoot(
                    imageBackground = preferencesState.imageBackground,
                    openArticle = { title, lang ->
                        viewModel.onAction(HomeAction.LoadPage(title, lang))
                        navController.navigateUp()
                    },
                    onBack = navController::navigateUp
                )
            }
            composable<LibrarySearchRoute> {
                LibrarySearchScreenRoot(
                    onOpenArticle = { title, lang ->
                        viewModel.onAction(HomeAction.LoadPage(title, lang))
                        navController.navigateUp()
                    },
                    onOpenSavedArticle = { pageId, lang ->
                        navController.navigateUp()
                        viewModel.onAction(HomeAction.LoadSavedArticle(pageId, lang))
                    },
                    onBack = navController::navigateUp
                )
            }

            composable<SettingsScreen> {
                SettingsScreenRoot(
                    preferencesState = preferencesState,
                    lastBackStackEntry = backStack.last(),
                    recentLangs = recentLangs,
                    languageSearchStr = languageSearchStr,
                    languageSearchQuery = languageSearchQuery,
                    onHomeAction = onHomeAction,
                    onNavigateToReadingSettings = {
                        navController.navigate(ReadingSettingsScreen) {
                            launchSingleTop = true
                        }
                    },
                    onReplayTutorial = {
                        viewModel.replayTutorial()
                        navController.navigateUp()
                    },
                    onOpenDonation = {
                        donationBilling?.clearMessage()
                        donationBilling?.refresh()
                        showDonationDialog = true
                    },
                    onOpenDebug = {
                        navController.navigate(DebugRoute) { launchSingleTop = true }
                    },
                    onGetLibraryStats = { viewModel.getLibraryStats() },
                    onSetTopicEnabled = { topic, enabled ->
                        viewModel.setTopicEnabled(topic, enabled)
                    },
                    onNavigateToVocabulary = {
                        navController.navigate(VocabularyRoute) { launchSingleTop = true }
                    },
                    onNavigateToStatistics = {
                        navController.navigate(StatisticsRoute) { launchSingleTop = true }
                    },
                    onBackup = {
                        navController.navigate(BackupRoute) { launchSingleTop = true }
                    },
                    onBack = navController::navigateUp
                )
            }

            composable<VocabularyRoute> {
                val words by remember {
                    viewModel.getVocabularyWords()
                }.collectAsStateWithLifecycle(emptyList())
                VocabularyScreen(
                    words = words,
                    onDelete = viewModel::deleteVocabularyWord,
                    onBack = navController::navigateUp
                )
            }

            composable<BackupRoute> {
                BackupScreen(
                    onListAutoBackups = viewModel::listAutoBackups,
                    onExport = viewModel::exportBackup,
                    onShare = viewModel::createBackup,
                    onRestoreFile = { uri -> viewModel.restoreBackup(uri = uri) },
                    onRestoreAuto = { file -> viewModel.restoreBackup(file = file) },
                    onBack = navController::navigateUp
                )
            }

            composable<StatisticsRoute> {
                StatisticsScreen(
                    onGetStats = viewModel::getReadingStats,
                    onBack = navController::navigateUp
                )
            }

            composable<CommonsGalleryRoute> { entry ->
                val route = entry.toRoute<CommonsGalleryRoute>()
                CommonsGalleryScreen(
                    title = route.title,
                    onGetImages = viewModel::getCommonsImages,
                    onBack = navController::navigateUp
                )
            }

            composable<CuratorsScreen> {
                CuratorsScreenRoot(
                    openArticle = { title, lang ->
                        viewModel.onAction(HomeAction.LoadPage(title, lang))
                        navController.navigate(HomeScreen()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onBack = navController::navigateUp
                )
            }

            composable<DebugRoute> {
                DebugScreen(
                    donationState = donationState,
                    donationPrompts = preferencesState.donationPrompts,
                    debugLogging = preferencesState.debugLogging,
                    supporterMode = preferencesState.supporterMode,
                    donationPromptsSuppressed = DeveloperMode.donationPromptsSuppressed(context),
                    onBack = navController::navigateUp,
                    onShowDonationPrompt = {
                        navController.navigateUp()
                        viewModel.showDonationPromptForTest()
                    },
                    onOpenDonationDialog = {
                        donationBilling?.clearMessage()
                        donationBilling?.refresh()
                        showDonationDialog = true
                    },
                    onSimulatePurchase = { donationBilling?.debugSimulatePurchase() },
                    onResetDonation = {
                        donationBilling?.debugReset()
                        settingsViewModel.onAction(SettingsAction.SaveSupporterMode(false))
                    },
                    onSetDonationPrompts = {
                        settingsViewModel.onAction(SettingsAction.SaveDonationPrompts(it))
                    },
                    onSetDebugLogging = {
                        settingsViewModel.onAction(SettingsAction.SaveDebugLogging(it))
                    },
                    onSetSupporterMode = {
                        settingsViewModel.onAction(SettingsAction.SaveSupporterMode(it))
                    },
                    onSetDonationPromptsSuppressed = {
                        DeveloperMode.setDonationPromptsSuppressed(context, it)
                        if (it) viewModel.dismissDonationPrompt()
                    },
                    onOpenFeatureRequests = { showFeatureRequestDialog = true },
                    onDisableDeveloperMode = {
                        DeveloperMode.setEnabled(context, false)
                        navController.navigateUp()
                    }
                )
            }

            composable<ReadingSettingsScreen> {
                ReadingSettingsScreenRoot(
                    onNavigateToRandomTopics = {
                        navController.navigate(RandomTopicsScreen) {
                            launchSingleTop = true
                        }
                    },
                    onGetLibraryStats = { viewModel.getLibraryStats() },
                    kiwixStatus = kiwixStatus,
                    onSelectKiwix = viewModel::selectKiwixDatabase,
                    onRemoveKiwix = viewModel::removeKiwixDatabase,
                    onSetKiwixForRandom = viewModel::setKiwixForRandom,
                    onOpenKiwixReader = {
                        navController.navigate(KiwixReader()) { launchSingleTop = true }
                    },
                    onBack = navController::navigateUp
                )
            }

            composable<KiwixReader> { entry ->
                val route = entry.toRoute<KiwixReader>()
                KiwixScreen(
                    repository = viewModel.kiwixRepository,
                    startPath = route.path.takeIf { it.isNotBlank() },
                    random = route.random,
                    onBack = navController::navigateUp
                )
            }

            composable<RandomTopicsScreen> {
                val indexState by viewModel.indexState.collectAsStateWithLifecycle()
                RandomTopicsScreen(
                    onGetTopics = { viewModel.getRandomTopics() },
                    onSaveTopics = { topics, force ->
                        // Persist the selection (and enabled flags) before building, and await it:
                        // starting the build first could race with the save and pick up the old set.
                        viewModel.saveTopicSelection(topics)
                        viewModel.startIndexBuild(topics, force)
                    },
                    indexState = indexState,
                    onCancelBuild = viewModel::cancelIndexBuild,
                    onResolveInterests = { text, status -> viewModel.resolveInterests(text, status) },
                    onGetInterestText = { viewModel.getInterestText() },
                    onSaveInterestText = { viewModel.saveInterestText(it) },
                    onGetLibraryStats = { viewModel.getLibraryStats() },
                    onSetTopicEnabled = { topic, enabled -> viewModel.setTopicEnabled(topic, enabled) },
                    onRemoveTopic = { topic -> viewModel.removeTopicFromLibrary(topic) },
                    onEstimateOffline = { count -> viewModel.estimateOfflineDownload(count) },
                    onDownloadOffline = { count, progress ->
                        viewModel.downloadOfflineArticles(count, progress)
                    },
                    onGetOfflineStats = { viewModel.getOfflineStats() },
                    onDeleteOffline = { viewModel.deleteOfflineArticles() },
                    onSaveTopicHints = { viewModel.saveTopicHints(it) },
                    onResetSeen = { viewModel.resetSeenArticles() },
                    onStartBrowsing = {
                        // Do not cancel the build: it continues behind the reader.
                        navController.navigate(HomeScreen()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onBack = navController::navigateUp
                )
            }
        }
    }
    val currentArticle = backStack.lastOrNull() as? HomeSubscreen.Article
    // Image-less articles get a "Find images" shortcut in the tab panel. The lead photo and the
    // body pictures are both checked, so an article with illustrations but no infobox image is
    // not flagged.
    val articleHasImages = currentArticle?.let { article ->
        article.photo != null || article.thumbnail != null ||
                article.extract.any { section ->
                    section.any { block ->
                        val text = block.text.trimStart()
                        text.startsWith("[[File:", ignoreCase = true) ||
                                text.startsWith("<gallery", ignoreCase = true)
                    }
                }
    } ?: true
    val imageSearchTitle = currentArticle?.title?.takeIf {
        !articleHasImages && it.isNotBlank() && it != "Error"
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    ArticleTabPanel(
        open = tabPanelOpen,
        onOpenChange = { tabPanelOpen = it },
        currentRoute = navBackStackEntry?.destination,
        tabs = homeScreenState.tabs,
        activeTabId = homeScreenState.activeTabId,
        canGoBack = homeScreenState.canGoBack,
        canGoForward = homeScreenState.canGoForward,
        canOpenInBrowser = currentArticle != null && currentArticle.title != "Error",
        imageSearchTitle = imageSearchTitle,
        onFindImages = { title ->
            navController.navigate(CommonsGalleryRoute(title)) { launchSingleTop = true }
        },
        onOpenInBrowser = {
            currentArticle?.let {
                viewModel.onAction(
                    HomeAction.OpenInBrowser(
                        title = it.title,
                        lang = it.currentLang ?: preferencesState.lang
                    )
                )
            }
        },
        onBack = { viewModel.onAction(HomeAction.NavigateBack) },
        onForward = { viewModel.onAction(HomeAction.NavigateForward) },
        onSelectTab = { viewModel.onAction(HomeAction.SwitchTab(it)) },
        onCloseTab = { viewModel.onAction(HomeAction.CloseTab(it)) },
        onNewTab = { viewModel.onAction(HomeAction.NewTab) },
        recentlyClosed = homeScreenState.recentlyClosed,
        onReopenClosedTab = { viewModel.onAction(HomeAction.ReopenClosedTab) }
    )

    TabAddedFlash(
        pulse = tabAddedPulse,
        count = tabAddedCount,
        alreadyOpen = tabAddedAlreadyOpen
    )

    // Optional donation milestone (disabled unless the user turns it on in Settings).
    donationPromptAt?.let { milestone ->
        BasicAlertDialog(onDismissRequest = viewModel::dismissDonationPrompt) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.donationPromptTitle),
                        style = typography.headlineSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.donationPromptDesc, milestone),
                        style = typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = viewModel::dismissDonationPrompt) {
                            Text(stringResource(R.string.later))
                        }
                        TextButton(
                            onClick = {
                                viewModel.dismissDonationPrompt()
                                if (donationBilling != null) {
                                    donationBilling.clearMessage()
                                    donationBilling.refresh()
                                    showDonationDialog = true
                                } else {
                                    uriHandler.openUri("https://github.com/PatrickJamesDev")
                                }
                            }
                        ) {
                            Text(stringResource(R.string.donateNow))
                        }
                    }
                }
            }
        }
    }

    if (showFeatureRequestDialog) {
        BugReportDialog(
            onDismiss = { showFeatureRequestDialog = false },
            onCopied = { showFeatureRequestDialog = false }
        )
    }

    if (showDonationDialog) {
        DonationDialog(
            state = donationState,
            onDismiss = {
                showDonationDialog = false
                donationBilling?.clearMessage()
            },
            onDonate = { productId ->
                val activity = hostActivity
                if (activity != null && donationBilling != null) {
                    donationBilling.donate(activity, productId)
                }
            },
            onRefresh = { donationBilling?.refresh() },
            onOpenGithub = {
                showDonationDialog = false
                donationBilling?.clearMessage()
                uriHandler.openUri("https://github.com/PatrickJamesDev")
            }
        )
    }
    }
}

/**
 * The article tab panel. It follows the finger in from the right edge, shows every open article
 * tab, and also carries unlimited back/forward plus the app shortcuts that used to live in the
 * narrow rail. It overlays the article instead of resizing it, so opening it never reflows text.
 * There is no permanent handle: the panel is reachable by swiping from the edge or from the Tabs
 * entry in the navigation drawer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArticleTabPanel(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    currentRoute: NavDestination?,
    tabs: List<ArticleTab>,
    activeTabId: Long?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    canOpenInBrowser: Boolean,
    imageSearchTitle: String? = null,
    onFindImages: (String) -> Unit = {},
    onOpenInBrowser: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    recentlyClosed: List<ArticleTab>,
    onReopenClosedTab: () -> Unit
) {
    // Back closes the panel first, like the navigation drawer on the left.
    BackHandler(enabled = open) { onOpenChange(false) }

    val density = LocalDensity.current
    val dragState = remember {
        AnchoredDraggableState(initialValue = TabPanelValue.Closed)
    }
    var anchorsReady by remember { mutableStateOf(false) }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        dragState,
        { distance -> distance * 0.5f },
        tween(260)
    )
    // Stable instance: the drag modifier must not be re-created while a finger is on it.
    val stableFling = remember(dragState) { flingBehavior }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = (maxWidth * 0.88f).coerceAtMost(320.dp)
        val panelWidthPx = with(density) { panelWidth.toPx() }

        LaunchedEffect(panelWidthPx) {
            dragState.updateAnchors(
                DraggableAnchors {
                    TabPanelValue.Closed at panelWidthPx
                    TabPanelValue.Open at 0f
                }
            )
            anchorsReady = true
        }

        LaunchedEffect(open, panelWidthPx, anchorsReady) {
            val target = if (open) TabPanelValue.Open else TabPanelValue.Closed
            if (anchorsReady && dragState.currentValue != target &&
                !dragState.isAnimationRunning
            ) {
                dragState.animateTo(target)
            }
        }

        // The drawer's Tabs entry and the close button set `open`; a finger drag settles the
        // anchored state, so mirror its settled value back into the hoisted state.
        LaunchedEffect(dragState) {
            snapshotFlow { dragState.settledValue }.collect { value ->
                onOpenChange(value == TabPanelValue.Open)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val offset = if (anchorsReady) dragState.offset else if (open) 0f else panelWidthPx
                    val fraction = if (offset.isNaN()) 0f
                    else (1f - offset / panelWidthPx).coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = 0.32f * fraction))
                }
                // The scrim is decorative: when closed it must not sit in the accessibility
                // tree as an invisible full-screen clickable node, and TalkBack users can
                // dismiss the panel with the visible close button instead. It also must not
                // install a full-screen tap detector while closed, or it would swallow every
                // touch in the app (the panel is composed above the whole screen).
                .then(
                    if (open) Modifier
                        .clearAndSetSemantics {}
                        .pointerInput(Unit) {
                            detectTapGestures { onOpenChange(false) }
                        }
                    else Modifier
                )
        )

        // The same sheet as the navigation drawer on the left (container colour, corner radius,
        // elevation, 320 dp width, window insets, title, item and spacing), just anchored to the
        // right edge and dragged in with the finger.
        //
        // The drag handler sits on a STATIONARY wrapper, and only the sheet inside it is translated.
        // It used to be attached to the sheet itself, after the graphicsLayer that the same drag
        // moves: the finger's position relative to a node that moves with it barely changes, so the
        // drag measured about half its real distance and stuttered. The navigation drawer on the
        // left (ModalNavigationDrawer) puts its drag handler on the fixed container too, which is
        // why it felt smoother.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(panelWidth)
                .fillMaxHeight()
                .then(
                    // While closed the wrapper must not sit in the way of the article; the edge strip
                    // below starts the drag then.
                    if (open) Modifier.anchoredDraggable(
                        dragState,
                        Orientation.Horizontal,
                        flingBehavior = stableFling
                    ) else Modifier
                )
        ) {
        ModalDrawerSheet(
            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            drawerContentColor = MaterialTheme.colorScheme.onSurface,
            drawerShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            drawerTonalElevation = 6.dp,
            windowInsets = WindowInsets.systemBars
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
            modifier = Modifier
                .fillMaxSize()
                // graphicsLayer keeps the panel on the render thread: laying it out with
                // Modifier.offset on every animation frame made the opening animation jitter.
                .graphicsLayer {
                    val offset = if (anchorsReady) dragState.offset
                    else if (open) 0f else panelWidthPx
                    translationX = if (offset.isNaN()) panelWidthPx else offset
                }
        ) {
            val showLanguage = remember(tabs) { tabs.map { it.lang }.distinct().size > 1 }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item(key = "tabs-title") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.tabs),
                            style = typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp)
                        )
                        IconButton(onClick = { onOpenChange(false) }) {
                            Icon(
                                painterResource(R.drawable.clear),
                                contentDescription = stringResource(R.string.closeTabPanel)
                            )
                        }
                    }
                }

                item(key = "tabs-actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 20.dp, bottom = 8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onOpenChange(false); onBack() },
                            enabled = canGoBack,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.articleBack)
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onOpenChange(false); onForward() },
                            enabled = canGoForward,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_forward),
                                contentDescription = stringResource(R.string.articleForward)
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onOpenChange(false); onOpenInBrowser() },
                            enabled = canOpenInBrowser,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                painterResource(R.drawable.open_in_browser),
                                contentDescription = stringResource(R.string.openInBrowser)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onOpenChange(false); onNewTab() }) {
                            Icon(
                                painterResource(R.drawable.add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.newTab))
                        }
                    }
                }

                if (tabs.isEmpty()) {
                    item(key = "tabs-empty") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.tab),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.noTabs),
                                style = typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.noTabsDesc),
                                style = typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    items(tabs, key = { it.id }) { tab ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    tab.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = tab.id == activeTabId,
                            onClick = {
                                onOpenChange(false)
                                onSelectTab(tab.id)
                            },
                            icon = { TabCover(tab.thumbnail) },
                            badge = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (showLanguage) {
                                        Text(
                                            tab.lang.uppercase(),
                                            style = typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.clear),
                                            contentDescription = stringResource(R.string.closeTab),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .animateItem()
                        )
                    }
                }
            }

            // Pinned under the list so it stays visible however far the tab list is scrolled.
            if (imageSearchTitle != null || recentlyClosed.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    recentlyClosed.lastOrNull()?.let { closed ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    stringResource(R.string.reopenClosedTab, closed.title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = false,
                            onClick = {
                                onOpenChange(false)
                                onReopenClosedTab()
                            },
                            icon = { Icon(painterResource(R.drawable.undo), null) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    // Only for articles that have no lead picture and no body pictures.
                    if (imageSearchTitle != null) {
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    stringResource(R.string.findImages),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = false,
                            onClick = {
                                onOpenChange(false)
                                onFindImages(imageSearchTitle)
                            },
                            icon = { Icon(painterResource(R.drawable.image), null) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
        }

        // No permanent handle: a narrow strip at the physical right edge starts the same
        // finger-following drag. A horizontal-only drag keeps vertical scrolling untouched.
        if (!open) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(24.dp)
                    .anchoredDraggable(
                        dragState,
                        Orientation.Horizontal,
                        flingBehavior = stableFling
                    )
            )
        }
    }
}

/**
 * The confirmation for "a link was held and its article went into the tabs": a pill with the tab
 * icon and the new tab count slides in from the right edge (the side the tab panel opens from),
 * a check mark pops on it, and it slides away again. It never takes touches.
 */
@Composable
private fun TabAddedFlash(pulse: Int, count: Int, alreadyOpen: Boolean) {
    val slide = remember { Animatable(0f) }      // 0 = hidden off the edge, 1 = fully shown
    val pop = remember { Animatable(0f) }        // the check mark
    val bump = remember { Animatable(1f) }       // the whole pill, a quick overshoot
    var shownCount by remember { mutableIntStateOf(count) }
    var shownAlreadyOpen by remember { mutableStateOf(alreadyOpen) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        shownCount = count
        shownAlreadyOpen = alreadyOpen
        pop.snapTo(0f)
        slide.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        launch {
            bump.snapTo(1f)
            bump.animateTo(1.18f, tween(110))
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        if (!shownAlreadyOpen) pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        delay(1000)
        slide.animateTo(0f, tween(260))
    }
    val visible by remember { derivedStateOf { slide.value > 0.001f } }
    if (!visible) return
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    translationX = (1f - slide.value) * (size.width + 24.dp.toPx())
                    scaleX = bump.value
                    scaleY = bump.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(R.drawable.tab), contentDescription = null, modifier = Modifier.size(30.dp))
                    Text(
                        shownCount.toString(),
                        style = typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!shownAlreadyOpen) {
                    Icon(
                        painterResource(R.drawable.check),
                        contentDescription = stringResource(R.string.addedToTabs),
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            }
                    )
                }
            }
        }
    }
}

private enum class TabPanelValue { Closed, Open }

/** The cover picture of a tab; the tab icon shows until it is loaded, or when there is none. */
@Composable
private fun TabCover(thumbnail: String?) {
    val model = remember(thumbnail) { ImagePrefetcher.smallThumbnailUrl(thumbnail) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Icon(
            painterResource(R.drawable.tab),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        if (!model.isNullOrBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeleteHistoryItemDialog(
    item: SearchHistoryItem?,
    setShowDeleteDialog: (Boolean) -> Unit,
    removeHistoryItem: (SearchHistoryItem?) -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = { setShowDeleteDialog(false) }
    ) {
        val titleText =
            if (item != null) stringResource(string.dialogDeleteSearchHistory)
            else stringResource(string.dialogDeleteSearchHistoryDesc)
        val descText =
            if (item != null) stringResource(string.dialogDeleteHistoryItem, item.query)
            else stringResource(string.dialogDeleteHistoryItemDesc)

        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = titleText,
                    style = typography.headlineSmall
                )
                Spacer(modifier = Modifier.padding(16.dp))
                Text(
                    text = descText,
                    style = typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { setShowDeleteDialog(false) }) {
                        Text(text = stringResource(string.cancel))
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        setShowDeleteDialog(false)
                        removeHistoryItem(item)
                    }) {
                        Text(text = stringResource(string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBarProtection(
    color: Color = MaterialTheme.colorScheme.surface,
    heightProvider: () -> Float = calculateGradientHeight(),
) {
    Canvas(Modifier.fillMaxSize()) {
        val calculatedHeight = heightProvider()
        val gradient = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 1f),
                color.copy(alpha = 0.7f),
                Color.Transparent
            ),
            startY = 0f,
            endY = calculatedHeight
        )
        drawRect(
            brush = gradient,
            size = Size(size.width, calculatedHeight),
        )
    }
}

@Composable
fun calculateGradientHeight(): () -> Float {
    val statusBars = WindowInsets.statusBars
    val density = LocalDensity.current
    return { statusBars.getTop(density).times(1.2f) }
}

@Serializable
data class HomeScreen(
    val lang: String? = null,
    val query: String? = null
)

@Serializable
object SavedArticlesScreen

@Serializable
object HighlightsScreen

@Serializable
object HistoryScreen

@Serializable
object LibrarySearchRoute

@Serializable
object SettingsScreen

@Serializable
object VerticalFeedRoute

@Serializable
object CuratorsScreen

@Serializable
object DebugRoute

@Serializable
object VocabularyRoute

@Serializable
object StatisticsRoute

@Serializable
object BackupRoute

@Serializable
data class CommonsGalleryRoute(val title: String)

@Serializable
object ReadingSettingsScreen

@Serializable
object RandomTopicsScreen

/** Opens the Kiwix/ZIM reader, either at a known entry or at a random article. */
@Serializable
data class KiwixReader(
    val random: Boolean = false,
    /** Empty means "start at the archive's main page". */
    val path: String = ""
)
