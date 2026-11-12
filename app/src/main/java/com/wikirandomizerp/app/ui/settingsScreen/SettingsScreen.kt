package com.wikirandomizerp.app.ui.settingsScreen

import kotlin.math.roundToInt
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.app.LocaleManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.BuildConfig
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.R.string
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.DeveloperMode
import com.wikirandomizerp.app.data.LibraryStats
import com.wikirandomizerp.app.data.UpdateResult
import com.wikirandomizerp.app.data.UpdateState
import com.wikirandomizerp.app.data.langCodeToName
import com.wikirandomizerp.app.data.sync.SyncAuthState
import com.wikirandomizerp.app.data.toColor
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeAction
import com.wikirandomizerp.app.ui.aboutScreen.UpdateDialog
import com.wikirandomizerp.app.ui.homeScreen.viewModel.HomeSubscreen
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsAction
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.SettingsViewModel
import com.wikirandomizerp.app.ui.theme.CustomColors.listItemColors
import com.wikirandomizerp.app.ui.theme.CustomColors.topBarColors
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.bottomListItemShape
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.cardShape
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.middleListItemShape
import com.wikirandomizerp.app.ui.theme.WRShapeDefaults.topListItemShape
import com.wikirandomizerp.app.ui.theme.WikiRandomizerPTheme
import com.wikirandomizerp.app.ui.theme.readingFontLabels
import java.io.File
import kotlin.math.round

/** One row in the Settings search results. [checked] makes it a switch, [value] shows a value. */
private data class SettingsSearchEntry(
    val title: String,
    val description: String,
    val keywords: String = "",
    val icon: Int,
    val value: String? = null,
    val checked: Boolean? = null,
    val action: () -> Unit
)

