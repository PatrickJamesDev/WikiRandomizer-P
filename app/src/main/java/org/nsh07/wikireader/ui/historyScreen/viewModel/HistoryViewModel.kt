package org.nsh07.wikireader.ui.historyScreen.viewModel

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
import org.nsh07.wikireader.data.safeCoroutineHandler
import org.nsh07.wikireader.WikiRandomizerPApplication
import org.nsh07.wikireader.data.AppDatabaseRepository
import org.nsh07.wikireader.data.PreferencesRepository
import org.nsh07.wikireader.data.TopicBackfill
import org.nsh07.wikireader.data.ViewHistoryItem

class HistoryViewModel(
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
        // History rows written before the topic column get tagged once, in the background.
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            TopicBackfill.history(appContext, appDatabaseRepository)
        }
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appPreferencesRepository.readStringPreference("history-sort")?.let { _sortName.value = it }
            appPreferencesRepository.readStringPreference("history-layout")?.let { _layoutName.value = it }
        }
    }

    fun setSort(name: String) {
        _sortName.value = name
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appPreferencesRepository.saveStringPreference("history-sort", name)
        }
    }

    fun setLayout(name: String) {
        _layoutName.value = name
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appPreferencesRepository.saveStringPreference("history-layout", name)
        }
    }

    val viewHistoryFlow = appDatabaseRepository.getViewHistory().distinctUntilChanged()

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.InsertItem -> insertViewHistoryItem(action.item)
            is HistoryAction.RemoveItem -> removeViewHistoryItem(action.item)
            is HistoryAction.RemoveAll -> removeViewHistoryItem(null)
        }
    }

    private fun insertViewHistoryItem(item: ViewHistoryItem) =
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appDatabaseRepository.insertViewHistory(item)
        }

    private fun removeViewHistoryItem(item: ViewHistoryItem?) {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            if (item != null) appDatabaseRepository.deleteViewHistory(item)
            else appDatabaseRepository.deleteAllViewHistory()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WikiRandomizerPApplication)
                val appHistoryRepository = application.container.appDatabaseRepository
                HistoryViewModel(
                    appDatabaseRepository = appHistoryRepository,
                    appContext = application.applicationContext,
                    appPreferencesRepository = application.container.appPreferencesRepository
                )
            }
        }
    }

}