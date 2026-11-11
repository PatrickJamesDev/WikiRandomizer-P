package com.wikirandomizerp.app.data

import kotlinx.coroutines.flow.Flow

interface DatabaseRepository {
    suspend fun insertSearchHistory(search: SearchHistoryItem, deduplicate: Boolean = true)
    suspend fun deleteSearchHistory(search: SearchHistoryItem)
    suspend fun deleteAllSearchHistory()
    suspend fun deleteOldSearchHistory()
    fun getSearchHistory(): Flow<List<SearchHistoryItem>>

    suspend fun insertViewHistory(viewHistoryItem: ViewHistoryItem)
    suspend fun deleteViewHistory(viewHistoryItem: ViewHistoryItem)
    suspend fun deleteViewHistoryByTitle(title: String, lang: String)
    suspend fun deleteAllViewHistory()
    suspend fun deleteOldViewHistory()
    fun getViewHistory(): Flow<List<ViewHistoryItem>>
    fun getRecentLanguages(): Flow<List<String>>
    suspend fun getViewedTitles(lang: String): List<String>

    suspend fun insertSavedArticle(savedArticle: SavedArticle)
    suspend fun deleteSavedArticle(pageId: Int, lang: String)
    suspend fun deleteAllSavedArticles()
    suspend fun isArticleSaved(pageId: Int, lang: String): Boolean
    suspend fun isTitleSaved(title: String, lang: String): Boolean
    suspend fun getSavedArticle(pageId: Int, lang: String): SavedArticle?
    suspend fun getSavedArticleInfo(pageId: Int, lang: String): ArticleInfo?
    fun getSavedArticleLanguages(): Flow<List<LanguageInfo>>
    fun getSavedArticleFolders(): Flow<List<String>>
    fun getSavedArticles(): Flow<List<ArticleInfo>>
    suspend fun searchSavedArticles(query: String): List<ArticleInfo>
    suspend fun getSavedArticlesWithoutTopic(): List<ArticleInfo>
    suspend fun setSavedArticleTopic(pageId: Int, lang: String, topic: String)
    suspend fun setSavedArticleFolder(pageId: Int, lang: String, folder: String?)
    suspend fun setSavedArticleNote(pageId: Int, lang: String, note: String?)
    suspend fun setSavedArticleFolderAndNote(
        pageId: Int,
        lang: String,
        folder: String?,
        note: String?
    )
    suspend fun getViewHistoryWithoutTopic(): List<ViewHistoryItem>
    suspend fun searchViewHistory(query: String): List<ViewHistoryItem>
    suspend fun setViewHistoryTopic(time: Long, topic: String)

    suspend fun insertSavedHighlight(highlight: SavedHighlight): Long
    suspend fun findSavedHighlight(title: String, lang: String, text: String): SavedHighlight?
    suspend fun getSavedHighlightsForArticle(title: String, lang: String): List<SavedHighlight>
    suspend fun deleteSavedHighlight(id: Long)
    suspend fun deleteAllSavedHighlights()
    fun getSavedHighlights(): Flow<List<SavedHighlight>>
    suspend fun searchSavedHighlights(query: String): List<SavedHighlight>

    suspend fun insertVocabularyWord(word: VocabularyWord): Long
    suspend fun findVocabularyWord(word: String, lang: String): VocabularyWord?
    suspend fun deleteVocabularyWord(id: Long)
    suspend fun deleteAllVocabularyWords()
    fun getVocabularyWords(): Flow<List<VocabularyWord>>
    suspend fun countVocabularyWords(): Int
    suspend fun countViewHistory(): Int
    suspend fun countSavedArticles(): Int
    suspend fun countSavedHighlights(): Int

    suspend fun insertUserLanguage(userLanguage: UserLanguage)
    suspend fun deleteUserLanguage(lang: String)
    suspend fun deselectAllUserLanguages()
    suspend fun markUserLanguageSelected(lang: String)
    fun getUserLanguages(): Flow<List<UserLanguage>>
}

