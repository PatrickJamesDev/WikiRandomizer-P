package com.wikirandomizerp.app.ui.homeScreen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wikirandomizerp.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/*
 * iNaturalist support for species articles:
 *  - a "where should this open?" choice (iNaturalist app or browser), remembered on request;
 *  - a gallery of community photos of the species, shown below the Wikimedia Commons gallery.
 */

// ---- Where links open -----------------------------------------------------------------------

enum class INatOpenMode { ASK, APP, BROWSER }

/** The remembered answer to "open iNaturalist links in the app or the browser?". */
object INatPrefs {
    private const val FILE = "inaturalist_prefs"
    private const val KEY_MODE = "open_mode"

    fun mode(context: Context): INatOpenMode {
        val raw = runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        }.getOrNull()
        return INatOpenMode.entries.firstOrNull { it.name == raw } ?: INatOpenMode.ASK
    }

    fun setMode(context: Context, mode: INatOpenMode) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode.name).apply()
        }
    }
}

/** True when the official iNaturalist app is installed (needs the <queries> entry in the manifest). */
fun isINaturalistInstalled(context: Context): Boolean =
    runCatching { context.packageManager.getPackageInfo(INATURALIST_PACKAGE, 0) }.isSuccess

/**
 * Opens [url] in the iNaturalist app ([inApp] = true) or in the web browser. When the app cannot
 * take the link it falls back to the browser and says so.
 */
fun openINaturalistUrl(context: Context, url: String, inApp: Boolean) {
    val uri = Uri.parse(url)
    if (inApp) {
        val appIntent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(INATURALIST_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(appIntent) }.isSuccess) return
        runCatching {
            Toast.makeText(context, R.string.inat_app_fallback_toast, Toast.LENGTH_LONG).show()
        }
    }
    // The selector limits the intent to web browsers, so apps that claim inaturalist.org links
    // (the iNaturalist app itself) cannot intercept a "browser" choice.
    val browser = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    browser.selector = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse("http://"))
    if (runCatching { context.startActivity(browser) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Opens iNaturalist links the way the reader chose: straight away when a choice is remembered,
 * otherwise through a dialog. Call [Dialogs] once in the composition that uses it.
 */
class INaturalistOpener(private val context: Context) {
    private var pendingUrl by mutableStateOf<String?>(null)
    private var showPrefs by mutableStateOf(false)

    fun open(url: String) {
        when (INatPrefs.mode(context)) {
            INatOpenMode.ASK -> pendingUrl = url
            INatOpenMode.APP -> openINaturalistUrl(context, url, inApp = true)
            INatOpenMode.BROWSER -> openINaturalistUrl(context, url, inApp = false)
        }
    }

    /** Lets the reader change the remembered choice. */
    fun choosePreference() {
        showPrefs = true
    }

    @Composable
    fun Dialogs() {
        pendingUrl?.let { url ->
            INaturalistOpenDialog(
                onDismiss = { pendingUrl = null },
                onChosen = { inApp, rememberChoice ->
                    if (rememberChoice) {
                        INatPrefs.setMode(
                            context,
                            if (inApp) INatOpenMode.APP else INatOpenMode.BROWSER
                        )
                    }
                    pendingUrl = null
                    openINaturalistUrl(context, url, inApp)
                }
            )
        }
        if (showPrefs) {
            INatPrefsDialog(onDismiss = { showPrefs = false })
        }
    }
}

@Composable
fun rememberINaturalistOpener(): INaturalistOpener {
    val context = LocalContext.current
    return remember(context) { INaturalistOpener(context) }
}

@Composable
private fun ChoiceRow(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = (if (selected) "\u2713  " else "") + label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

@Composable
private fun INaturalistOpenDialog(
    onDismiss: () -> Unit,
    onChosen: (inApp: Boolean, rememberChoice: Boolean) -> Unit
) {
    val context = LocalContext.current
    val installed = remember { isINaturalistInstalled(context) }
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inat_open_title)) },
        text = {
            Column {
                ChoiceRow(
                    label = stringResource(
                        if (installed) R.string.inat_open_app else R.string.inat_open_app_missing
                    ),
                    enabled = installed
                ) { onChosen(true, rememberChoice) }
                ChoiceRow(label = stringResource(R.string.inat_open_browser)) {
                    onChosen(false, rememberChoice)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(top = 8.dp)
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text(
                        text = stringResource(R.string.inat_open_remember),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.inat_close)) } }
    )
}

@Composable
private fun INatPrefsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { isINaturalistInstalled(context) }
    var mode by remember { mutableStateOf(INatPrefs.mode(context)) }
    fun pick(value: INatOpenMode) {
        INatPrefs.setMode(context, value)
        mode = value
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inat_open_prefs_title)) },
        text = {
            Column {
                ChoiceRow(
                    label = stringResource(R.string.inat_open_ask),
                    selected = mode == INatOpenMode.ASK
                ) { pick(INatOpenMode.ASK) }
                ChoiceRow(
                    label = stringResource(
                        if (installed) R.string.inat_open_app else R.string.inat_open_app_missing
                    ),
                    enabled = installed || mode == INatOpenMode.APP,
                    selected = mode == INatOpenMode.APP
                ) { pick(INatOpenMode.APP) }
                ChoiceRow(
                    label = stringResource(R.string.inat_open_browser),
                    selected = mode == INatOpenMode.BROWSER
                ) { pick(INatOpenMode.BROWSER) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.inat_close)) } }
    )
}

// ---- Observations gallery -------------------------------------------------------------------

/** One iNaturalist observation with the photo shown for it. */
data class INatObservation(
    val id: Long,
    val thumbUrl: String,
    val largeUrl: String,
    val observationUrl: String,
    val observer: String,
    val observedOn: String?,
    val place: String?,
    val attribution: String?,
    val width: Int,
    val height: Int
)

