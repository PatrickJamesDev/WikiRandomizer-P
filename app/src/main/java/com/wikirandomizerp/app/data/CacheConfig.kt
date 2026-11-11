package com.wikirandomizerp.app.data

/**
 * One place for the on-device cache and preload budget.
 *
 * Both the article text cache and Coil's image cache get the same 200 MB ceiling, so a device
 * can hold a large offline reading buffer without either side growing without bound. The
 * prefetch limits below decide how many articles and pictures are prepared ahead of time.
 */
object CacheConfig {
    /** Disk budget for each cache (article text / images). */
    const val DISK_CACHE_BYTES = 200L * 1024 * 1024

    /** Upper bound on cached article files; the byte budget is the real limit. */
    const val ARTICLE_MAX_FILES = 8_000

    /** Most images one article may warm. */
    const val MAX_ARTICLE_IMAGES = 100

    /** High-resolution (1920 px) renditions warmed per article. */
    const val MAX_HIGH_RES_IMAGES = 60
}
