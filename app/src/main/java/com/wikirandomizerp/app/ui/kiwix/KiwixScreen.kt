package com.wikirandomizerp.app.ui.kiwix

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.kiwix.KiwixArticle
import com.wikirandomizerp.app.data.kiwix.KiwixRepository
import com.wikirandomizerp.app.data.kiwix.KiwixSuggestion
import java.io.ByteArrayInputStream

/** Host used for every ZIM URL. Nothing is ever fetched from the network: it is intercepted. */
private const val KIWIX_HOST = "kiwix.local"

private fun baseUrlFor(path: String): String =
    "https://$KIWIX_HOST/" + path.split('/').joinToString("/") { Uri.encode(it) }

/**
 * Adds the few rules a phone-sized WebView needs on top of Kiwix's desktop-oriented CSS. The page
 * itself (and its pictures, fonts and styles) comes entirely from the ZIM through the interceptor.
 */
private fun decorateHtml(html: String): String {
    val style = """
        <style id="wr-kiwix-style">
          html, body { max-width: 100%; overflow-x: hidden; }
          img, video, svg { max-width: 100% !important; height: auto !important; }
          table { max-width: 100% !important; }
          pre, code { overflow-x: auto; }
          a { overflow-wrap: break-word; }
        </style>
    """.trimIndent()
    val headEnd = html.indexOf("</head>", ignoreCase = true)
    return if (headEnd >= 0) {
        html.substring(0, headEnd) + style + html.substring(headEnd)
    } else {
        style + html
    }
}

