package com.wikirandomizerp.app.data

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.wikirandomizerp.app.BuildConfig
import com.wikirandomizerp.app.data.billing.DonationBilling
import com.wikirandomizerp.app.data.sync.LocalSyncStore
import com.wikirandomizerp.app.network.DebugLogInterceptor
import com.wikirandomizerp.app.network.HostSelectionInterceptor
import java.util.concurrent.TimeUnit
import com.wikirandomizerp.app.network.WikipediaApiService
import com.wikirandomizerp.app.ui.settingsScreen.viewModel.PreferencesState
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File

interface AppContainer {
    val appStatus: MutableStateFlow<AppStatus>
    val preferencesStateMutableFlow: MutableStateFlow<PreferencesState>
    val interceptor: HostSelectionInterceptor
    val wikipediaRepository: WikipediaRepository
    val appPreferencesRepository: AppPreferencesRepository
    val appDatabaseRepository: AppDatabaseRepository
    val updateChecker: UpdateChecker
    val backupManager: BackupManager
    val donationBilling: DonationBilling
    val localSyncStore: LocalSyncStore
    val userAgentString: String
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val baseUrl = "https://en.wikipedia.org"
    private val json = Json { ignoreUnknownKeys = true }

    override val interceptor = HostSelectionInterceptor()

    override val appStatus = MutableStateFlow(AppStatus.NOT_INITIALIZED)
    override val preferencesStateMutableFlow = MutableStateFlow(PreferencesState())
    override val userAgentString: String =
        "WikiRandomizer-P/${BuildConfig.VERSION_NAME} (https://github.com/PatrickJamesDev/WikiRandomizer-P) okhttp/5.1.0 retrofit/3.0.0"

    private val okHttpClient by lazy {
        // 50 MB disk cache — eliminates repeat round-trips for recently-viewed articles
        // and search results (Wikipedia's maxage/smaxage=900 parameters are honoured by
        // OkHttp's Cache-Control machinery when we attach a cache).
        val cacheDir = File(context.cacheDir, "okhttp_cache")
        val cache = Cache(cacheDir, 50L * 1024 * 1024) // 50 MB

        // OkHttp allows only 5 simultaneous requests per host by default, which would silently
        // cap the library builder's parallel downloads (all requests go to one Wikipedia host).
        val dispatcher = Dispatcher().apply {
            maxRequests = 48
            maxRequestsPerHost = 12
        }

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .cache(cache)
            // Defaults are 10 s; slow mobile connections and big category listings need more,
            // otherwise brief slowness shows up as a hard "network error".
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header(
                            "User-Agent",
                            userAgentString
                        )
                        .build()
                )
            }
            .addInterceptor(interceptor)
            .addInterceptor(DebugLogInterceptor()) // after the host rewrite: logs the final URL
            .build()
    }

    /**
     * GitHub is not Wikipedia: the shared client's [HostSelectionInterceptor] rewrites every host
     * to the selected Wikipedia language, which turned the release API request into a 404 on
     * en.wikipedia.org. Updates use their own client so they always reach api.github.com and the
     * release asset host. It also keeps the 50 MB Wikipedia cache out of APK downloads.
     */
    private val updateHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("User-Agent", userAgentString)
                        .build()
                )
            }
            .addInterceptor(DebugLogInterceptor())
            .build()
    }

    // Single Retrofit instance with ScalarsConverterFactory first so that
    // plain-String responses (getPageContent) are handled without ambiguity,
    // followed by the JSON converter for everything else.
    private val wikipediaRetrofit by lazy {
        Retrofit.Builder()
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .build()
    }

    private val wikipediaRetrofitService: WikipediaApiService by lazy {
        wikipediaRetrofit.create(WikipediaApiService::class.java)
    }

    override val wikipediaRepository: WikipediaRepository by lazy {
        // Both parameters now use the same unified service — one connection pool,
        // one set of interceptors, one cache.
        NetworkWikipediaRepository(
            wikipediaRetrofitService,
            wikipediaRetrofitService,
            Dispatchers.IO
        )
    }

    override val appPreferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(
            Dispatchers.IO,
            AppDatabase.getDatabase(context).preferenceDao()
        )
    }

    override val appDatabaseRepository: AppDatabaseRepository by lazy {
        AppDatabaseRepository(
            searchHistoryDao = AppDatabase.getDatabase(context).searchHistoryDao(),
            savedArticleDao = AppDatabase.getDatabase(context).savedArticleDao(),
            savedHighlightDao = AppDatabase.getDatabase(context).savedHighlightDao(),
            savedImageDao = AppDatabase.getDatabase(context).savedImageDao(),
            vocabularyDao = AppDatabase.getDatabase(context).vocabularyDao(),
            viewHistoryDao = AppDatabase.getDatabase(context).viewHistoryDao(),
            userLanguageDao = AppDatabase.getDatabase(context).userLanguageDao()
        )
    }

    override val backupManager: BackupManager by lazy {
        BackupManager(context.applicationContext)
    }

    override val localSyncStore: LocalSyncStore by lazy {
        LocalSyncStore(context.applicationContext, appDatabaseRepository)
    }

    override val updateChecker: UpdateChecker by lazy {
        UpdateChecker(
            client = updateHttpClient,
            userAgent = userAgentString,
            context = context.applicationContext
        )
    }

    override val donationBilling: DonationBilling by lazy {
        DonationBilling(context.applicationContext)
    }
}