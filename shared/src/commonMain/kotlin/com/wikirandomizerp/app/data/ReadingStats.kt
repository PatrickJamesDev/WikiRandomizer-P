package com.wikirandomizerp.app.data

/** Local-only reading totals shown on the statistics screen. */
data class ReadingStats(
    val articlesOpened: Int = 0,
    val articlesInHistory: Int = 0,
    val savedArticles: Int = 0,
    val savedHighlights: Int = 0,
    val vocabularyWords: Int = 0,
    val readingSeconds: Long = 0L,
    val readingSessions: Int = 0,
    // Day-by-day figures. They start counting with the update that introduced them.
    val readingTodaySeconds: Long = 0L,
    val readingWeekSeconds: Long = 0L,
    val articlesToday: Int = 0,
    val articlesWeek: Int = 0,
    val activeDays: Int = 0,
    val currentStreakDays: Int = 0,
    val longestSessionSeconds: Long = 0L,
    val bestDaySeconds: Long = 0L
) {
    val readingMinutes: Long get() = readingSeconds / 60
    val readingHours: Long get() = readingSeconds / 3600
    val averageSessionSeconds: Long
        get() = if (readingSessions > 0) readingSeconds / readingSessions else 0L
}
