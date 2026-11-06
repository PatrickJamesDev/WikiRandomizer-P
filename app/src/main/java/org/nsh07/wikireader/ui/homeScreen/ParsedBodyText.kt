package org.nsh07.wikireader.ui.homeScreen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import com.github.tomtung.latex2unicode.LaTeX2Unicode
import kotlinx.coroutines.withTimeoutOrNull
import org.nsh07.wikireader.data.perfMeasure
import org.nsh07.wikireader.parser.HTML_INFOBOX_MARKER
import org.nsh07.wikireader.parser.ReferenceData.infoboxTemplates
import kotlin.text.Typography.nbsp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ParsedBodyText(
    body: List<AnnotatedString>,
    lang: String,
    fontSize: Int,
    fontFamily: FontFamily,
    sharedScope: SharedTransitionScope,
    background: Boolean,
    renderMath: Boolean,
    darkTheme: Boolean,
    dataSaver: Boolean,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit = {},
    onGalleryImageClick: (String, String) -> Unit,
    showRef: (String) -> Unit,
    modifier: Modifier = Modifier,
    checkFirstImage: Boolean = false,
    pageImageUri: String? = null,
    /** Accessibility: a heavier paragraph weight, easier to read for low vision. */
    boldText: Boolean = false,
    /** Texts of saved highlights, tinted when they occur in this body. */
    highlights: List<String> = emptyList(),
    /**
     * Compose a long section in slices over a few frames instead of all at once (laying out dozens
     * of paragraphs in one frame is a visible stall). Only for content that appears at the top of
     * the screen or below the user's thumb: restoring a saved scroll position needs the full height.
     */
    staged: Boolean = false
) {
    org.nsh07.wikireader.data.TrackRecompositions("ParsedBodyText")
    val context = LocalContext.current
    val dpi = LocalDensity.current.density
    val baseBodyStyle = typography.bodyLarge
    val bodyStyle = remember(baseBodyStyle, boldText) {
        baseBodyStyle.copy(
            hyphens = BODY_HYPHENS,
            fontWeight = if (boldText) FontWeight.SemiBold else baseBodyStyle.fontWeight
        )
    }

    val total = body.size
    // rememberSaveable: a section that scrolled out of the list and comes back must not be built
    // again slice by slice (its height would jump while the user scrolls up through it).
    var shown by rememberSaveable(total) {
        mutableStateOf(if (staged && total > STAGE_FIRST + STAGE_STEP) STAGE_FIRST else total)
    }
    if (shown < total) {
        LaunchedEffect(total) {
            while (shown < total) {
                withFrameNanos { }
                shown = minOf(total, shown + STAGE_STEP)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.perfMeasure("section body, ${minOf(shown, total)}/$total blocks")
    ) {
        for (blockIndex in 0 until minOf(shown, total)) {
            val it = body[blockIndex]
            if (it.blockStartsWith("[[File:")) {
                if (!dataSaver) {
                    with(sharedScope) {
                        ImageWithCaption(
                            text = it.toString(),
                            lang = lang,
                            fontSize = fontSize,
                            onLinkClick = onLinkClick,
                            onLinkLongClick = onLinkLongClick,
                            onClick = onGalleryImageClick,
                            darkTheme = darkTheme,
                            background = background,
                            checkFirstImage = checkFirstImage,
                            pageImageUri = pageImageUri
                        )
                    }
                }
            } else if (it.blockStartsWith("<gallery")) {
                if (!dataSaver) {
                    Gallery(
                        text = it.toString(),
                        lang = lang,
                        fontSize = fontSize,
                        onClick = onGalleryImageClick,
                        onLinkClick = onLinkClick,
                        onLinkLongClick = onLinkLongClick,
                        background = background
                    )
                }
            } else if (it.blockStartsWith("<math")) {
                if (renderMath) {
                    EquationImage(
                        context = context,
                        dpi = dpi,
                        latex = remember { it.toString().substringAfter('>') },
                        fontSize = fontSize,
                        darkTheme = darkTheme
                    )
                } else {
                    Text(
                        // The Scala LaTeX converter can throw on malformed formulas: show the source instead
                        text = remember(it) {
                            try {
                                LaTeX2Unicode.convert(it.toString())
                                    .replace(' ', nbsp).substringAfter('>')
                            } catch (e: Throwable) {
                                it.toString().substringAfter('>')
                            }
                        },
                        fontFamily = FontFamily.Serif,
                        fontSize = (fontSize + 4).sp,
                        lineHeight = (24 * (fontSize / 16.0) + 4).toInt().sp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    )
                }
            } else if (it.blockStartsWith("{{clade", ignoreCase = true)) {
                Cladogram(
                    text = it.toString(),
                    fontSize = fontSize
                )
            } else if (it.blockStartsWith(HTML_INFOBOX_MARKER, ignoreCase = true)) {
                // HTML mode: the article's infobox / taxobox ("Scientific classification",
                // "Binomial name", "Synonyms"), converted from the REST HTML table.
                HtmlInfoboxCard(
                    text = it.toString(),
                    lang = lang,
                    fontSize = fontSize,
                    darkTheme = darkTheme,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick
                )
            } else if (it.blockStartsWith("{|")) {
                AsyncWikitable(
                    text = it.toString(),
                    fontSize = fontSize,
                    onLinkClick = onLinkClick,
                    showRef = showRef
                )
            } else if (
                infoboxTemplates.fastAny { item -> it.blockStartsWith(item, ignoreCase = true) }
            ) {
                AsyncInfobox(
                    text = it.toString(),
                    lang = lang,
                    fontSize = fontSize,
                    darkTheme = darkTheme,
                    background = background,
                    onImageClick = onGalleryImageClick,
                    onLinkClick = onLinkClick,
                    showRef = showRef
                )
            } else {
                // The built-in Text link detector only supports taps. Article links are therefore
                // converted to a plain StringAnnotation here, and one gesture handles both a tap
                // (navigate) and a long press (open in a background tab). Reference links keep
                // their LinkAnnotation.Url and are still handled by Text itself.
                val linkColor = MaterialTheme.colorScheme.primary
                val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
                val displayText = remember(it, linkColor, highlights, highlightColor) {
                    it.toTappableArticleLinks(linkColor, highlights, highlightColor)
                }
                var textLayout by remember(displayText) {
                    mutableStateOf<TextLayoutResult?>(null)
                }
                val currentLayout by rememberUpdatedState(textLayout)
                Text(
                    text = displayText,
                    style = bodyStyle,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                    onTextLayout = { textLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .perfMeasure("paragraph (${displayText.length} chars)")
                        .articleLinkGestures(
                            text = displayText,
                            layoutProvider = { currentLayout },
                            onLinkClick = onLinkClick,
                            onLinkLongClick = onLinkLongClick
                        )
                )
            }
        }
    }
}


private const val ARTICLE_LINK_TAG = "wikireader-article-link"

/** How long a link must be held before its article is added to the tabs. */
private const val LINK_HOLD_MILLIS = 500L

/**
 * Tap = navigate, long press = open in a background tab, for text that has been prepared with
 * [toTappableArticleLinks]. Shared by article paragraphs, image captions and gallery captions so
 * every link in an article behaves the same way.
 */
@Composable
internal fun Modifier.articleLinkGestures(
    text: AnnotatedString,
    layoutProvider: () -> TextLayoutResult?,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit
): Modifier {
    val guard = LocalLinkTapGuard.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this.pointerInput(text, guard) {
    // Hold a link for half a second to add its article to the tabs.
    val longPressTimeout = LINK_HOLD_MILLIS
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        // Read the down in the Initial pass: the text selection gesture is attached to the Text
        // itself and runs in the Main pass. Consuming the down here stops it from starting a
        // selection underneath a tap or a long press on a link.
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        // A touch that lands while the article is scrolling (or just stopped) belongs to the
        // scroll: it must stop the fling and must never open a link. Leave the down unconsumed so
        // the list receives it, exactly like a normal Compose clickable would.
        if (guard.blocked()) return@awaitEachGesture
        val layout = layoutProvider() ?: return@awaitEachGesture
        if (text.isEmpty()) return@awaitEachGesture
        val offset = layout.getOffsetForPosition(down.position)
            .coerceIn(0, text.length - 1)
        val tag = text.getStringAnnotations(ARTICLE_LINK_TAG, offset, offset + 1)
            .firstOrNull()?.item
        if (tag.isNullOrBlank()) return@awaitEachGesture

        down.consume()
        var moved = false
        var released = false
        val longPressed = withTimeoutOrNull(longPressTimeout) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull false
                if (!change.pressed) {
                    released = true
                    return@withTimeoutOrNull false
                }
                if ((change.position - down.position).getDistance() > touchSlop) {
                    moved = true
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        } ?: true

        when {
            longPressed -> {
                // Fire as soon as the 500 ms hold expires. Do not wait for ACTION_UP: the tab
                // should be created while the finger is still down, and the tab-added flash can
                // therefore appear immediately under the user's finger.
                haptic.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                onLinkLongClick(tag)

                // Keep ownership of this gesture until release. Otherwise the same held finger
                // could turn into a scroll after the tab has already been opened.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                }
            }

            released && !moved -> onLinkClick(tag)
            // moved: let the scrollable parent keep the drag.
        }
    }
    }
}

/**
 * Decides whether a touch on a link may open it. Links are ignored while the article list is
 * scrolling (including a fling) and for [GRACE_NANOS] after it stops, so a finger that lands on
 * text to stop a fling, or that touches down just as the list settles, never opens a link by
 * accident.
 */
class LinkTapGuard(private val isScrolling: () -> Boolean) {
    @Volatile
    private var lastScrollEndNanos: Long = Long.MIN_VALUE / 4

    /** Called when the list goes from scrolling to idle. */
    fun scrollEnded() {
        lastScrollEndNanos = System.nanoTime()
    }

    fun blocked(): Boolean =
        isScrolling() || System.nanoTime() - lastScrollEndNanos < GRACE_NANOS

    private companion object {
        const val GRACE_NANOS = 250_000_000L
    }
}

/** No guard (never blocks) unless a scrolling screen provides one. */
val LocalLinkTapGuard = staticCompositionLocalOf { LinkTapGuard { false } }

/**
 * Replaces article [LinkAnnotation.Clickable] ranges with a plain [ARTICLE_LINK_TAG] string
 * annotation plus the link color. This gives [ParsedBodyText] full control over tap vs. long press,
 * which the built-in Text link detector does not expose. Reference links and any other annotations
 * are copied through unchanged.
 */
internal fun AnnotatedString.toTappableArticleLinks(
    linkColor: Color,
    highlights: List<String> = emptyList(),
    highlightColor: Color = Color.Unspecified
): AnnotatedString {
    if (isEmpty()) return this
    val links = getLinkAnnotations(0, length)
    val hasTappableLinks = links.any {
        val link = it.item
        (link is LinkAnnotation.Clickable && !link.tag.isNullOrBlank()) ||
                (link is LinkAnnotation.Url && wikiArticleTitle(link.url) != null)
    }
    if (!hasTappableLinks && (highlights.isEmpty() || highlightColor == Color.Unspecified)) {
        return this
    }
    val builder = AnnotatedString.Builder(text)
    spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    paragraphStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    if (highlightColor != Color.Unspecified) {
        highlights.forEach { raw ->
            val needle = raw.trim()
            if (needle.isEmpty()) return@forEach
            var from = 0
            while (from <= length - needle.length) {
                val at = text.indexOf(needle, from, ignoreCase = true)
                if (at < 0) break
                builder.addStyle(
                    SpanStyle(background = highlightColor),
                    at,
                    at + needle.length
                )
                from = at + needle.length.coerceAtLeast(1)
            }
        }
    }
    links.forEach { range ->
        when (val link = range.item) {
            is LinkAnnotation.Clickable -> {
                val tag = link.tag
                if (!tag.isNullOrBlank()) {
                    builder.addStyle(SpanStyle(color = linkColor), range.start, range.end)
                    builder.addStringAnnotation(
                        ARTICLE_LINK_TAG,
                        tag,
                        range.start,
                        range.end
                    )
                } else {
                    builder.addLink(link, range.start, range.end)
                }
            }

            is LinkAnnotation.Url -> {
                // HTML mode turns relative wiki links into absolute wikipedia.org URLs. Those are
                // article links, not external references: route them through the same in-app
                // navigation gesture instead of letting Text hand them to a browser.
                val title = wikiArticleTitle(link.url)
                if (title != null) {
                    builder.addStyle(SpanStyle(color = linkColor), range.start, range.end)
                    builder.addStringAnnotation(ARTICLE_LINK_TAG, title, range.start, range.end)
                } else {
                    builder.addLink(link, range.start, range.end)
                }
            }
            else -> Unit
        }
    }
    return builder.toAnnotatedString()
}

/**
 * Extracts the article title from a Wikipedia URL produced by HTML mode. Returns null for
 * external links, reference anchors and non-article namespaces so those keep their normal
 * LinkAnnotation.Url behaviour.
 */
private fun wikiArticleTitle(url: String): String? {
    if (url.isBlank()) return null
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (!host.endsWith(".wikipedia.org")) return null
    val path = uri.path ?: return null
    if (!path.startsWith("/wiki/")) return null
    val raw = path.removePrefix("/wiki/").substringBefore('#')
    if (raw.isBlank()) return null
    val title = runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw)
        .replace('_', ' ')
        .trim()
    if (title.isEmpty()) return null
    val namespace = title.substringBefore(':', "").lowercase()
    if (namespace in setOf(
            "file", "image", "special", "category", "help", "portal", "talk",
            "template", "wikipedia", "module", "mediawiki", "draft"
        )
    ) return null
    return title
}

/**
 * Same as [AnnotatedString.text] plus `startsWith`, but tolerates the leading newline/space that
 * block splitting can leave on a block. The old exact prefix check sent `"\n{{clade..."` to the
 * ordinary text renderer, where a cladogram is just unreadable template source.
 */
internal fun AnnotatedString.blockStartsWith(prefix: String, ignoreCase: Boolean = false): Boolean {
    val s = text
    var start = 0
    // A handful of whitespace characters at most: never copy or trim the whole paragraph.
    while (start < s.length && start < 8 && s[start].isWhitespace()) start++
    return s.startsWith(prefix, start, ignoreCase)
}

/** Blocks composed in the first frame / added per frame when a long section is built in slices. */
private const val STAGE_FIRST = 8
private const val STAGE_STEP = 4

/**
 * Automatic hyphenation makes text layout noticeably slower (every word is looked up in the
 * hyphenation dictionary while lines are broken), which hurts scrolling on long articles.
 * Set back to [Hyphens.Auto] if you prefer the look over speed.
 */
private val BODY_HYPHENS = Hyphens.None