data class INatGallery(val taxonId: Long, val observations: List<INatObservation>)

fun inaturalistObservationsUrl(taxonId: Long): String =
    "https://www.inaturalist.org/observations?taxon_id=$taxonId&photos=true&quality_grade=research"

/**
 * Fetches research-grade, openly licensed photo observations of a taxon from the public
 * iNaturalist API (most community-endorsed first). Results are cached for the session.
 */
object INaturalistObservations {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<Long, List<INatObservation>>()

    suspend fun fetch(taxonId: Long): List<INatObservation> = withContext(Dispatchers.IO) {
        cache[taxonId]?.let { return@withContext it }
        val url = "https://api.inaturalist.org/v1/observations".toHttpUrl()
            .newBuilder()
            .addQueryParameter("taxon_id", taxonId.toString())
            .addQueryParameter("quality_grade", "research")
            .addQueryParameter("photos", "true")
            .addQueryParameter("photo_license", "cc0,cc-by,cc-by-sa,cc-by-nc,cc-by-nc-sa")
            .addQueryParameter("order_by", "votes")
            .addQueryParameter("per_page", "30")
            .addQueryParameter("spam", "false")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WikiRandomizer-P (iNaturalist gallery)")
            .build()
        val parsed: List<INatObservation>? = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()?.let { parse(it) }
        }
        if (parsed != null) cache[taxonId] = parsed
        parsed ?: emptyList()
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun sized(url: String, size: String): String = url.replace("/square.", "/$size.")

    private fun parse(body: String): List<INatObservation> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<INatObservation>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            if (id <= 0L) continue
            val photo = o.optJSONArray("photos")?.optJSONObject(0) ?: continue
            val square = photo.text("url") ?: continue
            val dims = photo.optJSONObject("original_dimensions")
            out += INatObservation(
                id = id,
                thumbUrl = sized(square, "medium"),
                largeUrl = sized(square, "large"),
                observationUrl = "https://www.inaturalist.org/observations/$id",
                observer = o.optJSONObject("user")?.text("login").orEmpty(),
                observedOn = o.text("observed_on"),
                place = o.text("place_guess"),
                attribution = photo.text("attribution"),
                width = dims?.optInt("width", 0) ?: 0,
                height = dims?.optInt("height", 0) ?: 0
            )
        }
        return out
    }
}

/**
 * Loads the iNaturalist photo gallery of the species the article [title] is about, in the
 * background. Returns null while loading, when the species is not found, or when it has no
 * openly licensed photos, so the page simply shows no gallery in those cases.
 */
@Composable
fun rememberINaturalistGallery(title: String, enabled: Boolean): INatGallery? {
    var gallery by remember(title) { mutableStateOf<INatGallery?>(null) }
    LaunchedEffect(title, enabled) {
        if (enabled && gallery == null && title.isNotBlank()) {
            try {
                val id = INaturalistTaxa.resolveId(title)
                if (id != null) {
                    val observations = INaturalistObservations.fetch(id)
                    if (observations.isNotEmpty()) gallery = INatGallery(id, observations)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No network or API error: the page just shows no iNaturalist gallery.
            }
        }
    }
    return if (enabled) gallery else null
}

/**
 * A row of community photos of the species, shown right below the Wikimedia Commons gallery.
 * Tapping a photo shows its details with an action that opens that exact observation in
 * iNaturalist (app or browser, as the reader chose).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun INaturalistGalleryStrip(
    gallery: INatGallery,
    opener: INaturalistOpener,
    modifier: Modifier = Modifier,
    /**
     * When provided, tapping a photo opens the in-app full-screen gallery at that position instead
     * of redirecting to iNaturalist. The observation page stays one tap away in the viewer.
     */
    onOpenImage: ((List<INatObservation>, Int) -> Unit)? = null
) {
    var selected by remember(gallery.taxonId) { mutableStateOf<INatObservation?>(null) }
    selected?.let { obs ->
        INatObservationDialog(
            obs = obs,
            onOpen = {
                selected = null
                opener.open(obs.observationUrl)
            },
            onDismiss = { selected = null }
        )
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.inat_gallery_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.inat_gallery_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(gallery.observations, key = { _, obs -> obs.id }) { index, obs ->
                INatThumb(obs) {
                    if (onOpenImage != null) {
                        onOpenImage(gallery.observations, index)
                    } else {
                        selected = obs
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            TextButton(onClick = { opener.open(inaturalistObservationsUrl(gallery.taxonId)) }) {
                Text(stringResource(R.string.inat_gallery_view_all))
            }
            TextButton(onClick = { opener.choosePreference() }) {
                Text(stringResource(R.string.inat_gallery_where))
            }
        }
    }
}

@Composable
private fun INatThumb(obs: INatObservation, onClick: () -> Unit) {
    val context = LocalContext.current
    val height = 160.dp
    val ratio = if (obs.height > 0) (obs.width.toFloat() / obs.height).coerceIn(0.6f, 2f) else 1f
    Box(
        modifier = Modifier
            .height(height)
            .width(height * ratio)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(obs.thumbUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.inat_observed_by, obs.observer),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(height)
                .width(height * ratio)
        )
    }
}

@Composable
private fun INatObservationDialog(
    obs: INatObservation,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(obs.largeUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.inat_observed_by, obs.observer),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(12.dp))
                if (obs.observer.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.inat_observed_by, obs.observer),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                listOfNotNull(obs.observedOn, obs.place).takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it.joinToString(" \u00B7 "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                obs.attribution?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inat_open_observation))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.inat_close)) } }
    )
}