/**
 * Reads a Kiwix/ZIM archive in an offline WebView.
 *
 * A WebView is used instead of the native article renderer because a ZIM page is a complete
 * self-contained document: its own CSS, images, fonts, math and cross-article links. All requests
 * for [KIWIX_HOST] are answered from the archive; external requests are blocked, which is both the
 * point of an offline database and a guarantee that reading it does not leak traffic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiwixScreen(
    repository: KiwixRepository,
    startPath: String?,
    random: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by repository.status.collectAsStateWithLifecycle()

    var article by remember { mutableStateOf<KiwixArticle?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<String>() }

    // Remember which page was actually opened. Without this a configuration change (rotation,
    // theme change) would run the "random" branch again and show a different article.
    var openedPath by rememberSaveable(startPath, random) { mutableStateOf("") }

    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KiwixSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    val currentPathRef = rememberUpdatedState(article?.path)

    suspend fun showArticle(result: KiwixArticle?, record: Boolean) {
        if (result == null) {
            error = context.getString(R.string.kiwixNoArticle)
            return
        }
        article = result
        // Remember the page so a configuration change restores it instead of drawing a new
        // random article or jumping back to the start path.
        openedPath = result.path
        if (record && history.lastOrNull() != result.path) history.add(result.path)
    }

    suspend fun loadPath(path: String, record: Boolean) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) { repository.article(path) }
        showArticle(result, record)
        loading = false
    }

    suspend fun loadInitial() {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            when {
                openedPath.isNotBlank() -> repository.article(openedPath)
                random -> repository.randomArticle()
                !startPath.isNullOrBlank() -> repository.article(startPath)
                else -> repository.mainArticle()
            }
        }
        if (result == null) {
            // Nothing from the archive: make sure a stale status is not hiding a real problem.
            withContext(Dispatchers.IO) { runCatching { repository.refresh() } }
        } else if (!status.ready) {
            // The archive opened fine but the start-up refresh had not finished/failed: publish
            // its metadata now so the random and search actions become enabled.
            withContext(Dispatchers.IO) { runCatching { repository.refresh() } }
        }
        history.clear()
        showArticle(result, record = true)
        loading = false
    }

    suspend fun loadRandom() {
        loading = true
        error = null
        showArticle(withContext(Dispatchers.IO) { repository.randomArticle() }, record = true)
        loading = false
    }

    val openPath: (String) -> Unit = { path -> scope.launch { loadPath(path, record = true) } }
    val openPathRef = rememberUpdatedState(openPath)

    LaunchedEffect(retryKey, startPath, random) { loadInitial() }

    // Search the title index with a small debounce. Suggestions are read on the IO dispatcher
    // because libzim is blocking.
    LaunchedEffect(query, showSearch) {
        if (!showSearch) return@LaunchedEffect
        if (query.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(250)
        results = withContext(Dispatchers.IO) { repository.suggestions(query, 24) }
        searching = false
    }

    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            // No network is needed to read a ZIM. External requests are answered with an empty
            // response by the interceptor below; file/content access stays off so a page cannot
            // reach outside the archive either.
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.WHITE)
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (url.host.equals(KIWIX_HOST, ignoreCase = true)) {
                    val path = url.path?.trimStart('/').orEmpty()
                    if (path.isBlank() || path == currentPathRef.value) return false
                    openPathRef.value(path)
                    return true
                }
                if (url.scheme == "http" || url.scheme == "https") {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                    }
                }
                // External resources are never loaded inside an offline archive.
                return true
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                if (url.scheme == "data" || url.scheme == "about" || url.scheme == "blob") {
                    return null
                }
                if (!url.host.equals(KIWIX_HOST, ignoreCase = true)) {
                    return emptyResponse()
                }
                val path = url.path?.trimStart('/').orEmpty()
                if (path.isBlank()) return emptyResponse()
                val resource = repository.resource(path) ?: return emptyResponse()
                val mime = resource.mimeType
                val encoding = if (
                    mime.startsWith("text/", ignoreCase = true) ||
                    mime.contains("json", ignoreCase = true) ||
                    mime.contains("javascript", ignoreCase = true) ||
                    mime.contains("svg", ignoreCase = true)
                ) "utf-8" else null
                return WebResourceResponse(
                    mime,
                    encoding,
                    ByteArrayInputStream(resource.bytes)
                )
            }
        }
        onDispose {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    // In-page navigation keeps a small history: the system back gesture and the toolbar arrow
    // both step back through visited ZIM pages before leaving the reader.
    val goBack: () -> Unit = {
        if (history.size > 1) {
            history.removeAt(history.lastIndex)
            val previous = history.last()
            scope.launch {
                loading = true
                val result = withContext(Dispatchers.IO) { repository.article(previous) }
                if (result != null) article = result
                loading = false
            }
        } else {
            onBack()
        }
    }

    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.title
                            ?: status.title
                            ?: stringResource(R.string.kiwixTitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { loadRandom() } },
                        enabled = !loading && status.ready
                    ) {
                        Icon(
                            painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.kiwixRandom)
                        )
                    }
                    IconButton(
                        onClick = { showSearch = true },
                        enabled = status.ready
                    ) {
                        Icon(
                            painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.kiwixSearchTitle)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    val current = article
                    if (current != null && view.tag != current.path) {
                        view.tag = current.path
                        view.loadDataWithBaseURL(
                            baseUrlFor(current.path),
                            decorateHtml(current.html),
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (article == null && !loading) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val openError = status.error
                    val loadError = error
                    Text(
                        text = if (openError != null) {
                            stringResource(R.string.kiwixOpenFailed, openError)
                        } else {
                            loadError ?: stringResource(R.string.kiwixNoArticle)
                        },
                        style = typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    if (!status.selected) {
                        Text(
                            text = stringResource(R.string.kiwixNoArchive),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(onClick = { retryKey++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            if (article != null && error != null) {
                Surface(
                    color = colorScheme.errorContainer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = error.orEmpty(),
                        style = typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showSearch) {
        Dialog(
            onDismissRequest = { showSearch = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                color = colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.kiwixSearchTitle),
                        style = typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.kiwixSearchHint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (searching) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (!searching && query.length >= 2 && results.isEmpty()) {
                        Text(
                            stringResource(R.string.kiwixSearchEmpty),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(results, key = { it.path }) { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion.title) },
                                supportingContent = {
                                    Text(
                                        suggestion.path,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.clickable {
                                    showSearch = false
                                    query = ""
                                    results = emptyList()
                                    openPath(suggestion.path)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showSearch = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

private fun emptyResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
