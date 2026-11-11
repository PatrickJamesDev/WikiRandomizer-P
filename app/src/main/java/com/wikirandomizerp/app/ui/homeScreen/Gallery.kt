package com.wikirandomizerp.app.ui.homeScreen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.wikirandomizerp.app.ui.image.ImageDownloadMenu
import com.wikirandomizerp.app.ui.image.rememberImageMenuState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.parser.toWikitextAnnotatedStringSafe
import com.wikirandomizerp.app.ui.image.FeedImage
import com.wikirandomizerp.app.ui.image.ImagePrefetcher

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun Gallery(
    text: String,
    lang: String,
    fontSize: Int,
    background: Boolean,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String) -> Unit = {},
    onClick: (String, String) -> Unit
) {
    val content = remember(text) { text.substringAfter('>').trim(' ', '\n').lines() }
    val pagerState = rememberPagerState { content.size }
    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        val fileName = remember(text) { content[it].substringBefore('|').substringAfter(':').trim() }
        val uriLow = remember(text) { ImagePrefetcher.fileUrl(lang, fileName) }
        val uriHigh = remember(text) {
            ImagePrefetcher.fileUrl(lang, fileName, width = 1920)
        }
            val description = remember(text) { content[it].substringAfter('|', "") }
            val captionColorScheme = colorScheme
            val captionTypography = typography
            val caption = remember(description, fontSize, captionColorScheme, captionTypography) {
                description.toWikitextAnnotatedStringSafe(
                    colorScheme = captionColorScheme,
                    fontSize = fontSize - 2,
                    loadPage = onLinkClick,
                    typography = captionTypography,
                    showRef = {}
                )
            }
            val captionDisplay = remember(caption, captionColorScheme.primary) {
                caption.toTappableArticleLinks(captionColorScheme.primary)
            }
            val captionLayout = remember(captionDisplay) {
                androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
            }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            val menu = rememberImageMenuState()
            val haptic = LocalHapticFeedback.current
            Box {
                FeedImage(
                    source = uriLow,
                    description = description,
                    loadingIndicator = false,
                    lightPlaceholder = true,
                    background = background,
                    modifier = Modifier
                        .clip(shapes.large)
                        .combinedClickable(
                            onClick = { onClick(uriHigh, description) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu.open = true
                            }
                        )
                )
                ImageDownloadMenu(menu, uriHigh, description.ifBlank { null })
            }
            Text(
                text = captionDisplay,
                fontSize = (fontSize - 2).sp,
                lineHeight = (24 * ((fontSize - 2) / 16.0)).toInt().sp,
                textAlign = TextAlign.Center,
                color = colorScheme.onSurfaceVariant,
                onTextLayout = { captionLayout.value = it },
                modifier = Modifier
                    .padding(8.dp)
                    .articleLinkGestures(
                        text = captionDisplay,
                        layoutProvider = { captionLayout.value },
                        onLinkClick = onLinkClick,
                        onLinkLongClick = onLinkLongClick
                    )
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage - 1
                        )
                    }
                },
                enabled = pagerState.currentPage != 0
            ) {
                Icon(
                    painterResource(R.drawable.keyboard_arrow_left),
                    contentDescription = stringResource(R.string.scrollLeft)
                )
            }
            Text(
                stringResource(
                    R.string.imageCounter,
                    pagerState.currentPage + 1,
                    pagerState.pageCount
                )
            )
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1
                        )
                    }
                },
                enabled = pagerState.currentPage != pagerState.pageCount - 1
            ) {
                Icon(
                    painterResource(R.drawable.keyboard_arrow_right),
                    contentDescription = stringResource(R.string.scrollRight)
                )
            }
        }
    }
}