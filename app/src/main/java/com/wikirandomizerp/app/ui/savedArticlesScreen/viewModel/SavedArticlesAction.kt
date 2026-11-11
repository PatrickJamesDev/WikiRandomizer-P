package com.wikirandomizerp.app.ui.savedArticlesScreen.viewModel

sealed class SavedArticlesAction {
    data class Delete(val pageId: Int, val lang: String) : SavedArticlesAction()
    object DeleteAll : SavedArticlesAction()

    /** Updates the optional folder/note of one saved article from the details dialog. */
    data class SetDetails(
        val pageId: Int,
        val lang: String,
        val folder: String?,
        val note: String?
    ) : SavedArticlesAction()
}