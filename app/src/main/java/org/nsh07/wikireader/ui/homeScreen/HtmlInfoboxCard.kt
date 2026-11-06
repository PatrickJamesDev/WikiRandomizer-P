package org.nsh07.wikireader.ui.homeScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.DebugLog
import org.nsh07.wikireader.parser.HtmlInfobox
import org.nsh07.wikireader.parser.HtmlInfoboxRow
import org.nsh07.wikireader.parser.absolutizeHtmlLinks
import org.nsh07.wikireader.parser.parseHtmlInfoboxBlock
import org.nsh07.wikireader.parser.plainText
import org.nsh07.wikireader.ui.theme.CustomColors.cardColors

/**
 * The infobox of an article in HTML mode. For plants, animals and fungi this is the taxobox:
 * "Scientific classification" (Kingdom, Clade, Order, Family, Genus, Species), "Binomial name"
 * and "Synonyms", drawn with the same coloured header bands as on the website. Links in the
 * cells open the linked article (long press: in a background tab), like links in the text.
 *
 * A taxobox starts expanded because it is the point of a species article; other infoboxes start
 * collapsed, like the wikitext reader's infobox card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HtmlInfoboxCard(
    text: String,
    lang: String,
    fontSize: Int,
    darkTheme: Boolean,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val box: HtmlInfobox? = remember(text) {
        try {
            parseHtmlInfoboxBlock(text)
        } catch (e: Throwable) {
            DebugLog.w("Infobox", "could not read an HTML infobox", e)
            null
        }
    }
    if (box == null) return

    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val titleText = remember(box) { plainText(box.titleHtml) }
    var expanded by rememberSaveable { mutableStateOf(box.taxobox) }

    Card(
        colors = cardColors,
        shape = shapes.largeIncreased,
        modifier = modifier
            .widthIn(max = 512.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                leadingContent = {
                    FilledTonalIconButton(
                        onClick = { expanded = !expanded },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painterResource(
                                if (expanded) R.drawable.keyboard_arrow_up
                                else R.drawable.keyboard_arrow_down
                            ),
                            contentDescription = stringResource(R.string.expand_section)
                        )
                    }
                },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.infobox), fontWeight = FontWeight.Medium)
                        if (!expanded && titleText.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                titleText,
                                color = colors.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
                modifier = Modifier
                    .clip(shapes.largeIncreased)
                    .padding(vertical = 8.dp)
                    .clickable(onClick = { expanded = !expanded })
            )

            AnimatedVisibility(
                expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (box.titleHtml.isNotEmpty()) {
                        InfoboxBand(
                            html = box.titleHtml,
                            rgb = box.titleColor,
                            title = true,
                            lang = lang,
                            fontSize = fontSize,
                            darkTheme = darkTheme,
                            onLinkClick = onLinkClick,
                            onLinkLongClick = onLinkLongClick
                        )
                    }
                    for (row in box.rows) {
                        when (row) {
                            is HtmlInfoboxRow.Header -> InfoboxBand(
                                html = row.html,
                                rgb = row.color,
                                title = false,
                                lang = lang,
                                fontSize = fontSize,
                                darkTheme = darkTheme,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = onLinkLongClick
                            )

                            is HtmlInfoboxRow.Field -> Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                InfoboxCellText(
                                    html = row.labelHtml,
                                    lang = lang,
                                    fontSize = fontSize,
                                    color = colors.onSurfaceVariant,
                                    fontWeight = null,
                                    textAlign = TextAlign.Start,
                                    onLinkClick = onLinkClick,
                                    onLinkLongClick = onLinkLongClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                InfoboxCellText(
                                    html = row.valueHtml,
                                    lang = lang,
                                    fontSize = fontSize,
                                    color = Color.Unspecified,
                                    fontWeight = null,
                                    textAlign = TextAlign.Start,
                                    onLinkClick = onLinkClick,
                                    onLinkLongClick = onLinkLongClick,
                                    modifier = Modifier.weight(1.6f)
                                )
                            }

                            is HtmlInfoboxRow.Wide -> InfoboxCellText(
                                html = row.html,
                                lang = lang,
                                fontSize = fontSize,
                                color = Color.Unspecified,
                                fontWeight = null,
                                textAlign = TextAlign.Center,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = onLinkLongClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )

                            is HtmlInfoboxRow.Item -> Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    "\u2022",
                                    fontSize = fontSize.sp,
                                    lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                InfoboxCellText(
                                    html = row.html,
                                    lang = lang,
                                    fontSize = fontSize,
                                    color = Color.Unspecified,
                                    fontWeight = null,
                                    textAlign = TextAlign.Start,
                                    onLinkClick = onLinkClick,
                                    onLinkLongClick = onLinkLongClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalIconButton(
                        onClick = { expanded = false },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .width(52.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.keyboard_arrow_up),
                            contentDescription = stringResource(R.string.collapse_section)
                        )
                    }
                }
            }
        }
    }
}

/** A coloured full-width band: the name at the top, "Scientific classification", "Synonyms", … */
@Composable
private fun InfoboxBand(
    html: String,
    rgb: Int?,
    title: Boolean,
    lang: String,
    fontSize: Int,
    darkTheme: Boolean,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bandColors = remember(rgb, darkTheme, scheme) { infoboxBandColors(rgb, darkTheme, scheme) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(bandColors.first)
            .padding(horizontal = 12.dp, vertical = if (title) 10.dp else 6.dp)
    ) {
        InfoboxCellText(
            html = html,
            lang = lang,
            fontSize = if (title) fontSize + 2 else fontSize,
            color = bandColors.second,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick
        )
    }
}

/**
 * Band background and the readable text colour on it. The website colours (light green for
 * plants, pink for animals, …) are made dark in the dark theme so the page does not glare.
 */
private fun infoboxBandColors(rgb: Int?, darkTheme: Boolean, scheme: ColorScheme): Pair<Color, Color> {
    if (rgb == null) return Pair(scheme.secondaryContainer, scheme.onSecondaryContainer)
    var band = Color(red = (rgb shr 16) and 0xFF, green = (rgb shr 8) and 0xFF, blue = rgb and 0xFF)
    if (darkTheme) band = lerp(band, Color.Black, 0.62f)
    val onBand = if (band.luminance() > 0.5f) Color.Black else Color.White
    return Pair(band, onBand)
}

/** Cell markup → text with tappable article links. Never throws. */
private fun infoboxCellText(html: String, lang: String, linkColor: Color): AnnotatedString =
    try {
        AnnotatedString.fromHtml(absolutizeHtmlLinks(html, lang)).toTappableArticleLinks(linkColor)
    } catch (e: Throwable) {
        AnnotatedString(plainText(html))
    }

@Composable
private fun InfoboxCellText(
    html: String,
    lang: String,
    fontSize: Int,
    color: Color,
    fontWeight: FontWeight?,
    textAlign: TextAlign,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val display = remember(html, lang, linkColor) { infoboxCellText(html, lang, linkColor) }
    var textLayout by remember(display) { mutableStateOf<TextLayoutResult?>(null) }
    val currentLayout by rememberUpdatedState(textLayout)
    Text(
        text = display,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
        textAlign = textAlign,
        onTextLayout = { textLayout = it },
        modifier = modifier.articleLinkGestures(
            text = display,
            layoutProvider = { currentLayout },
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick
        )
    )
}
