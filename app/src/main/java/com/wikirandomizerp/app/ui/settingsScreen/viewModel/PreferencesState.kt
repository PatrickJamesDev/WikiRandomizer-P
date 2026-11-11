package com.wikirandomizerp.app.ui.settingsScreen.viewModel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PreferencesState(
    val theme: String = "auto",
    val lang: String = "en",
    val fontStyle: String = "sans",
    val colorScheme: String = Color(0xFF4C662B).toString(),
    val fontSize: Int = 16,
    val blackTheme: Boolean = false,
    val dataSaver: Boolean = false,
    val feedEnabled: Boolean = true,
    val expandedSections: Boolean = true,
    val imageBackground: Boolean = false,
    val immersiveMode: Boolean = true,
    val renderMath: Boolean = true,
    val browsingHistory: Boolean = true,
    val searchHistory: Boolean = true,
    /** When true, article discovery uses popularity-ranked results instead of a random walk. */
    val popularArticlesOnly: Boolean = false,
    /** When true, articles shorter than 5 000 bytes of wikitext are filtered out. */
    val avoidStubs: Boolean = false,
    /** When true, the random article feature (topic-based or fully random) only returns articles that have a lead image. */
    val onlyArticlesWithImages: Boolean = false,
    /** When true, random topic articles prefer Good/Featured articles when any are available. */
    val preferQuality: Boolean = true,
    /** Mirrors DebugLog.enabled (persisted separately so it applies from app start). */
    val debugLogging: Boolean = false,
    /** Saving an article also downloads its full text for offline reading (default: just a bookmark). */
    val saveOffline: Boolean = false,
    /** Offer a folder field when saving an article (off by default). */
    val saveFolders: Boolean = false,
    /** Offer a personal note field when saving an article (off by default). */
    val saveNotes: Boolean = false,
    /** -2 only obscure, -1 prefer obscure, 0 no preference, 1 prefer popular, 2 only popular. */
    val popularityBias: Int = 0,
    /** 0 broad, 1 balanced, 2 strict, 3 very strict: how closely articles must match the topic. */
    val topicStrictness: Int = 1,
    /** Pulling down on an article loads a new random article (instead of reloading the page). */
    val pullForRandom: Boolean = true,
    /** Quietly prepares the next random articles (text and pictures) while you read. */
    val preloadArticles: Boolean = true,
    /**
     * Opt-in: learn from reading behaviour (time on page, scroll depth, likes) to pick more
     * articles from the topics the user actually reads. Off by default; no data leaves the device.
     */
    val personalizationEnabled: Boolean = true,
    /** "left" or "right": the physical side the navigation drawer opens from. */
    val menuSide: String = "left",
    /** "wikitext" (native, fast) or "html" (Wikipedia's rendered article HTML). */
    val renderMode: String = "wikitext",
    /** Accessibility: use quieter, shorter Material motion instead of expressive motion. */
    val reduceMotion: Boolean = false,
    /** Accessibility: haptic feedback for sliders and searches. */
    val hapticFeedback: Boolean = true,
    /** Accessibility: render article paragraphs with a heavier weight. */
    val boldArticleText: Boolean = false,
    /** Keep a passage automatically when a text selection is left alone for a moment. */
    val autoSaveHighlights: Boolean = false,
    /**
     * Show a gentle donation prompt after 100 / 300 / 600 / 1000 article opens. Off by default:
     * the feature is implemented but the project does not ask for money unless it is enabled.
     */
    val donationPrompts: Boolean = false,
    /**
     * Supporter mode unlocks the in-app bug reporter. There is no billing integration yet, so
     * this is a self-declared flag; when Play Billing is added it should be set by the purchase.
     */
    val supporterMode: Boolean = false
)
