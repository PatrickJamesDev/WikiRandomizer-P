package com.wikirandomizerp.app.ui.highlightsScreen.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.safeCoroutineHandler

class HighlightsViewModel(
    private val appDatabaseRepository: AppDatabaseRepository
) : ViewModel() {

    val highlightsFlow = appDatabaseRepository.getSavedHighlights().distinctUntilChanged()

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appDatabaseRepository.deleteSavedHighlight(id)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            appDatabaseRepository.deleteAllSavedHighlights()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WikiRandomizerPApplication)
                HighlightsViewModel(application.container.appDatabaseRepository)
            }
        }
    }
}