@Composable
fun SettingsScreenRoot(
    preferencesState: PreferencesState,
    lastBackStackEntry: HomeSubscreen,
    recentLangs: List<String>,
    languageSearchStr: String,
    languageSearchQuery: String,
    onHomeAction: (HomeAction) -> Unit,
    /** Opens the Reading & topics submenu that contains the random-topic picker. */
    onNavigateToReadingSettings: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    onOpenDonation: () -> Unit = {},
    onOpenDebug: () -> Unit = {},
    onGetLibraryStats: suspend () -> LibraryStats = {
        LibraryStats(totalArticles = 0, unseen = 0, topics = emptyList())
    },
    onSetTopicEnabled: suspend (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToVocabulary: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onBackup: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val context = LocalContext.current

    // Deliberately not wrapped in remember: the labels come from the current app locale, and a
    // remembered map kept the old language after the user switched the app language.
    val themeMap: Map<String, Pair<Int, String>> = mapOf(
        "auto" to Pair(
            R.drawable.brightness_auto,
            context.getString(string.themeSystemDefault)
        ),
        "light" to Pair(R.drawable.light_mode, context.getString(string.themeLight)),
        "dark" to Pair(R.drawable.dark_mode, context.getString(string.themeDark))
    )
    val reverseThemeMap: Map<String, String> = mapOf(
        context.getString(string.themeSystemDefault) to "auto",
        context.getString(string.themeLight) to "light",
        context.getString(string.themeDark) to "dark"
    )
    val fontOptions = readingFontLabels()
    val fontStyleMap: Map<String, String> = fontOptions.toMap()
    val reverseFontStyleMap: Map<String, String> =
        fontOptions.associate { (key, label) -> label to key }
    val fontStyles = fontOptions.map { it.second }

    SettingsScreen(
        preferencesState = preferencesState,
        lastBackStackEntry = lastBackStackEntry,
        recentLangs = recentLangs,
        languageSearchStr = languageSearchStr,
        languageSearchQuery = languageSearchQuery,
        themeMap = themeMap,
        reverseThemeMap = reverseThemeMap,
        fontStyles = fontStyles,
        fontStyleMap = fontStyleMap,
        reverseFontStyleMap = reverseFontStyleMap,
        onAction = viewModel::onAction,
        onHomeAction = onHomeAction,
        onNavigateToReadingSettings = onNavigateToReadingSettings,
        onReplayTutorial = onReplayTutorial,
        onOpenDonation = onOpenDonation,
        onOpenDebug = onOpenDebug,
        onGetLibraryStats = onGetLibraryStats,
        onSetTopicEnabled = onSetTopicEnabled,
        onNavigateToVocabulary = onNavigateToVocabulary,
        onNavigateToStatistics = onNavigateToStatistics,
        onBackup = onBackup,
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    preferencesState: PreferencesState,
    lastBackStackEntry: HomeSubscreen,
    recentLangs: List<String>,
    languageSearchStr: String,
    languageSearchQuery: String,
    themeMap: Map<String, Pair<Int, String>>,
    reverseThemeMap: Map<String, String>,
    fontStyles: List<String>,
    fontStyleMap: Map<String, String>,
    reverseFontStyleMap: Map<String, String>,
    onAction: (SettingsAction) -> Unit,
    onHomeAction: (HomeAction) -> Unit,
    onNavigateToReadingSettings: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    onOpenDonation: () -> Unit = {},
    onOpenDebug: () -> Unit = {},
    onGetLibraryStats: suspend () -> LibraryStats = {
        LibraryStats(totalArticles = 0, unseen = 0, topics = emptyList())
    },
    onSetTopicEnabled: suspend (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToVocabulary: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onBackup: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val appInfoIntent = remember {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    val currentLocales =
        if (Build.VERSION.SDK_INT >= 33) {
            context
                .getSystemService(LocaleManager::class.java)
                .applicationLocales
        } else null
    val currentLocalesSize = currentLocales?.size() ?: 0

    val theme = preferencesState.theme
    val fontStyle = preferencesState.fontStyle
    val color = preferencesState.colorScheme.toColor()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackBarHostState = remember { SnackbarHostState() }
    var developerEnabled by remember { mutableStateOf(DeveloperMode.isEnabled(context)) }
    val versionTapTimes = remember { mutableListOf<Long>() }
    val availableUpdate by UpdateState.available.collectAsState()
    val checkingUpdate by UpdateState.checking.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    val app = context.applicationContext as? WikiRandomizerPApplication
    val localSyncStore = app?.container?.localSyncStore
    val cloudConfigured = localSyncStore?.cloudConfigured == true
    val authState = if (localSyncStore != null) {
        localSyncStore.authState.collectAsStateWithLifecycle(
            initialValue = localSyncStore.currentAuth
        ).value
    } else {
        SyncAuthState.NotConfigured
    }
    val cloudAccess = localSyncStore?.hasCloudAccess() == true
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(localSyncStore?.autoSync ?: false) }
    var lastSyncAt by remember { mutableStateOf(localSyncStore?.lastSyncAt) }
    var syncing by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val fontImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                File(dir, "custom.ttf").outputStream().use { output -> input.copyTo(output) }
            } ?: error("could not open the selected font")
        }.isSuccess
        if (imported) {
            onAction(SettingsAction.SaveFontStyle("custom"))
            coroutineScope.launch { snackBarHostState.showSnackbar(context.getString(string.fontImportedToast)) }
        } else {
            coroutineScope.launch { snackBarHostState.showSnackbar(context.getString(string.fontImportFailed)) }
        }
    }


    val (showThemeDialog, setShowThemeDialog) = remember { mutableStateOf(false) }
    val (showDebugLog, setShowDebugLog) = remember { mutableStateOf(false) }
    val (showBugReport, setShowBugReport) = remember { mutableStateOf(false) }
    val (showResetSettingsDialog, setShowResetSettingsDialog) = remember { mutableStateOf(false) }
    val (showColorSchemeDialog, setShowColorSchemeDialog) = remember { mutableStateOf(false) }
    val (showFontDialog, setShowFontDialog) = remember { mutableStateOf(false) }
    val (showLanguageSheet, setShowLanguageSheet) = remember { mutableStateOf(false) }
    val (showAppLocaleSheet, setShowAppLocaleSheet) = remember { mutableStateOf(false) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var settingsCategory by rememberSaveable { mutableStateOf(SettingsCategory.ROOT) }
    val settingsListState = rememberLazyListState()
    LaunchedEffect(settingsCategory) {
        settingsListState.scrollToItem(0)
    }
    BackHandler(enabled = settingsCategory != SettingsCategory.ROOT) {
        settingsCategory = SettingsCategory.ROOT
    }

    var libraryStats by remember { mutableStateOf<LibraryStats?>(null) }
    LaunchedEffect(preferencesState.lang) {
        libraryStats = onGetLibraryStats()
    }
    // Settings stays composed while the user builds/removes a topic on the Random topics screen.
    // Refresh on return so the switch list never shows a stale library.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        coroutineScope.launch { libraryStats = onGetLibraryStats() }
        developerEnabled = DeveloperMode.isEnabled(context)
    }

    var animateFontSize by remember { mutableStateOf(true) }
    var fontSizeFloat by remember(preferencesState.fontSize) { mutableFloatStateOf(preferencesState.fontSize.toFloat()) }
    val fontSizeAnimated by animateFloatAsState(
        fontSizeFloat,
        animationSpec = if (animateFontSize) motionScheme.defaultSpatialSpec()
        else tween(durationMillis = 0)
    )

    val disabledAlpha = 0.5f

    val disabledListColors =
        ListItemDefaults.colors(
            containerColor = colorScheme.surfaceContainerLow,
            headlineColor = listItemColors.headlineColor.copy(disabledAlpha),
            leadingIconColor = listItemColors.leadingIconColor.copy(disabledAlpha),
            overlineColor = listItemColors.overlineColor.copy(disabledAlpha),
            supportingColor = listItemColors.supportingTextColor.copy(disabledAlpha),
            trailingIconColor = listItemColors.trailingIconColor.copy(disabledAlpha),
        )

    // Compose Material 3 uses the 3P spec, which hasn't yet been updated with the new
    // switch tokens, which are already used across Google products.
    // Therefore we must override certain colors to let the Switch remain accessible
    // with the new 2025 color spec.
    // See https://github.com/nsh07/WikiReader/issues/272 for more info
    //
    // Once Compose Material 3 updates Switch tokens, this should be removed.
    val switchColors = SwitchDefaults.colors(
        checkedIconColor = colorScheme.primary,
    )

    val switchItems = remember(preferencesState) {
        listOf(
            SettingsSwitchItem(
                preferencesState.blackTheme,
                R.drawable.contrast,
                string.settingBlackTheme,
                string.settingBlackThemeDesc,
                SettingsAction::SaveBlackTheme
            ),
            SettingsSwitchItem(
                preferencesState.dataSaver,
                R.drawable.data_saver_on,
                string.settingDataSaver,
                string.settingDataSaverDesc,
                SettingsAction::SaveDataSaver
            ),
            SettingsSwitchItem(
                preferencesState.feedEnabled,
                R.drawable.feed,
                string.settingFeed,
                string.settingFeedDesc,
                SettingsAction::SaveFeedEnabled,
                enabled = !preferencesState.dataSaver
            ),
            SettingsSwitchItem(
                preferencesState.expandedSections,
                R.drawable.expand_all,
                string.settingExpandSections,
                string.settingExpandSectionsDesc,
                SettingsAction::SaveExpandedSections
            ),
            SettingsSwitchItem(
                preferencesState.imageBackground,
                R.drawable.texture,
                string.settingImageBackground,
                string.settingImageBackgroundDesc,
                SettingsAction::SaveImageBackground
            ),
            SettingsSwitchItem(
                preferencesState.immersiveMode,
                R.drawable.open_in_full,
                string.settingImmersiveMode,
                string.settingImmersiveModeDesc,
                SettingsAction::SaveImmersiveMode
            ),
            SettingsSwitchItem(
                preferencesState.renderMath,
                R.drawable.function,
                string.settingRenderMath,
                string.settingRenderMathDesc,
                SettingsAction::SaveRenderMath
            ),
            SettingsSwitchItem(
                preferencesState.saveOffline,
                R.drawable.download,
                string.settingSaveOffline,
                string.settingSaveOfflineDesc,
                SettingsAction::SaveSaveOffline
            ),
            SettingsSwitchItem(
                preferencesState.saveFolders,
                R.drawable.folder,
                string.settingSaveFolders,
                string.settingSaveFoldersDesc,
                SettingsAction::SaveSaveFolders
            ),
            SettingsSwitchItem(
                preferencesState.saveNotes,
                R.drawable.note,
                string.settingSaveNotes,
                string.settingSaveNotesDesc,
                SettingsAction::SaveSaveNotes
            ),
            SettingsSwitchItem(
                preferencesState.browsingHistory,
                R.drawable.manage_history,
                string.history,
                string.historyDesc,
                SettingsAction::SaveHistory
            ),
            SettingsSwitchItem(
                preferencesState.searchHistory,
                R.drawable.search_history,
                string.settingSearchHistory,
                string.settingSearchHistoryDesc,
                SettingsAction::SaveSearchHistory
            ),
            SettingsSwitchItem(
                preferencesState.pullForRandom,
                R.drawable.keyboard_arrow_down,
                string.settingPullForRandom,
                string.settingPullForRandomDesc,
                SettingsAction::SavePullForRandom
            ),
            SettingsSwitchItem(
                preferencesState.preloadArticles,
                R.drawable.shuffle,
                string.settingPreloadArticles,
                string.settingPreloadArticlesDesc,
                SettingsAction::SavePreloadArticles,
                enabled = !preferencesState.dataSaver
            ),
            SettingsSwitchItem(
                preferencesState.personalizationEnabled,
                R.drawable.thumb_up,
                string.settingPersonalization,
                string.settingPersonalizationDesc,
                SettingsAction::SavePersonalization
            ),
            SettingsSwitchItem(
                preferencesState.reduceMotion,
                R.drawable.update,
                string.settingReduceMotion,
                string.settingReduceMotionDesc,
                SettingsAction::SaveReduceMotion
            ),
            SettingsSwitchItem(
                preferencesState.hapticFeedback,
                R.drawable.open_in_full,
                string.settingHaptics,
                string.settingHapticsDesc,
                SettingsAction::SaveHapticFeedback
            ),
            SettingsSwitchItem(
                preferencesState.boldArticleText,
                R.drawable.format_size,
                string.settingBoldText,
                string.settingBoldTextDesc,
                SettingsAction::SaveBoldArticleText
            ),
            SettingsSwitchItem(
                preferencesState.inaturalistEnabled,
                R.drawable.leaf,
                string.settingINaturalist,
                string.settingINaturalistDesc,
                SettingsAction::SaveINaturalist
            ),
            SettingsSwitchItem(
                preferencesState.swipeGestures,
                R.drawable.menu_open,
                string.settingSwipeGestures,
                string.settingSwipeGesturesDesc,
                SettingsAction::SaveSwipeGestures
            ),
            SettingsSwitchItem(
                preferencesState.wikivoyageEnabled,
                R.drawable.translate,
                string.settingWikivoyage,
                string.settingWikivoyageDesc,
                SettingsAction::SaveWikivoyage
            ),
            SettingsSwitchItem(
                preferencesState.inaturalistGallery,
                R.drawable.leaf,
                string.settingINaturalistGallery,
                string.settingINaturalistGalleryDesc,
                SettingsAction::SaveINaturalistGallery
            ),
        )
    }

    val switchCategories = remember {
        listOf(
            SettingsCategory.BEHAVIOUR, // 0 data saver
            SettingsCategory.READING, // 1 feed
            SettingsCategory.READING, // 2 expand sections
            SettingsCategory.APPEARANCE, // 3 image background
            SettingsCategory.APPEARANCE, // 4 immersive mode
            SettingsCategory.READING, // 5 render math
            SettingsCategory.DATA, // 6 save offline
            SettingsCategory.DATA, // 7 save folders
            SettingsCategory.DATA, // 8 save notes
            SettingsCategory.DATA, // 9 browsing history
            SettingsCategory.DATA, // 10 search history
            SettingsCategory.READING, // 11 pull for random
            SettingsCategory.BEHAVIOUR, // 12 preload articles
            SettingsCategory.BEHAVIOUR, // 13 personalization
            SettingsCategory.APPEARANCE, // 14 reduce motion
            SettingsCategory.APPEARANCE, // 15 haptics
            SettingsCategory.APPEARANCE, // 16 bold text
            SettingsCategory.READING, // 17 iNaturalist action
            SettingsCategory.BEHAVIOUR, // 18 swipe gestures
            SettingsCategory.BEHAVIOUR, // 19 Wikivoyage
            SettingsCategory.READING // 20 iNaturalist gallery
        )
    }

    val visibleSwitchItems = switchItems.drop(1).filterIndexed { index, _ ->
        switchCategories.getOrNull(index) == settingsCategory
    }

    if (showThemeDialog)
        ThemeDialog(
            themeMap = themeMap,
            reverseThemeMap = reverseThemeMap,
            theme = theme,
            setShowThemeDialog = setShowThemeDialog,
            setTheme = { onAction(SettingsAction.SaveTheme(it)) }
        )
    if (showFontDialog)
        FontPickerDialog(
            fontStyleMap = fontStyleMap,
            selected = fontStyle,
            onSelect = {
                onAction(SettingsAction.SaveFontStyle(it))
                setShowFontDialog(false)
            },
            onImport = {
                setShowFontDialog(false)
                fontImportLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { setShowFontDialog(false) }
        )
    if (showColorSchemeDialog)
        ColorSchemePickerDialog(
            currentColor = color,
            onColorChange = { onAction(SettingsAction.SaveColorScheme(it.toString())) },
            setShowDialog = setShowColorSchemeDialog
        )
    if (showCloudSyncDialog && localSyncStore != null) {
        CloudSyncAccountDialog(
            store = localSyncStore,
            onDismiss = {
                showCloudSyncDialog = false
                autoSync = localSyncStore.autoSync
                lastSyncAt = localSyncStore.lastSyncAt
            }
        )
    }
    if (showDebugLog) DebugLogDialog(onDismiss = { setShowDebugLog(false) })
    if (showBugReport) BugReportDialog(
        onDismiss = { setShowBugReport(false) },
        onCopied = {
            setShowBugReport(false)
            coroutineScope.launch {
                snackBarHostState.showSnackbar(context.getString(R.string.reportBugCopied))
            }
        }
    )
    if (showResetSettingsDialog)
        ResetSettingsDialog(
            onResetSettings = { onAction(SettingsAction.ResetSettings) },
            setShowResetSettingsDialog = setShowResetSettingsDialog,
            showSnackbar = { coroutineScope.launch { snackBarHostState.showSnackbar(it) } }
        )
    availableUpdate?.let { updateInfo ->
        if (showUpdateDialog) {
            UpdateDialog(
                info = updateInfo,
                onDismiss = { showUpdateDialog = false },
                onOpenReleasePage = { uriHandler.openUri(updateInfo.releaseUrl) }
            )
        }
    }
    if (showLanguageSheet)
        LanguageBottomSheet(
            lang = preferencesState.lang,
            recentLangs = recentLangs,
            searchStr = languageSearchStr,
            searchQuery = languageSearchQuery,
            setShowSheet = setShowLanguageSheet,
            setLang = {
                // The Home view model observes the language preference and resets to a feed in
                // the new language. Reloading the current article here would ask the new wiki
                // for an article that may not exist (e.g. an English-only title), showing an
                // error page instead of the new-language feed.
                onAction(SettingsAction.SaveLang(it))
            },
            setSearchStr = { onHomeAction(HomeAction.UpdateLanguageSearchStr(it)) }
        )
    if (showAppLocaleSheet && currentLocales != null)
        AppLocaleBottomSheet(
            searchStr = languageSearchStr,
            currentLocales = currentLocales,
            setSearchStr = { onHomeAction(HomeAction.UpdateLanguageSearchStr(it)) },
            setShowSheet = setShowAppLocaleSheet
        )

    Scaffold(
        topBar = {
            SettingsTopBar(
                scrollBehavior = scrollBehavior,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { insets ->
        LazyColumn(
            state = settingsListState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = insets,
            modifier = Modifier
                .background(topBarColors.containerColor)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item(key = "settings-search") {
                OutlinedTextField(
                    value = settingsSearchQuery,
                    onValueChange = { settingsSearchQuery = it },
                    placeholder = { Text(stringResource(string.searchSettings)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.search), contentDescription = null)
                    },
                    trailingIcon = {
                        if (settingsSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { settingsSearchQuery = "" }) {
                                Icon(
                                    painterResource(R.drawable.clear),
                                    contentDescription = stringResource(string.clearSearchField)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }
            if (settingsSearchQuery.isBlank()) {
            if (settingsCategory == SettingsCategory.ROOT) {
                item(key = "settings-hub-header") { SettingsHubHeader() }
                items(
                    SettingsCategory.entries.filter { it != SettingsCategory.ROOT },
                    key = { it.name }
                ) { category ->
                    SettingsCategoryCard(category) { settingsCategory = category }
                    Spacer(Modifier.height(10.dp))
                }
                item { Spacer(Modifier.height(48.dp)) }
            } else {
            item(key = "settings-category-header") {
                SettingsCategoryHeader(settingsCategory) { settingsCategory = SettingsCategory.ROOT }
            }
            if (settingsCategory == SettingsCategory.READING) {
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.view_list),
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingsReadingTopics)) },
                    supportingContent = {
                        // Keep this row's text stable while the asynchronous library stats load.
                        // It used to switch from the description to a summary a moment after the
                        // screen appeared, which looked like the setting had changed by itself.
                        Text(stringResource(string.settingsReadingTopicsDesc))
                    },
                    colors = listItemColors,
                    items = 1,
                    index = 0
                ) { onNavigateToReadingSettings() }
            }
            }
            if (settingsCategory == SettingsCategory.APPEARANCE) {
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.palette),
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingColorScheme)) },
                    supportingContent = {
                        if (color == Color.White) Text(stringResource(string.colorSchemeDynamic))
                        else Text(stringResource(string.colorSchemeColor))
                    },
                    colors = listItemColors,
                    items = 3,
                    index = 0
                ) { setShowColorSchemeDialog(true) }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(themeMap[theme]!!.first),
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingTheme)) },
                    supportingContent = { Text(themeMap[theme]!!.second) },
                    colors = listItemColors,
                    items = 3,
                    index = 1
                ) { setShowThemeDialog(true) }
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(switchItems[0].icon), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(switchItems[0].label)) },
                    supportingContent = { Text(stringResource(switchItems[0].description)) },
                    trailingContent = {
                        Switch(
                            checked = switchItems[0].checked,
                            onCheckedChange = { onAction(switchItems[0].actionConstructor(it)) },
                            thumbContent = {
                                if (switchItems[0].checked) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.clear),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            },
                            enabled = switchItems[0].enabled,
                            colors = switchColors
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(bottomListItemShape)
                )
            }
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (settingsCategory == SettingsCategory.BEHAVIOUR) {
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.translate),
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingWikipediaLanguage)) },
                    supportingContent = { Text(langCodeToName(preferencesState.lang)) },
                    colors = listItemColors,
                    items = 4,
                    index = 0
                ) { setShowLanguageSheet(true) }
            }
            if (currentLocales != null)
                item {
                    ClickableListItem(
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.language),
                                contentDescription = null
                            )
                        },
                        headlineContent = { Text(stringResource(string.settingAppLanguage)) },
                        supportingContent = {
                            Text(
                                if (currentLocalesSize > 0) currentLocales.get(0).displayName
                                else stringResource(string.themeSystemDefault)
                            )
                        },
                        colors = listItemColors,
                        items = 4,
                        index = 1
                    ) { setShowAppLocaleSheet(true) }
                }
            }
            if (settingsCategory == SettingsCategory.APPEARANCE) {
            item {
                ListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.serif),
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingFontStyle)) },
                    supportingContent = {
                        Text(fontStyleMap[fontStyle] ?: fontStyle)
                    },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.arrow_forward),
                            contentDescription = null
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(topListItemShape)
                        .clickable { setShowFontDialog(true) }
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.format_size),
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingFontSize)) },
                    supportingContent = {
                        Column {
                            val intSize = round(fontSizeFloat).toInt()
                            Text(
                                text = stringResource(string.settingFontSizePreview),
                                style = typography.bodyLarge,
                                fontSize = fontSizeAnimated.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                IconButton(
                                    enabled = intSize > 10,
                                    onClick = {
                                        val next = (intSize - 1).coerceAtLeast(10)
                                        animateFontSize = false
                                        fontSizeFloat = next.toFloat()
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onAction(SettingsAction.SaveFontSize(next))
                                    }
                                ) {
                                    Icon(
                                        painterResource(R.drawable.remove),
                                        contentDescription = stringResource(string.settingFontSizeDecrease)
                                    )
                                }
                                Text(
                                    text = "$intSize sp",
                                    style = typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    enabled = intSize < 22,
                                    onClick = {
                                        val next = (intSize + 1).coerceAtMost(22)
                                        animateFontSize = false
                                        fontSizeFloat = next.toFloat()
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onAction(SettingsAction.SaveFontSize(next))
                                    }
                                ) {
                                    Icon(
                                        painterResource(R.drawable.add),
                                        contentDescription = stringResource(string.settingFontSizeIncrease)
                                    )
                                }
                            }
                        }
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(bottomListItemShape)
                )
            }
            if (developerEnabled) {
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.format_size), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingLineSpacing)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(string.settingLineSpacingDesc))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    IconButton(
                                        enabled = preferencesState.lineSpacing > 80,
                                        onClick = {
                                            onAction(
                                                SettingsAction.SaveLineSpacing(
                                                    (preferencesState.lineSpacing - 10).coerceAtLeast(80)
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.remove),
                                            contentDescription = null
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            string.settingLineSpacingValue,
                                            preferencesState.lineSpacing
                                        ),
                                        style = typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        enabled = preferencesState.lineSpacing < 200,
                                        onClick = {
                                            onAction(
                                                SettingsAction.SaveLineSpacing(
                                                    (preferencesState.lineSpacing + 10).coerceAtMost(200)
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.add),
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        },
                        colors = listItemColors
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.format_size), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingReadingMargin)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(string.settingReadingMarginDesc))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    IconButton(
                                        enabled = preferencesState.readingMargin > 4,
                                        onClick = {
                                            onAction(
                                                SettingsAction.SaveReadingMargin(
                                                    (preferencesState.readingMargin - 4).coerceAtLeast(4)
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.remove),
                                            contentDescription = null
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            string.settingReadingMarginValue,
                                            preferencesState.readingMargin
                                        ),
                                        style = typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        enabled = preferencesState.readingMargin < 48,
                                        onClick = {
                                            onAction(
                                                SettingsAction.SaveReadingMargin(
                                                    (preferencesState.readingMargin + 4).coerceAtMost(48)
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.add),
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        },
                        colors = listItemColors
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.format_size), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingJustifyText)) },
                        supportingContent = { Text(stringResource(string.settingJustifyTextDesc)) },
                        trailingContent = {
                            Switch(
                                checked = preferencesState.justifyText,
                                onCheckedChange = {
                                    onAction(SettingsAction.SaveJustifyText(it))
                                },
                                colors = switchColors
                            )
                        },
                        colors = listItemColors
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.format_size), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingSepiaTheme)) },
                        supportingContent = { Text(stringResource(string.settingSepiaThemeDesc)) },
                        trailingContent = {
                            Switch(
                                checked = preferencesState.sepiaTheme,
                                onCheckedChange = {
                                    onAction(SettingsAction.SaveSepiaTheme(it))
                                },
                                colors = switchColors
                            )
                        },
                        colors = listItemColors
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.format_size), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingLinkPreviews)) },
                        supportingContent = { Text(stringResource(string.settingLinkPreviewsDesc)) },
                        trailingContent = {
                            Switch(
                                checked = preferencesState.linkPreviews,
                                onCheckedChange = {
                                    onAction(SettingsAction.SaveLinkPreviews(it))
                                },
                                colors = switchColors
                            )
                        },
                        colors = listItemColors
                    )
                }
            }
            }
            if (settingsCategory == SettingsCategory.DATA) {
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.view_list), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.vocabulary)) },
                    supportingContent = { Text(stringResource(string.vocabularyEmpty)) },
                    trailingContent = {
                        Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(topListItemShape)
                        .clickable { onNavigateToVocabulary() }
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.history), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.statistics)) },
                    supportingContent = { Text(stringResource(string.statsSettingsDesc)) },
                    trailingContent = {
                        Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(middleListItemShape)
                        .clickable { onNavigateToStatistics() }
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.download_done), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.backup)) },
                    supportingContent = { Text(stringResource(string.backupDesc)) },
                    trailingContent = {
                        Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(bottomListItemShape)
                        .clickable { onBackup() }
                )
            }
            // Firebase sync is a developer preview. It stays completely out of the normal UI.
            if (developerEnabled) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                val supporting = when {
                    !cloudConfigured -> context.getString(string.settingCloudNotConfiguredDesc)
                    authState is SyncAuthState.SignedIn ->
                        context.getString(
                            string.settingCloudSignedIn,
                            (authState as SyncAuthState.SignedIn).email
                                ?: (authState as SyncAuthState.SignedIn).uid
                        )
                    else -> context.getString(string.settingCloudSignedOut)
                }
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.person), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingCloudAccount)) },
                    supportingContent = { Text(supporting) },
                    trailingContent = {
                        Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
                    },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(topListItemShape)
                        .clickable { showCloudSyncDialog = true }
                )
            }
            item {
                val syncReady = cloudConfigured && cloudAccess && authState is SyncAuthState.SignedIn
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.cloud), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingSync)) },
                    supportingContent = { Text(stringResource(string.settingSyncDesc)) },
                    trailingContent = {
                        Switch(
                            checked = autoSync,
                            onCheckedChange = {
                                autoSync = it
                                localSyncStore?.setAutoSync(it)
                            },
                            enabled = syncReady,
                            colors = switchColors
                        )
                    },
                    colors = if (syncReady) listItemColors else disabledListColors,
                    modifier = Modifier.clip(middleListItemShape)
                )
            }
            item {
                val lastSyncLabel = lastSyncAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.cloud), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingSyncNow)) },
                    supportingContent = {
                        Text(
                            when {
                                !cloudConfigured -> stringResource(string.settingCloudNotConfiguredDesc)
                                !cloudAccess -> stringResource(string.settingSyncSupporterOnly)
                                authState !is SyncAuthState.SignedIn ->
                                    stringResource(string.settingCloudNeedSignIn)
                                syncing -> stringResource(string.settingSyncRunning)
                                lastSyncLabel != null ->
                                    stringResource(string.settingSyncLast, lastSyncLabel)
                                else -> stringResource(string.settingSyncLocalOnly)
                            }
                        )
                    },
                    trailingContent = if (syncing) {
                        {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null,
                    colors = if (cloudConfigured) listItemColors else disabledListColors,
                    modifier = Modifier
                        .clip(bottomListItemShape)
                        .clickable(enabled = !syncing && localSyncStore != null) {
                            val store = localSyncStore ?: return@clickable
                            when {
                                !cloudConfigured -> coroutineScope.launch {
                                    snackBarHostState.showSnackbar(
                                        context.getString(string.settingCloudNotConfiguredDesc)
                                    )
                                }
                                !cloudAccess -> onOpenDonation()
                                authState !is SyncAuthState.SignedIn -> showCloudSyncDialog = true
                                else -> coroutineScope.launch {
                                    syncing = true
                                    val result = runCatching { store.syncNow() }
                                    syncing = false
                                    result.onSuccess { synced ->
                                        lastSyncAt = synced.lastSyncAt
                                        snackBarHostState.showSnackbar(
                                            context.getString(
                                                string.settingSyncComplete,
                                                synced.recordCount
                                            )
                                        )
                                    }.onFailure { failure ->
                                        snackBarHostState.showSnackbar(
                                            context.getString(
                                                string.settingSyncCloudError,
                                                failure.message
                                                    ?: context.getString(string.settingSyncFailed)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                )
            }
            }
            }
            if (settingsCategory == SettingsCategory.READING) {
            item {
                // Renderer: fast native wikitext parsing, or Wikipedia's fully rendered HTML.
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.code), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingArticleFormat)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(string.settingArticleFormatDesc))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    ButtonGroupDefaults.ConnectedSpaceBetween
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                val html = preferencesState.renderMode == "html"
                                ToggleButton(
                                    checked = !html,
                                    onCheckedChange = {
                                        onAction(SettingsAction.SaveRenderMode("wikitext"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                                ) {
                                    Text(stringResource(string.articleFormatWikitext))
                                }
                                ToggleButton(
                                    checked = html,
                                    onCheckedChange = {
                                        onAction(SettingsAction.SaveRenderMode("html"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                                ) {
                                    Text(stringResource(string.articleFormatHtml))
                                }
                            }
                        }
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(cardShape)
                )
            }
            }
            if (settingsCategory == SettingsCategory.BEHAVIOUR) {
            item {
                // Physical side of the navigation drawer / edge-swipe gesture.
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.menu), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingMenuSide)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(string.settingMenuSideDesc))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    ButtonGroupDefaults.ConnectedSpaceBetween
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                val right = preferencesState.menuSide == "right"
                                ToggleButton(
                                    checked = !right,
                                    onCheckedChange = {
                                        onAction(SettingsAction.SaveMenuSide("left"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                                ) {
                                    Text(stringResource(string.menuSideLeft))
                                }
                                ToggleButton(
                                    checked = right,
                                    onCheckedChange = {
                                        onAction(SettingsAction.SaveMenuSide("right"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                                ) {
                                    Text(stringResource(string.menuSideRight))
                                }
                            }
                        }
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(cardShape)
                )
            }
            if (developerEnabled) {
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.person), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingTalkPages)) },
                        supportingContent = { Text(stringResource(string.settingTalkPagesDesc)) },
                        trailingContent = {
                            Switch(
                                checked = preferencesState.talkPages,
                                onCheckedChange = {
                                    onAction(SettingsAction.SaveTalkPages(it))
                                },
                                colors = switchColors
                            )
                        },
                        colors = listItemColors
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.leaf), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingDailyNotification)) },
                        supportingContent = {
                            Text(stringResource(string.settingDailyNotificationDesc))
                        },
                        trailingContent = {
                            Switch(
                                checked = preferencesState.dailyNotification,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                    onAction(SettingsAction.SaveDailyNotification(enabled))
                                },
                                colors = switchColors
                            )
                        },
                        colors = listItemColors
                    )
                }
                item {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.history), contentDescription = null)
                        },
                        headlineContent = { Text(stringResource(string.settingHomeWidget)) },
                        supportingContent = { Text(stringResource(string.settingHomeWidgetDesc)) },
                        trailingContent = {
                            Switch(
                                checked = preferencesState.homeWidget,
                                onCheckedChange = {
                                    onAction(SettingsAction.SaveHomeWidget(it))
                                },
                                colors = switchColors
                            )
                        },
                        colors = listItemColors
                    )
                }
            }
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (settingsCategory == SettingsCategory.READING) {
            item {
                // How closely articles must match the topic (hides low-relevance articles).
                var strictPos by remember(preferencesState.topicStrictness) {
                    mutableFloatStateOf(preferencesState.topicStrictness.toFloat())
                }
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.check), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingStrictness)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(string.settingStrictnessDesc))
                            Text(
                                stringResource(
                                    when (strictPos.roundToInt()) {
                                        0 -> string.strictnessBroad
                                        2 -> string.strictnessStrict
                                        3 -> string.strictnessVeryStrict
                                        else -> string.strictnessBalanced
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = strictPos,
                                onValueChange = { strictPos = it.roundToInt().toFloat() },
                                onValueChangeFinished = {
                                    onAction(SettingsAction.SaveTopicStrictness(strictPos.roundToInt()))
                                },
                                valueRange = 0f..3f,
                                steps = 2
                            )
                        }
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(middleListItemShape)
                )
            }
            item {
                // Popularity preference (replaces the old "popular articles only" switch).
                var sliderPos by remember(preferencesState.popularityBias) {
                    mutableFloatStateOf(preferencesState.popularityBias.toFloat())
                }
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.upward), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingPopularity)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(string.settingPopularityDesc))
                            Text(
                                stringResource(
                                    when (sliderPos.roundToInt()) {
                                        -2 -> string.popularityObscureOnly
                                        -1 -> string.popularityPreferObscure
                                        1 -> string.popularityPreferPopular
                                        2 -> string.popularityPopularOnly
                                        else -> string.popularityNoPreference
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = sliderPos,
                                onValueChange = { sliderPos = it.roundToInt().toFloat() },
                                onValueChangeFinished = {
                                    onAction(SettingsAction.SavePopularityBias(sliderPos.roundToInt()))
                                },
                                valueRange = -2f..2f,
                                steps = 3
                            )
                        }
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(middleListItemShape)
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.expand_all), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingAvoidStubs)) },
                    supportingContent = { Text(stringResource(string.settingAvoidStubsDesc)) },
                    trailingContent = {
                        Switch(
                            checked = preferencesState.avoidStubs,
                            onCheckedChange = { onAction(SettingsAction.SaveAvoidStubs(it)) },
                            thumbContent = {
                                if (preferencesState.avoidStubs) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.clear),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            },
                            colors = switchColors
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(middleListItemShape)
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.check), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingPreferQuality)) },
                    supportingContent = { Text(stringResource(string.settingPreferQualityDesc)) },
                    trailingContent = {
                        Switch(
                            checked = preferencesState.preferQuality,
                            onCheckedChange = { onAction(SettingsAction.SavePreferQuality(it)) },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        if (preferencesState.preferQuality) R.drawable.check else R.drawable.clear
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            },
                            colors = switchColors
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(middleListItemShape)
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.image), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingImagesOnly)) },
                    supportingContent = { Text(stringResource(string.settingImagesOnlyDesc)) },
                    trailingContent = {
                        Switch(
                            checked = preferencesState.onlyArticlesWithImages,
                            onCheckedChange = { onAction(SettingsAction.SaveOnlyArticlesWithImages(it)) },
                            thumbContent = {
                                if (preferencesState.onlyArticlesWithImages) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.clear),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            },
                            colors = switchColors
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(bottomListItemShape)
                )
            }
            }
            item { Spacer(Modifier.height(12.dp)) }
            itemsIndexed(visibleSwitchItems) { index, item ->
                ListItem(
                    leadingContent = {
                        Icon(painterResource(item.icon), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(item.label)) },
                    supportingContent = { Text(stringResource(item.description)) },
                    trailingContent = {
                        Switch(
                            checked = item.checked && item.enabled,
                            onCheckedChange = { onAction(item.actionConstructor(it)) },
                            thumbContent = {
                                if (item.checked && item.enabled) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.clear),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            },
                            enabled = item.enabled,
                            colors = switchColors
                        )
                    },
                    colors =
                        if (item.enabled)
                            listItemColors
                        else
                            disabledListColors,
                    modifier = Modifier
                        .clip(
                            when {
                                visibleSwitchItems.size == 1 -> cardShape
                                index == 0 -> topListItemShape
                                index == visibleSwitchItems.lastIndex -> bottomListItemShape
                                else -> middleListItemShape
                            }
                        )
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (settingsCategory == SettingsCategory.ABOUT) {
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.outline_info),
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.settingReplayTutorial)) },
                    supportingContent = { Text(stringResource(string.settingReplayTutorialDesc)) },
                    colors = listItemColors,
                    items = 1,
                    index = 0
                ) { onReplayTutorial() }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.outline_info), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.version)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    colors = listItemColors,
                    modifier = Modifier
                        .clip(topListItemShape)
                        .clickable {
                            val now = System.currentTimeMillis()
                            versionTapTimes.add(now)
                            versionTapTimes.removeAll { now - it > 10_000L }
                            if (versionTapTimes.size >= 10) {
                                versionTapTimes.clear()
                                DeveloperMode.setEnabled(context, true)
                                developerEnabled = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    snackBarHostState.showSnackbar(
                                        context.getString(string.debugModeEnabled)
                                    )
                                }
                            }
                        }
                )
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.update), contentDescription = null)
                    },
                    headlineContent = {
                        Text(
                            availableUpdate?.let {
                                stringResource(string.updateAvailable, it.version)
                            } ?: stringResource(string.checkForUpdates)
                        )
                    },
                    supportingContent = {
                        Text(
                            when {
                                checkingUpdate -> stringResource(string.checkingForUpdates)
                                availableUpdate != null -> stringResource(string.updateAvailableDesc)
                                else -> stringResource(string.checkForUpdatesDesc)
                            }
                        )
                    },
                    trailingContent = if (checkingUpdate) {
                        {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null,
                    colors = listItemColors,
                    items = 6,
                    index = 1
                ) {
                    val info = availableUpdate
                    if (info != null) {
                        showUpdateDialog = true
                    } else if (!checkingUpdate) {
                        coroutineScope.launch {
                            if (app == null) {
                                snackBarHostState.showSnackbar(context.getString(string.updateFailed))
                                return@launch
                            }
                            UpdateState.checking.value = true
                            when (
                                val result = app.container.updateChecker
                                    .checkForUpdate(BuildConfig.VERSION_NAME)
                            ) {
                                is UpdateResult.Available -> {
                                    UpdateState.available.value = result.info
                                    showUpdateDialog = true
                                }

                                is UpdateResult.UpToDate -> snackBarHostState.showSnackbar(
                                    context.getString(string.upToDate)
                                )

                                is UpdateResult.Error -> snackBarHostState.showSnackbar(
                                    context.getString(string.updateCheckFailed, result.message)
                                )
                            }
                            UpdateState.checking.value = false
                        }
                    }
                }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.code), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.sourceCode)) },
                    supportingContent = { Text("github.com/PatrickJamesDev/WikiRandomizer-P") },
                    colors = listItemColors,
                    items = 6,
                    index = 2
                ) { uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P") }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.gavel), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.license)) },
                    supportingContent = { Text("GPL v3.0") },
                    colors = listItemColors,
                    items = 6,
                    index = 3
                ) { uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P/blob/main/LICENSE") }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.update), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.releases)) },
                    supportingContent = { Text(stringResource(string.releasesDesc)) },
                    colors = listItemColors,
                    items = 6,
                    index = 4
                ) { uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P/releases") }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.translate), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.translate)) },
                    supportingContent = { Text(stringResource(string.translateDesc)) },
                    colors = listItemColors,
                    items = 6,
                    index = 5
                ) { uriHandler.openUri("https://hosted.weblate.org/engage/wikireader/") }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.github), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.authorName)) },
                    supportingContent = { Text(stringResource(string.authorGithubDesc)) },
                    colors = listItemColors,
                    items = 2,
                    index = 0
                ) { uriHandler.openUri("https://github.com/PatrickJamesDev") }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.heart),
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    },
                    headlineContent = { Text(stringResource(string.authorSupport)) },
                    supportingContent = { Text(stringResource(string.authorSupportDesc)) },
                    colors = listItemColors,
                    items = 2,
                    index = 1
                ) { onOpenDonation() }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.wikipedia_s_w), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.wikipedia)) },
                    supportingContent = { Text(stringResource(string.wikipediaWebsiteDesc)) },
                    colors = listItemColors,
                    items = 2,
                    index = 0
                ) { uriHandler.openUri("https://wikipedia.org") }
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.wikimedia_logo_black), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.supportWikipedia)) },
                    supportingContent = { Text(stringResource(string.donateToWikipedia)) },
                    colors = listItemColors,
                    items = 2,
                    index = 1
                ) { uriHandler.openUri("https://wikimediafoundation.org/support/") }
            }
            if (developerEnabled) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.heart), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingDonationPrompts)) },
                    supportingContent = {
                        Text(stringResource(string.settingDonationPromptsDesc))
                    },
                    trailingContent = {
                        Switch(
                            checked = preferencesState.donationPrompts,
                            onCheckedChange = {
                                onAction(SettingsAction.SaveDonationPrompts(it))
                            },
                            colors = switchColors
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(cardShape)
                )
            }
            item {
                ClickableListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.code), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.developerOptions)) },
                    supportingContent = { Text(stringResource(string.developerOptionsDesc)) },
                    colors = listItemColors,
                    items = 1,
                    index = 0
                ) { onOpenDebug() }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.search_history), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingDebugLogging)) },
                    supportingContent = { Text(stringResource(string.settingDebugLoggingDesc)) },
                    trailingContent = {
                        Switch(
                            checked = preferencesState.debugLogging,
                            onCheckedChange = { onAction(SettingsAction.SaveDebugLogging(it)) },
                            colors = switchColors
                        )
                    },
                    colors = listItemColors,
                    modifier = Modifier.clip(topListItemShape)
                )
            }
            item {
                ListItem(
                    leadingContent = {
                        Icon(painterResource(R.drawable.expand_all), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(string.settingViewDebugLog)) },
                    modifier = Modifier
                        .clip(bottomListItemShape)
                        .clickable { setShowDebugLog(true) },
                    colors = listItemColors
                )
            }
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (preferencesState.supporterMode) {
                item {
                    ClickableListItem(
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.note),
                                tint = colorScheme.primary,
                                contentDescription = null
                            )
                        },
                        headlineContent = { Text(stringResource(string.featureReports)) },
                        supportingContent = { Text(stringResource(string.featureReportsDesc)) },
                        colors = listItemColors,
                        items = 1,
                        index = 0
                    ) { setShowBugReport(true) }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            item {
                Text(
                    text = stringResource(string.aboutIndependence),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier.padding(vertical = 14.dp),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = listItemColors.containerColor)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Spacer(
                                Modifier
                                    .clip(MaterialShapes.Cookie12Sided.toShape())
                                    .background(colorScheme.secondaryContainer)
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                            Icon(
                                painterResource(R.drawable.filled_info),
                                tint = colorScheme.onSecondaryContainer,
                                contentDescription = stringResource(string.information),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            stringResource(string.setAsDefault),
                            style = typography.headlineSmall
                        )
                        Text(
                            text = stringResource(string.setAsDefaultDesc),
                            style = typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                shapes = ButtonDefaults.shapes(),
                                onClick = { context.startActivity(appInfoIntent) }
                            ) {
                                Text(stringResource(string.settings))
                            }
                            FilledTonalButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = {
                                    uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P#readme")
                                }
                            ) {
                                Text(stringResource(string.instructions))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                FilledTonalButton(
                    onClick = { setShowResetSettingsDialog(true) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.reset_settings),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(string.resetSettings))
                }
            }
            // Extra room so the last action (Reset settings) can scroll fully above the
            // navigation bar on edge-to-edge devices.
            item { Spacer(Modifier.height(72.dp)) }
            }
            }
            } else {
                // Search mode: a flat, tappable list of every setting. Switches toggle in place,
                // pickers open their dialog/sheet and sliders step to the next value.
                val strictLabel = when (preferencesState.topicStrictness) {
                    0 -> string.strictnessBroad
                    2 -> string.strictnessStrict
                    3 -> string.strictnessVeryStrict
                    else -> string.strictnessBalanced
                }
                val popularityLabel = when (preferencesState.popularityBias) {
                    -2 -> string.popularityObscureOnly
                    -1 -> string.popularityPreferObscure
                    1 -> string.popularityPreferPopular
                    2 -> string.popularityPopularOnly
                    else -> string.popularityNoPreference
                }
                val entries = buildList {
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingsReadingTopics),
                            context.getString(string.settingsReadingTopicsDesc),
                            "topics library shuffle random offline download", R.drawable.view_list
                        ) { onNavigateToReadingSettings() }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingColorScheme),
                            if (color == Color.White) context.getString(string.colorSchemeDynamic)
                            else context.getString(string.colorSchemeColor),
                            "colour dynamic material you", R.drawable.palette
                        ) { setShowColorSchemeDialog(true) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingTheme),
                            themeMap[theme]?.second ?: theme,
                            "dark light system appearance", themeMap[theme]?.first ?: R.drawable.brightness_auto
                        ) { setShowThemeDialog(true) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingBlackTheme),
                            context.getString(string.settingBlackThemeDesc),
                            "dark amoled oled", R.drawable.dark_mode,
                            checked = preferencesState.blackTheme
                        ) { onAction(SettingsAction.SaveBlackTheme(!preferencesState.blackTheme)) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingWikipediaLanguage),
                            langCodeToName(preferencesState.lang),
                            "language articles wiki", R.drawable.translate
                        ) { setShowLanguageSheet(true) }
                    )
                    if (currentLocales != null) add(
                        SettingsSearchEntry(
                            context.getString(string.settingAppLanguage),
                            if (currentLocalesSize > 0) currentLocales.get(0).displayName
                            else context.getString(string.themeSystemDefault),
                            "locale interface ui", R.drawable.language
                        ) { setShowAppLocaleSheet(true) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingFontStyle),
                            fontStyleMap[fontStyle] ?: fontStyle,
                            "serif sans typeface text", R.drawable.serif
                        ) { setShowFontDialog(true) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingFontSize),
                            "${preferencesState.fontSize}",
                            "text size bigger smaller", R.drawable.format_size
                        ) {
                            onAction(
                                SettingsAction.SaveFontSize(
                                    if (preferencesState.fontSize >= 22) 10 else preferencesState.fontSize + 1
                                )
                            )
                        }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingArticleFormat),
                            if (preferencesState.renderMode == "html")
                                context.getString(string.articleFormatHtml)
                            else context.getString(string.articleFormatWikitext),
                            "html wikitext renderer", R.drawable.code
                        ) {
                            onAction(
                                SettingsAction.SaveRenderMode(
                                    if (preferencesState.renderMode == "html") "wikitext" else "html"
                                )
                            )
                        }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingMenuSide),
                            if (preferencesState.menuSide == "right") context.getString(string.menuSideRight)
                            else context.getString(string.menuSideLeft),
                            "drawer navigation left right", R.drawable.menu
                        ) {
                            onAction(
                                SettingsAction.SaveMenuSide(
                                    if (preferencesState.menuSide == "right") "left" else "right"
                                )
                            )
                        }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingStrictness),
                            context.getString(strictLabel),
                            "relevance filter match balanced strict", R.drawable.check
                        ) {
                            onAction(SettingsAction.SaveTopicStrictness((preferencesState.topicStrictness + 1) % 4))
                        }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingPopularity),
                            context.getString(popularityLabel),
                            "obscure popular pageviews", R.drawable.upward
                        ) {
                            onAction(
                                SettingsAction.SavePopularityBias(
                                    if (preferencesState.popularityBias >= 2) -2 else preferencesState.popularityBias + 1
                                )
                            )
                        }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingAvoidStubs),
                            context.getString(string.settingAvoidStubsDesc),
                            "short articles quality", R.drawable.expand_all,
                            checked = preferencesState.avoidStubs
                        ) { onAction(SettingsAction.SaveAvoidStubs(!preferencesState.avoidStubs)) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingPreferQuality),
                            context.getString(string.settingPreferQualityDesc),
                            "good featured", R.drawable.check,
                            checked = preferencesState.preferQuality
                        ) { onAction(SettingsAction.SavePreferQuality(!preferencesState.preferQuality)) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingImagesOnly),
                            context.getString(string.settingImagesOnlyDesc),
                            "pictures photos", R.drawable.image,
                            checked = preferencesState.onlyArticlesWithImages
                        ) {
                            onAction(
                                SettingsAction.SaveOnlyArticlesWithImages(!preferencesState.onlyArticlesWithImages)
                            )
                        }
                    )
                    switchItems.drop(1).forEach { item ->
                        add(
                            SettingsSearchEntry(
                                context.getString(item.label),
                                context.getString(item.description),
                                "switch toggle on off", item.icon,
                                checked = item.checked && item.enabled
                            ) {
                                if (item.enabled) onAction(item.actionConstructor(!item.checked))
                            }
                        )
                    }
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingReplayTutorial),
                            context.getString(string.settingReplayTutorialDesc),
                            "tour help onboarding", R.drawable.outline_info
                        ) { onReplayTutorial() }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingDebugLogging),
                            context.getString(string.settingDebugLoggingDesc),
                            "logs diagnostics", R.drawable.search_history,
                            checked = preferencesState.debugLogging
                        ) { onAction(SettingsAction.SaveDebugLogging(!preferencesState.debugLogging)) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.settingViewDebugLog),
                            "Open the current debug log", "logs diagnostics share", R.drawable.expand_all
                        ) { setShowDebugLog(true) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.statistics),
                            context.getString(string.statsSettingsDesc),
                            "stats statistics reading time streak words articles charts history", R.drawable.history
                        ) { onNavigateToStatistics() }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.vocabulary),
                            context.getString(string.vocabularyEmpty),
                            "words definitions dictionary vocabulary builder", R.drawable.view_list
                        ) { onNavigateToVocabulary() }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.backup),
                            context.getString(string.backupDesc),
                            "backup backups restore export import data storage", R.drawable.download_done
                        ) { onBackup() }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.setAsDefault),
                            context.getString(string.setAsDefaultDesc),
                            "links default browser", R.drawable.filled_info
                        ) { context.startActivity(appInfoIntent) }
                    )
                    add(
                        SettingsSearchEntry(
                            context.getString(string.resetSettings),
                            context.getString(string.resetSettingsDialogDesc),
                            "restore defaults", R.drawable.reset_settings
                        ) { setShowResetSettingsDialog(true) }
                    )
                }
                val query = settingsSearchQuery.trim().lowercase()
                val matches = entries.filter { entry ->
                    query.isBlank() || listOf(entry.title, entry.description, entry.keywords)
                        .any { it.lowercase().contains(query) }
                }
                if (matches.isEmpty()) {
                    item(key = "settings-search-empty") {
                        ListItem(
                            headlineContent = { Text(stringResource(string.noSettingsMatch)) },
                            supportingContent = { Text(stringResource(string.searchSettingsHint)) },
                            colors = listItemColors,
                            modifier = Modifier.clip(cardShape)
                        )
                    }
                } else {
                    items(matches, key = { "settings-search-${it.title}" }) { entry ->
                        ListItem(
                            leadingContent = {
                                Icon(painterResource(entry.icon), contentDescription = null)
                            },
                            headlineContent = { Text(entry.title) },
                            supportingContent = { Text(entry.description) },
                            trailingContent = {
                                when {
                                    entry.checked != null -> Switch(
                                        checked = entry.checked,
                                        onCheckedChange = { entry.action() },
                                        colors = switchColors
                                    )
                                    entry.value != null -> Text(entry.value)
                                }
                            },
                            colors = listItemColors,
                            modifier = Modifier
                                .clip(cardShape)
                                .clickable { entry.action() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontPickerDialog(
    fontStyleMap: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(string.settingFontStyle)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                fontStyleMap.forEach { (key, label) ->
                    ListItem(
                        leadingContent = {
                            RadioButton(selected = key == selected, onClick = { onSelect(key) })
                        },
                        headlineContent = { Text(label) },
                        colors = listItemColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport) { Text(stringResource(string.fontImport)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(string.cancel)) }
        }
    )
}

/** Top-level groups shown on the settings hub. */
private enum class SettingsCategory(
    val icon: Int,
    val title: Int,
    val summary: Int,
    val count: Int
) {
    ROOT(0, 0, 0, 0),
    READING(
        R.drawable.view_list,
        string.settingsReadingTopics,
        string.settingsReadingTopicsDesc,
        11
    ),
    APPEARANCE(
        R.drawable.palette,
        string.settingsAppearance,
        string.settingsAppearanceDesc,
        10
    ),
    BEHAVIOUR(
        R.drawable.translate,
        string.settingsBehaviour,
        string.settingsBehaviourDesc,
        6
    ),
    DATA(
        R.drawable.download_done,
        string.settingsData,
        string.settingsDataDesc,
        6
    ),
    ABOUT(
        R.drawable.outline_info,
        string.settingsAbout,
        string.settingsAboutDesc,
        5
    )
}

@Composable
private fun SettingsHubHeader() {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
        Text(
            text = stringResource(string.settings),
            style = typography.headlineMedium,
            color = colorScheme.onSurface
        )
        Text(
            text = stringResource(string.settingsHubDesc),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SettingsCategoryHeader(
    category: SettingsCategory,
    onBack: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(string.settingsBackToHub)
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(category.title),
                style = typography.headlineSmall,
                color = colorScheme.onSurface
            )
            Text(
                text = stringResource(category.summary),
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primaryContainer)
            ) {
                Icon(
                    painterResource(category.icon),
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp)
            ) {
                Text(
                    text = stringResource(category.title),
                    style = typography.titleMedium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = stringResource(category.summary),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = stringResource(string.settingsCategoryCount, category.count),
                    style = typography.labelMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Icon(
                painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview
@Composable
fun SettingsPreview() {
    val context = LocalContext.current
    val themeMap: Map<String, Pair<Int, String>> = remember {
        mapOf(
            "auto" to Pair(
                R.drawable.brightness_auto,
                context.getString(string.themeSystemDefault)
            ),
            "light" to Pair(R.drawable.light_mode, context.getString(string.themeLight)),
            "dark" to Pair(R.drawable.dark_mode, context.getString(string.themeDark))
        )
    }
    val reverseThemeMap: Map<String, String> = remember {
        mapOf(
            context.getString(string.themeSystemDefault) to "auto",
            context.getString(string.themeLight) to "light",
            context.getString(string.themeDark) to "dark"
        )
    }
    val fontStyleMap: Map<String, String> = remember {
        mapOf(
            "sans" to context.getString(string.fontStyleSansSerif),
            "serif" to context.getString(string.fontStyleSerif)
        )
    }
    val reverseFontStyleMap: Map<String, String> = remember {
        mapOf(
            context.getString(string.fontStyleSansSerif) to "sans",
            context.getString(string.fontStyleSerif) to "serif"
        )
    }
    val fontStyles = remember {
        listOf(
            context.getString(string.fontStyleSansSerif),
            context.getString(string.fontStyleSerif)
        )
    }
    WikiRandomizerPTheme {
        SettingsScreen(
            preferencesState = PreferencesState(),
            lastBackStackEntry = HomeSubscreen.Logo,
            recentLangs = emptyList(),
            languageSearchStr = "",
            languageSearchQuery = "",
            themeMap = themeMap,
            reverseThemeMap = reverseThemeMap,
            fontStyles = fontStyles,
            fontStyleMap = fontStyleMap,
            reverseFontStyleMap = reverseFontStyleMap,
            onAction = {},
            onHomeAction = {},
            onBack = {},
        )
    }
}

data class SettingsSwitchItem(
    val checked: Boolean,
    val icon: Int,
    val label: Int,
    val description: Int,
    val actionConstructor: (Boolean) -> SettingsAction,
    val enabled: Boolean = true
)