package com.wikirandomizerp.app.ui.savedArticlesScreen.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.data.safeCoroutineHandler
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.PreferencesRepository
import com.wikirandomizerp.app.data.TopicBackfill
import com.wikirandomizerp.app.data.WRStatus

class SavedArticlesViewModel(
    private val appDatabaseRepository: AppDatabaseRepository,
    private val appContext: Context,
    private val appPreferencesRepository: PreferencesRepository
) : ViewModel() {

    /** Sort / layout survive leaving the screen and restarting the app. */
    private val _sortName = MutableStateFlow("DATE_NEW")
    val sortName = _sortName.asStateFlow()
    private val _layoutName = MutableStateFlow("LIST")
    val layoutName = _layoutName.asStateFlow()

    init {
        // Old saved rows predate the topic column. Fill them in once, in the background, so
        // sorting by topic is useful for existing libraries too.
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            TopicBackfill.saved(appContext, appDatabaseRepository)
        }
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appPreferencesRepository.readStringPreference("saved-sort")?.let { _sortName.value = it }
            appPreferencesRepository.readStringPreference("saved-layout")?.let { _layoutName.value = it }
        }
    }

    fun setSort(name: String) {
        _sortName.value = name
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appPreferencesRepository.saveStringPreference("saved-sort", name)
        }
    }

    fun setLayout(name: String) {
        _layoutName.value = name
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appPreferencesRepository.saveStringPreference("saved-layout", name)
        }
    }

    val savedArticleLangsFlow =
        appDatabaseRepository.getSavedArticleLanguages().distinctUntilChanged()
    val savedArticlesFlow = appDatabaseRepository.getSavedArticles().distinctUntilChanged()
    val savedFoldersFlow = appDatabaseRepository.getSavedArticleFolders().distinctUntilChanged()

    fun onAction(action: SavedArticlesAction): WRStatus {
        return when (action) {
            is SavedArticlesAction.Delete -> deleteArticle(action.pageId, action.lang)
            is SavedArticlesAction.DeleteAll -> deleteAllArticles()
            is SavedArticlesAction.SetDetails -> setDetails(
                action.pageId,
                action.lang,
                action.folder,
                action.note
            )
        }
    }

    private fun setDetails(
        pageId: Int,
        lang: String,
        folder: String?,
        note: String?
    ): WRStatus {
        val cleanFolder = folder?.trim()?.takeIf { it.isNotEmpty() }
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appDatabaseRepository.setSavedArticleFolderAndNote(
                pageId,
                lang,
                cleanFolder,
                cleanNote
            )
        }
        return WRStatus.SUCCESS
    }

    /**
     * Deletes the current article
     *
     * @return A [WRStatus] enum value indicating the status of the delete operation
     */
    private fun deleteArticle(pageId: Int, lang: String): WRStatus {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appDatabaseRepository.deleteSavedArticle(pageId, lang)
        }

        return WRStatus.SUCCESS
    }

    private fun deleteAllArticles(): WRStatus {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appDatabaseRepository.deleteAllSavedArticles()
        }
        return WRStatus.SUCCESS
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WikiRandomizerPApplication)
                val appHistoryRepository = application.container.appDatabaseRepository
                SavedArticlesViewModel(
                    appDatabaseRepository = appHistoryRepository,
                    appContext = application.applicationContext,
                    appPreferencesRepository = application.container.appPreferencesRepository
                )
            }
        }
    }

}