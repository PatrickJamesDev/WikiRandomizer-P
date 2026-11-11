package com.wikirandomizerp.app.ui.curatorsScreen.viewModel

import android.app.Application
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.AppDatabaseRepository
import com.wikirandomizerp.app.data.Curator
import com.wikirandomizerp.app.data.CuratorRepository
import com.wikirandomizerp.app.data.PreferencesRepository
import com.wikirandomizerp.app.data.TopicIndex
import com.wikirandomizerp.app.data.safeCoroutineHandler

sealed interface CuratorsUiState {
    data object Loading : CuratorsUiState
    data class Ready(
        val curators: List<Curator>,
        /** Curator ids whose picks overlap with the user's saved topics or liked articles. */
        val recommended: Set<String>,
        /** True when GitHub could not be reached and the bundled sample is shown. */
        val fromFallback: Boolean = false
    ) : CuratorsUiState
    data class Failed(val message: String) : CuratorsUiState
}

class CuratorsViewModel(
    private val application: Application,
    private val repository: CuratorRepository,
    private val preferences: PreferencesRepository,
    private val database: AppDatabaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CuratorsUiState>(CuratorsUiState.Loading)
    val state: StateFlow<CuratorsUiState> = _state.asStateFlow()

    private val _followed = MutableStateFlow<Set<String>>(emptySet())
    val followed: StateFlow<Set<String>> = _followed.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            _followed.value = readFollowed()
        }
        refresh()
    }

    fun refresh() {
        _state.value = CuratorsUiState.Loading
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            repository.load().fold(
                onSuccess = { load ->
                    val recommended = recommend(load.feed.curators)
                    _state.value = CuratorsUiState.Ready(
                        curators = load.feed.curators,
                        recommended = recommended,
                        fromFallback = load.fromFallback
                    )
                },
                onFailure = { error ->
                    _state.value = CuratorsUiState.Failed(
                        error.message ?: "Could not load the curator list"
                    )
                }
            )
        }
    }

    fun toggleFollow(id: String) {
        val next = if (id in _followed.value) _followed.value - id else _followed.value + id
        _followed.value = next
        viewModelScope.launch(Dispatchers.IO + safeCoroutineHandler) {
            preferences.saveStringPreference(FOLLOWED_KEY, next.sorted().joinToString(","))
        }
    }

    private suspend fun readFollowed(): Set<String> =
        preferences.readStringPreference(FOLLOWED_KEY)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /**
     * "People who save/like similar articles": a curator is recommended when their picks belong to
     * a topic the user already saved, or when the user liked one of the same articles. This is a
     * local, cheap comparison; nothing about the user leaves the device.
     */
    private suspend fun recommend(curators: List<Curator>): Set<String> {
        if (curators.isEmpty()) return emptySet()
        val saved = runCatching { database.getSavedArticles().first() }.getOrDefault(emptyList())
        if (saved.isEmpty()) return emptySet()
        val savedTitles = saved.map { it.title }.toHashSet()
        val savedTopics = saved.mapNotNull { it.topic }.toHashSet()
        val liked = runCatching {
            val lang = preferences.readStringPreference("lang") ?: "en"
            TopicIndex.get(application, lang).votedTitles(1).toHashSet()
        }.getOrDefault(emptySet())
        val interests = savedTitles + liked

        return curators.filter { curator ->
            curator.picks.any { pick ->
                pick.title in interests ||
                        runCatching {
                            TopicIndex.get(application, pick.lang)
                                .topicsForTitle(pick.title)
                                .any { it in savedTopics }
                        }.getOrDefault(false)
            }
        }.map { it.id }.toHashSet()
    }

    companion object {
        private const val FOLLOWED_KEY = "followed-curators"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as WikiRandomizerPApplication
                CuratorsViewModel(
                    application = app,
                    repository = CuratorRepository(app),
                    preferences = app.container.appPreferencesRepository,
                    database = app.container.appDatabaseRepository
                )
            }
        }
    }
}
