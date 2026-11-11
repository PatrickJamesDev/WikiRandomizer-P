package com.wikirandomizerp.app.ui.homeScreen.viewModel

import com.wikirandomizerp.app.data.UserLanguage

sealed class HomeAction {
    data class DeleteUserLanguage(val lang: String) : HomeAction()
    data class InsertUserLanguage(val userLanguage: UserLanguage) : HomeAction()
    data class LoadFeed(val fromBack: Boolean = false) : HomeAction()
    data class LoadPage(
        val title: String?,
        val lang: String? = null,
        val random: Boolean = false,
    ) : HomeAction()

    data class LoadSavedArticle(val pageId: Int, val lang: String) : HomeAction()
    data class LoadSearch(val query: String) : HomeAction()
    data class LoadSearchResultsDebounced(val query: String) : HomeAction()
    data class MarkUserLanguageSelected(val lang: String) : HomeAction()
    data class ReloadPage(val persistLang: Boolean = false) : HomeAction()
    data class SaveArticle(
        val lang: String,
        val unableToSaveError: String,
        val unableToDeleteError: String,
        /** Optional folder chosen in the save dialog (only when the setting is on). */
        val folder: String? = null,
        /** Optional personal note written in the save dialog (only when the setting is on). */
        val note: String? = null
    ) : HomeAction()

    /** [vote]: +1 more like this, -1 not interested, 0 removes the vote. [message] is a snackbar. */
    data class Rate(val title: String, val lang: String, val vote: Int, val message: String) : HomeAction()

    /** Reads the persisted vote for an article so its like/dislike buttons start in the right state. */
    data class LoadArticleVote(val title: String, val lang: String) : HomeAction()

    /** Keeps the selected passage from the article on screen in Saved highlights. */
    data class SaveHighlight(val text: String) : HomeAction()

    /** Looks up a selected word in Wiktionary and shows the definition sheet. */
    data class DefineWord(val word: String) : HomeAction()

    /** Adds a selected word to the vocabulary builder (looking up a definition if needed). */
    data class AddVocabularyWord(val word: String) : HomeAction()

    data object DismissDictionary : HomeAction()

    /** Adds an article to the tab list without disturbing the article currently being read. */
    data class OpenInNewTab(val title: String, val lang: String) : HomeAction()

    /** Opens a fresh random article in a new tab and switches to it. */
    data object NewTab : HomeAction()

    data class SwitchTab(val id: Long) : HomeAction()
    data class CloseTab(val id: Long) : HomeAction()

    /** Undo for closing a tab: brings back the most recently closed one and opens it. */
    data object ReopenClosedTab : HomeAction()

    /** Unlimited, lightweight article back/forward (independent of the 8-entry render stack). */
    data object NavigateBack : HomeAction()
    data object NavigateForward : HomeAction()

    /** Opens the article in the system browser at its Wikipedia URL. */
    data class OpenInBrowser(val title: String, val lang: String) : HomeAction()

    /** Furthest scroll position in the current article, 0..1 (opt-in personalization). */
    data class ReadingProgress(val progress: Float) : HomeAction()

    /** Exact reading place (first visible item + pixel offset) of the visible article. */
    data class UpdateScrollPosition(val index: Int, val offset: Int) : HomeAction()

    data class SetQuery(val text: String) : HomeAction()
    /** Scrolls the current article to an [HomeSubscreen.Article.extract] index. */
    data class ScrollToSection(val extractIndex: Int) : HomeAction()
    /** Clears a one-shot scroll request after the rendered article consumed it. */
    data class ConsumeScrollToSection(val uid: Long) : HomeAction()
    data class ShowFeedErrorSnackBar(val errorString: String) : HomeAction()
    data class UpdateLanguageSearchStr(val str: String) : HomeAction()
    data class UpdateRef(val ref: String) : HomeAction()
    object FocusSearchBar : HomeAction()
    object HideRef : HomeAction()
    object LoadRandom : HomeAction()
    object ScrollToTop : HomeAction()
    object StopAll : HomeAction()
}