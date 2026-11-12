package com.wikirandomizerp.app.ui.settingsScreen.viewModel

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.data.safeCoroutineHandler
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.AppStatus
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.data.PreferencesRepository
import com.wikirandomizerp.app.data.TopicIndex
import com.wikirandomizerp.app.data.UserLanguage
import com.wikirandomizerp.app.data.langCodeToName
import com.wikirandomizerp.app.network.HostGate

class SettingsViewModel(
    private val appStatusFlow: MutableStateFlow<AppStatus>,
    private val preferencesStateMutableFlow: MutableStateFlow<PreferencesState>,
    private val appDatabaseRepository: AppDatabaseRepository,
    private val appPreferencesRepository: PreferencesRepository,
    private val appContext: Context
) : ViewModel() {

    val preferencesState: StateFlow<PreferencesState> = preferencesStateMutableFlow.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            try {
                loadPreferencesAndInitialize()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Unreadable settings database: start with defaults rather than a stuck splash screen.
                DebugLog.e("Settings", "could not load preferences, using defaults", e)
                appStatusFlow.update { AppStatus.INITIALIZED }
            }
        }
    }

    private suspend fun loadPreferencesAndInitialize() {
        // One query for all three preference tables instead of ~30 individual reads. On a cold
        // start this removes most of the database round-trips that used to happen before the
        // splash screen could go away.
        val snapshot = appPreferencesRepository.readAllPreferences()
        val strings = snapshot.strings.toMutableMap()
        val ints = snapshot.ints.toMutableMap()
        val booleans = snapshot.booleans.toMutableMap()

        suspend fun string(key: String, default: String): String =
            strings[key] ?: appPreferencesRepository.saveStringPreference(key, default)
                .also { strings[key] = it }

        suspend fun int(key: String, default: Int): Int =
            ints[key] ?: appPreferencesRepository.saveIntPreference(key, default)
                .also { ints[key] = it }

        suspend fun boolean(key: String, default: Boolean): Boolean =
            booleans[key] ?: appPreferencesRepository.saveBooleanPreference(key, default)
                .also { booleans[key] = it }

        val colorScheme = string("color-scheme", Color(0xFF4C662B).toString())
        val fontStyle = string("font-style", "sans")
        val lang = string("lang", "en")
        val theme = string("theme", "auto")
        val fontSize = int("font-size", 16)
        val lineSpacing = int("line-spacing", 100).coerceIn(80, 200)
        val readingMargin = int("reading-margin", 16).coerceIn(4, 48)
        val justifyText = boolean("justify-text", false)
        val sepiaTheme = boolean("sepia-theme", false)
        val linkPreviews = boolean("link-previews", false)
        val talkPages = boolean("talk-pages", false)
        val dailyNotification = boolean("daily-notification", false)
        val homeWidget = boolean("home-widget", false)
        val blackTheme = boolean("black-theme", false)
        val dataSaver = boolean("data-saver", false)
        val feedEnabled = boolean("feed-enabled", true)

        // 1.0 beta changed two defaults: sections now open by default (so cladograms and
        // long sections are visible immediately) and personalization is on by default.
        // Apply that once to existing installs; after this marker the user's own choice wins.
        if (booleans["defaults-1.0-beta"] != true) {
            appPreferencesRepository.saveBooleanPreference("expanded-sections", true)
            appPreferencesRepository.saveBooleanPreference("personalization-enabled", true)
            appPreferencesRepository.saveBooleanPreference("defaults-1.0-beta", true)
            booleans["expanded-sections"] = true
            booleans["personalization-enabled"] = true
            booleans["defaults-1.0-beta"] = true
        }
        val expandedSections = boolean("expanded-sections", true)
        val imageBackground = boolean("image-background", false)
        val immersiveMode = boolean("immersive-mode", true)
        val renderMath = boolean("render-math", true)
        val browsingHistory = boolean("browsing-history", true)
        val searchHistory = boolean("search-history", true)
        val popularArticlesOnly = boolean("popular-articles-only", false)
        val avoidStubs = boolean("avoid-stubs", false)
        val onlyArticlesWithImages = boolean("only-articles-with-images", false)
        val saveOffline = boolean("save-offline", false)
        val saveFolders = boolean("save-folders", false)
        val saveNotes = boolean("save-notes", false)
        val inaturalistEnabled = boolean("inaturalist-enabled", false)
        val inaturalistGallery = boolean("inaturalist-gallery", false)
        val swipeGestures = boolean("swipe-gestures", false)
        val wikivoyageEnabled = boolean("wikivoyage-enabled", false)
        // Set before the first network request can run. Changing the source mid-session only
        // affects subsequent requests; a restart is recommended after toggling it.
        HostGate.project = if (wikivoyageEnabled) "wikivoyage" else "wikipedia"
        val popularityBias = int("popularity-bias", 0)
        val topicStrictness = int("topic-strictness", 1)
        val preferQuality = boolean("prefer-quality", true)
        val pullForRandom = boolean("pull-for-random", true)
        val preloadArticles = boolean("preload-articles", true)
        val personalizationEnabled = boolean("personalization-enabled", true)
        val menuSide = string("menu-side", "left")
            .takeIf { it == "left" || it == "right" } ?: "left"
        val renderMode = string("render-mode", "wikitext")
            .takeIf { it == "wikitext" || it == "html" } ?: "wikitext"
        val reduceMotion = boolean("reduce-motion", false)
        val hapticFeedback = boolean("haptic-feedback", true)
        val boldArticleText = boolean("bold-article-text", false)
        val autoSaveHighlights = boolean("auto-save-highlights", false)
        val donationPrompts = boolean("donation-prompts", false)
        val supporterMode = boolean("supporter-mode", false)

        preferencesStateMutableFlow.update { currentState ->
            currentState.copy(
                debugLogging = DebugLog.enabled,
                saveOffline = saveOffline,
                saveFolders = saveFolders,
                saveNotes = saveNotes,
                inaturalistEnabled = inaturalistEnabled,
                inaturalistGallery = inaturalistGallery,
                swipeGestures = swipeGestures,
                wikivoyageEnabled = wikivoyageEnabled,
                popularityBias = popularityBias,
                topicStrictness = topicStrictness,
                preferQuality = preferQuality,
                pullForRandom = pullForRandom,
                preloadArticles = preloadArticles,
                personalizationEnabled = personalizationEnabled,
                menuSide = menuSide,
                renderMode = renderMode,
                reduceMotion = reduceMotion,
                hapticFeedback = hapticFeedback,
                boldArticleText = boldArticleText,
                autoSaveHighlights = autoSaveHighlights,
                donationPrompts = donationPrompts,
                supporterMode = supporterMode,
                blackTheme = blackTheme,
                colorScheme = colorScheme,
                dataSaver = dataSaver,
                feedEnabled = feedEnabled,
                expandedSections = expandedSections,
                fontSize = fontSize,
                lineSpacing = lineSpacing,
                readingMargin = readingMargin,
                justifyText = justifyText,
                sepiaTheme = sepiaTheme,
                linkPreviews = linkPreviews,
                talkPages = talkPages,
                dailyNotification = dailyNotification,
                homeWidget = homeWidget,
                fontStyle = fontStyle,
                imageBackground = imageBackground,
                immersiveMode = immersiveMode,
                lang = lang,
                renderMath = renderMath,
                searchHistory = searchHistory,
                browsingHistory = browsingHistory,
                theme = theme,
                popularArticlesOnly = popularArticlesOnly,
                avoidStubs = avoidStubs,
                onlyArticlesWithImages = onlyArticlesWithImages
            )
        }

        // Let the first frame in immediately. Cleanup and the language-chip sync are useful but
        // must never keep the splash screen up (a slow or failing database looked like a freeze).
        appStatusFlow.update { AppStatus.INITIALIZED }

        // Restore optional developer-preview scheduling without blocking the first frame.
        runCatching {
            com.wikirandomizerp.app.widget.WidgetPrefs.setEnabled(appContext, homeWidget)
            com.wikirandomizerp.app.data.notifications.DailyArticleScheduler.update(
                appContext,
                dailyNotification,
                lang
            )
        }

        try {
            appDatabaseRepository.insertUserLanguage(
                UserLanguage(lang, langCodeToName(lang), selected = true)
            )
            appDatabaseRepository.deleteOldSearchHistory()
            appDatabaseRepository.deleteOldViewHistory()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("Settings", "start-up housekeeping failed (ignored)", e)
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SaveTheme -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(theme = action.value)
                }
                appPreferencesRepository.saveStringPreference("theme", action.value)
            }

            is SettingsAction.SaveFontStyle -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(fontStyle = action.value)
                }
                appPreferencesRepository.saveStringPreference("font-style", action.value)
            }

            is SettingsAction.SaveLang -> {
                val lang = action.value
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(lang = lang)
                }
                viewModelScope.launch(safeCoroutineHandler) {
                    appPreferencesRepository.saveStringPreference("lang", lang)
                    // The search screen's language chips come from this table. Without syncing it,
                    // the chip kept saying "English" while title search already used German.
                    try {
                        appDatabaseRepository.insertUserLanguage(
                            UserLanguage(
                                lang = lang,
                                langName = langCodeToName(lang),
                                selected = true
                            )
                        )
                    } catch (e: Exception) {
                        DebugLog.w("Settings", "could not sync the search language chip: ${e.message}")
                    }
                }
            }

            is SettingsAction.SaveColorScheme -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(colorScheme = action.value)
                }
                appPreferencesRepository.saveStringPreference("color-scheme", action.value)
            }

            is SettingsAction.SaveBlackTheme -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(blackTheme = action.value)
                }
                appPreferencesRepository.saveBooleanPreference("black-theme", action.value)
            }

            is SettingsAction.SaveFontSize -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveIntPreference("font-size", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(fontSize = action.value)
                }
            }

            is SettingsAction.SaveLineSpacing -> viewModelScope.launch(safeCoroutineHandler) {
                val value = action.value.coerceIn(80, 200)
                appPreferencesRepository.saveIntPreference("line-spacing", value)
                preferencesStateMutableFlow.update { it.copy(lineSpacing = value) }
            }

            is SettingsAction.SaveReadingMargin -> viewModelScope.launch(safeCoroutineHandler) {
                val value = action.value.coerceIn(4, 48)
                appPreferencesRepository.saveIntPreference("reading-margin", value)
                preferencesStateMutableFlow.update { it.copy(readingMargin = value) }
            }

            is SettingsAction.SaveJustifyText -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("justify-text", action.value)
                preferencesStateMutableFlow.update { it.copy(justifyText = action.value) }
            }

            is SettingsAction.SaveSepiaTheme -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("sepia-theme", action.value)
                preferencesStateMutableFlow.update { it.copy(sepiaTheme = action.value) }
            }

            is SettingsAction.SaveLinkPreviews -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("link-previews", action.value)
                preferencesStateMutableFlow.update { it.copy(linkPreviews = action.value) }
            }

            is SettingsAction.SaveTalkPages -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("talk-pages", action.value)
                preferencesStateMutableFlow.update { it.copy(talkPages = action.value) }
            }

            is SettingsAction.SaveDailyNotification -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("daily-notification", action.value)
                preferencesStateMutableFlow.update { it.copy(dailyNotification = action.value) }
                com.wikirandomizerp.app.data.notifications.DailyArticleScheduler.update(
                    appContext,
                    action.value,
                    preferencesStateMutableFlow.value.lang
                )
            }

            is SettingsAction.SaveHomeWidget -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("home-widget", action.value)
                preferencesStateMutableFlow.update { it.copy(homeWidget = action.value) }
                com.wikirandomizerp.app.widget.WidgetPrefs.setEnabled(appContext, action.value)
                com.wikirandomizerp.app.widget.RandomArticleWidgetProvider.updateAll(appContext)
            }

            is SettingsAction.SaveExpandedSections -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("expanded-sections", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(expandedSections = action.value)
                }
            }

            is SettingsAction.SaveRenderMath -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("render-math", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(renderMath = action.value)
                }
            }

            is SettingsAction.SaveHistory -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("browsing-history", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(browsingHistory = action.value)
                }
            }

            is SettingsAction.SaveSearchHistory -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("search-history", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(searchHistory = action.value)
                }
            }

            is SettingsAction.SaveDataSaver -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("data-saver", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(dataSaver = action.value)
                }
            }

            is SettingsAction.SaveFeedEnabled -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("feed-enabled", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(feedEnabled = action.value)
                }
            }

            is SettingsAction.SaveImageBackground -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("image-background", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(imageBackground = action.value)
                }
            }

            is SettingsAction.SaveImmersiveMode -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("immersive-mode", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(immersiveMode = action.value)
                }
            }

            is SettingsAction.SaveDonationPrompts -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("donation-prompts", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(donationPrompts = action.value)
                }
            }

            is SettingsAction.SaveSupporterMode -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("supporter-mode", action.value)
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(supporterMode = action.value)
                }
            }

            is SettingsAction.SavePopularArticlesOnly -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("popular-articles-only", action.value)
                preferencesStateMutableFlow.update { it.copy(popularArticlesOnly = action.value) }
            }

            is SettingsAction.SaveAvoidStubs -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("avoid-stubs", action.value)
                preferencesStateMutableFlow.update { it.copy(avoidStubs = action.value) }
            }

            is SettingsAction.SaveOnlyArticlesWithImages -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("only-articles-with-images", action.value)
                preferencesStateMutableFlow.update { it.copy(onlyArticlesWithImages = action.value) }
            }

            is SettingsAction.SaveSaveOffline -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("save-offline", action.value)
                preferencesStateMutableFlow.update { it.copy(saveOffline = action.value) }
            }

            is SettingsAction.SaveSaveFolders -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("save-folders", action.value)
                preferencesStateMutableFlow.update { it.copy(saveFolders = action.value) }
            }

            is SettingsAction.SaveSaveNotes -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("save-notes", action.value)
                preferencesStateMutableFlow.update { it.copy(saveNotes = action.value) }
            }

            is SettingsAction.SaveTopicStrictness -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveIntPreference("topic-strictness", action.value)
                preferencesStateMutableFlow.update { it.copy(topicStrictness = action.value) }
            }

            is SettingsAction.SaveINaturalist -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(inaturalistEnabled = action.value)
                }
                appPreferencesRepository.saveBooleanPreference("inaturalist-enabled", action.value)
            }

            is SettingsAction.SaveINaturalistGallery -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(inaturalistGallery = action.value)
                }
                appPreferencesRepository.saveBooleanPreference("inaturalist-gallery", action.value)
            }

            is SettingsAction.SaveSwipeGestures -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(swipeGestures = action.value)
                }
                appPreferencesRepository.saveBooleanPreference("swipe-gestures", action.value)
            }

            is SettingsAction.SaveWikivoyage -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { currentState ->
                    currentState.copy(wikivoyageEnabled = action.value)
                }
                appPreferencesRepository.saveBooleanPreference("wikivoyage-enabled", action.value)
                HostGate.project = if (action.value) "wikivoyage" else "wikipedia"
            }

            is SettingsAction.SavePopularityBias -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveIntPreference("popularity-bias", action.value)
                preferencesStateMutableFlow.update { it.copy(popularityBias = action.value) }
            }

            is SettingsAction.SaveDebugLogging -> {
                DebugLog.setEnabled(action.value)
                preferencesStateMutableFlow.update { it.copy(debugLogging = action.value) }
            }

            is SettingsAction.SavePreferQuality -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("prefer-quality", action.value)
                preferencesStateMutableFlow.update { it.copy(preferQuality = action.value) }
            }

            is SettingsAction.SavePullForRandom -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("pull-for-random", action.value)
                preferencesStateMutableFlow.update { it.copy(pullForRandom = action.value) }
            }

            is SettingsAction.SavePreloadArticles -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("preload-articles", action.value)
                preferencesStateMutableFlow.update { it.copy(preloadArticles = action.value) }
            }

            is SettingsAction.SavePersonalization -> viewModelScope.launch(safeCoroutineHandler) {
                appPreferencesRepository.saveBooleanPreference("personalization-enabled", action.value)
                preferencesStateMutableFlow.update { it.copy(personalizationEnabled = action.value) }
                // Turning the feature off also forgets what was learned: the user asked for no
                // behaviour tracking, and stale scores should not quietly come back later.
                if (!action.value) {
                    val lang = preferencesStateMutableFlow.value.lang
                    TopicIndex.get(appContext, lang).clearPersonalization()
                }
            }

            is SettingsAction.SaveMenuSide -> viewModelScope.launch(safeCoroutineHandler) {
                val side = if (action.value == "right") "right" else "left"
                preferencesStateMutableFlow.update { it.copy(menuSide = side) }
                appPreferencesRepository.saveStringPreference("menu-side", side)
            }

            is SettingsAction.SaveRenderMode -> viewModelScope.launch(safeCoroutineHandler) {
                val mode = if (action.value == "html") "html" else "wikitext"
                preferencesStateMutableFlow.update { it.copy(renderMode = mode) }
                appPreferencesRepository.saveStringPreference("render-mode", mode)
            }

            is SettingsAction.SaveReduceMotion -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { it.copy(reduceMotion = action.value) }
                appPreferencesRepository.saveBooleanPreference("reduce-motion", action.value)
            }

            is SettingsAction.SaveHapticFeedback -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { it.copy(hapticFeedback = action.value) }
                appPreferencesRepository.saveBooleanPreference("haptic-feedback", action.value)
            }

            is SettingsAction.SaveBoldArticleText -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { it.copy(boldArticleText = action.value) }
                appPreferencesRepository.saveBooleanPreference("bold-article-text", action.value)
            }

            is SettingsAction.SaveAutoSaveHighlights -> viewModelScope.launch(safeCoroutineHandler) {
                preferencesStateMutableFlow.update { it.copy(autoSaveHighlights = action.value) }
                appPreferencesRepository.saveBooleanPreference("auto-save-highlights", action.value)
            }

            is SettingsAction.ResetSettings -> viewModelScope.launch(safeCoroutineHandler) {
                try {
                    appPreferencesRepository.resetSettings()
                    // Reset also means "forget learned behaviour" (the feature is off by default).
                    try {
                        TopicIndex.get(
                            appContext,
                            HostGate.storageLanguage(preferencesStateMutableFlow.value.lang)
                        ).clearPersonalization()
                    } catch (e: Exception) {
                        DebugLog.w("Settings", "could not clear personalization on reset: ${e.message}")
                    }
                    HostGate.project = "wikipedia"
                    preferencesStateMutableFlow.update {
                        PreferencesState()
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Error in restoring settings: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WikiRandomizerPApplication)
                val appStatusFlow = application.container.appStatus
                val preferencesStateMutableFlow = application.container.preferencesStateMutableFlow
                val appPreferencesRepository = application.container.appPreferencesRepository
                val appHistoryRepository = application.container.appDatabaseRepository
                SettingsViewModel(
                    appStatusFlow = appStatusFlow,
                    preferencesStateMutableFlow = preferencesStateMutableFlow,
                    appPreferencesRepository = appPreferencesRepository,
                    appDatabaseRepository = appHistoryRepository,
                    appContext = application.applicationContext
                )
            }
        }
    }

}