class AppDatabaseRepository(
    private val searchHistoryDao: SearchHistoryDao,
    private val savedArticleDao: SavedArticleDao,
    private val savedHighlightDao: SavedHighlightDao,
    private val vocabularyDao: VocabularyDao,
    private val viewHistoryDao: ViewHistoryDao,
    private val userLanguageDao: UserLanguageDao
) : DatabaseRepository {
    override suspend fun insertSearchHistory(search: SearchHistoryItem, deduplicate: Boolean) {
        if (deduplicate) searchHistoryDao.deduplicateSearch(search.query, search.lang)
        searchHistoryDao.insert(search)
    }

    override suspend fun deleteSearchHistory(search: SearchHistoryItem) =
        searchHistoryDao.delete(search)

    override suspend fun deleteAllSearchHistory() = searchHistoryDao.deleteAll()
    override suspend fun deleteOldSearchHistory() = searchHistoryDao.deleteOld()
    override fun getSearchHistory(): Flow<List<SearchHistoryItem>> =
        searchHistoryDao.getSearchHistory()

    override suspend fun insertViewHistory(viewHistoryItem: ViewHistoryItem) {
        // Keep one entry per article (moved to the top) instead of only de-duplicating
        // consecutive visits, so revisits don't flood the 200-item history.
        viewHistoryDao.deleteByTitle(viewHistoryItem.title, viewHistoryItem.lang)
        viewHistoryDao.insert(viewHistoryItem)
    }

    override suspend fun deleteViewHistory(viewHistoryItem: ViewHistoryItem) =
        viewHistoryDao.delete(viewHistoryItem)

    override suspend fun deleteViewHistoryByTitle(title: String, lang: String) =
        viewHistoryDao.deleteByTitle(title, lang)

    override suspend fun deleteAllViewHistory() = viewHistoryDao.deleteAll()
    override suspend fun deleteOldViewHistory() = viewHistoryDao.deleteOld()
    override fun getViewHistory(): Flow<List<ViewHistoryItem>> = viewHistoryDao.getViewHistory()
    override fun getRecentLanguages(): Flow<List<String>> = viewHistoryDao.getRecentLanguages()
    override suspend fun getViewedTitles(lang: String): List<String> =
        viewHistoryDao.getViewedTitles(lang)

    override suspend fun insertSavedArticle(savedArticle: SavedArticle) =
        savedArticleDao.insert(savedArticle)

    override suspend fun deleteSavedArticle(pageId: Int, lang: String) =
        savedArticleDao.delete(pageId, lang)

    override suspend fun deleteAllSavedArticles() = savedArticleDao.deleteAll()
    override suspend fun isArticleSaved(pageId: Int, lang: String): Boolean =
        savedArticleDao.isSaved(pageId, lang)

    override suspend fun isTitleSaved(title: String, lang: String): Boolean =
        savedArticleDao.isTitleSaved(title, lang)

    override suspend fun getSavedArticle(pageId: Int, lang: String): SavedArticle? =
        savedArticleDao.getSavedArticle(pageId, lang)

    override suspend fun getSavedArticleInfo(pageId: Int, lang: String): ArticleInfo? =
        savedArticleDao.getSavedArticleInfo(pageId, lang)

    override fun getSavedArticleLanguages(): Flow<List<LanguageInfo>> =
        savedArticleDao.getSavedArticleLanguages()

    override fun getSavedArticleFolders(): Flow<List<String>> =
        savedArticleDao.getSavedArticleFolders()

    override fun getSavedArticles(): Flow<List<ArticleInfo>> =
        savedArticleDao.getAllSavedArticles()

    override suspend fun searchSavedArticles(query: String): List<ArticleInfo> =
        if (query.isBlank()) emptyList() else savedArticleDao.search(query)

    override suspend fun getSavedArticlesWithoutTopic(): List<ArticleInfo> =
        savedArticleDao.getSavedArticlesWithoutTopic()

    override suspend fun setSavedArticleTopic(pageId: Int, lang: String, topic: String) =
        savedArticleDao.setTopic(pageId, lang, topic)

    override suspend fun setSavedArticleFolder(pageId: Int, lang: String, folder: String?) =
        savedArticleDao.setFolder(pageId, lang, folder)

    override suspend fun setSavedArticleNote(pageId: Int, lang: String, note: String?) =
        savedArticleDao.setNote(pageId, lang, note)

    override suspend fun setSavedArticleFolderAndNote(
        pageId: Int,
        lang: String,
        folder: String?,
        note: String?
    ) = savedArticleDao.setFolderAndNote(pageId, lang, folder, note)

    override suspend fun getViewHistoryWithoutTopic(): List<ViewHistoryItem> =
        viewHistoryDao.getViewHistoryWithoutTopic()

    override suspend fun searchViewHistory(query: String): List<ViewHistoryItem> =
        if (query.isBlank()) emptyList() else viewHistoryDao.search(query)

    override suspend fun setViewHistoryTopic(time: Long, topic: String) =
        viewHistoryDao.setTopic(time, topic)

    override suspend fun insertSavedHighlight(highlight: SavedHighlight): Long =
        savedHighlightDao.insert(highlight)

    override suspend fun findSavedHighlight(title: String, lang: String, text: String): SavedHighlight? =
        savedHighlightDao.find(title, lang, text)

    override suspend fun getSavedHighlightsForArticle(
        title: String,
        lang: String
    ): List<SavedHighlight> = savedHighlightDao.getForArticleOnce(title, lang)

    override suspend fun deleteSavedHighlight(id: Long) =
        savedHighlightDao.deleteById(id)

    override suspend fun deleteAllSavedHighlights() =
        savedHighlightDao.deleteAll()

    override fun getSavedHighlights(): Flow<List<SavedHighlight>> =
        savedHighlightDao.getAll()

    override suspend fun searchSavedHighlights(query: String): List<SavedHighlight> =
        if (query.isBlank()) emptyList() else savedHighlightDao.search(query)

    override suspend fun insertVocabularyWord(word: VocabularyWord): Long =
        vocabularyDao.insert(word)

    override suspend fun findVocabularyWord(word: String, lang: String): VocabularyWord? =
        vocabularyDao.find(word, lang)

    override suspend fun deleteVocabularyWord(id: Long) = vocabularyDao.delete(id)
    override suspend fun deleteAllVocabularyWords() = vocabularyDao.deleteAll()
    override fun getVocabularyWords(): Flow<List<VocabularyWord>> = vocabularyDao.getAll()
    override suspend fun countVocabularyWords(): Int = vocabularyDao.count()
    override suspend fun countViewHistory(): Int = viewHistoryDao.count()
    override suspend fun countSavedArticles(): Int = savedArticleDao.count()
    override suspend fun countSavedHighlights(): Int = savedHighlightDao.count()

    override suspend fun insertUserLanguage(userLanguage: UserLanguage) {
        if (userLanguage.selected) deselectAllUserLanguages()
        userLanguageDao.insert(userLanguage)
    }

    override suspend fun deleteUserLanguage(lang: String) =
        userLanguageDao.delete(lang)

    override suspend fun deselectAllUserLanguages() = userLanguageDao.deselectAll()
    override suspend fun markUserLanguageSelected(lang: String) = userLanguageDao.markSelected(lang)
    override fun getUserLanguages(): Flow<List<UserLanguage>> = userLanguageDao.getUserLanguages()
}