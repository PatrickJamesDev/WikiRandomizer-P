package com.wikirandomizerp.app.ui.image

import android.content.Intent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.DebugLog
import com.wikirandomizerp.app.parser.toWikitextAnnotatedStringSafe

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun SharedTransitionScope.FullScreenImageTopBar(
    description: String,
    link: String? = null,
    onDownload: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val sendIntent: Intent = Intent()
        .apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                link
            )
            type = "text/plain"
        }
    val shareIntent = Intent.createChooser(sendIntent, null)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colorScheme = colorScheme
    val typography = typography

    TopAppBar(
        title = { Text(stringResource(R.string.image)) },
        subtitle = {
            val text = remember(description) {
                description.toWikitextAnnotatedStringSafe(
                    colorScheme = colorScheme,
                    typography = typography,
                    loadPage = {},
                    fontSize = 1,
                    showRef = {}
                ).toString()
            }
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(description),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    zIndexInOverlay = 1f
                )
            )
        },
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            if (onDownload != null) {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onDownload
                ) {
                    Icon(
                        painterResource(R.drawable.download),
                        tint = Color.White,
                        contentDescription = stringResource(R.string.downloadImage)
                    )
                }
            }
            if (link != null) {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { context.startActivity(shareIntent) }) {
                    Icon(
                        painterResource(R.drawable.share_filled),
                        tint = Color.White,
                        contentDescription = stringResource(R.string.shareLink)
                    )
                }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = {
                        // openUri throws for a malformed link or when no app can open it
                        try {
                            uriHandler.openUri(link)
                        } catch (e: Exception) {
                            DebugLog.w("Image", "could not open $link", e)
                        }
                    }) {
                    Icon(
                        painterResource(R.drawable.open_in_browser),
                        tint = Color.White,
                        contentDescription = stringResource(R.string.openInBrowser)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            subtitleContentColor = Color.LightGray,
            navigationIconContentColor = Color.White
        )
    )
}