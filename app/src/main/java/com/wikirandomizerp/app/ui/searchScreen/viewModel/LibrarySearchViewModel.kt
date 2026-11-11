package com.wikirandomizerp.app.ui.searchScreen.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wikirandomizerp.app.WikiRandomizerPApplication
import com.wikirandomizerp.app.data.AppDatabaseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibrarySearchKind { SAVED, HIGHLIGHT, HISTORY, SEARCH }

data class LibrarySearchHit(
    val kind: LibrarySearchKind,
    val title: String,
    val preview: String?,
    val lang: String,
    val pageId: Int? = null,
    val query: String? = null
)

data class LibrarySearchResults(
    val loading: Boolean = false,
    val hits: List<LibrarySearchHit> = emptyList()
) {
    fun of(kind: LibrarySearchKind): List<LibrarySearchHit> = hits.filter { it.kind == kind }
}

class LibrarySearchViewModel(
    private val appDatabaseRepository: AppDatabaseRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow(LibrarySearchResults())
    val results = _results.asStateFlow()

    init {
        viewModelScope.launch {
            _query.collectLatest { raw ->
                val query = raw.trim()
                if (query.length < 2) {
                    _results.value = LibrarySearchResults()
                    return@collectLatest
                }
                _results.value = _results.value.copy(loading = true)
                delay(220)
                val hits = try {
                    search(query)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    emptyList()
                }
                _results.value = LibrarySearchResults(hits = hits)
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    private suspend fun search(query: String): List<LibrarySearchHit> = withContext(Dispatchers.IO) {
        val saved = appDatabaseRepository.searchSavedArticles(query).map { article ->
            val detail = article.note?.takeIf { it.isNotBlank() }
                ?: article.description
                ?: article.folder?.takeIf { it.isNotBlank() }
            LibrarySearchHit(
                kind = LibrarySearchKind.SAVED,
                title = article.title,
                preview = detail,
                lang = article.lang,
                pageId = article.pageId
            )
        }
        val highlights = appDatabaseRepository.searchSavedHighlights(query).map { highlight ->
            LibrarySearchHit(
                kind = LibrarySearchKind.HIGHLIGHT,
                title = highlight.title,
                preview = highlight.text,
                lang = highlight.lang,
                pageId = highlight.pageId
            )
        }
        val history = appDatabaseRepository.searchViewHistory(query).map { item ->
            LibrarySearchHit(
                kind = LibrarySearchKind.HISTORY,
                title = item.title,
                preview = item.description ?: item.topic,
                lang = item.lang
            )
        }
        val searches = appDatabaseRepository.getSearchHistory().first()
            .filter { it.query.contains(query, ignoreCase = true) }
            .distinctBy { it.query.lowercase() to it.lang }
            .take(10)
            .map { item ->
                LibrarySearchHit(
                    kind = LibrarySearchKind.SEARCH,
                    title = item.query,
                    preview = item.lang,
                    lang = item.lang,
                    query = item.query
                )
            }
        (saved + highlights + history + searches).distinctBy { hit ->
            Triple(hit.kind, hit.title.lowercase(), hit.lang)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WikiRandomizerPApplication)
                LibrarySearchViewModel(application.container.appDatabaseRepository)
            }
        }
    }
}
