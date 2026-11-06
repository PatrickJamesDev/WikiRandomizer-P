package org.nsh07.wikireader.ui.aboutScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nsh07.wikireader.BuildConfig
import org.nsh07.wikireader.R
import org.nsh07.wikireader.WikiRandomizerPApplication
import org.nsh07.wikireader.data.UpdateResult
import org.nsh07.wikireader.data.UpdateState
import org.nsh07.wikireader.ui.settingsScreen.ClickableListItem
import org.nsh07.wikireader.ui.theme.CustomColors.listItemColors
import org.nsh07.wikireader.ui.theme.CustomColors.topBarColors
import org.nsh07.wikireader.ui.theme.WRShapeDefaults.middleListItemShape
import org.nsh07.wikireader.ui.theme.WRShapeDefaults.topListItemShape
import org.nsh07.wikireader.ui.theme.WikiRandomizerPTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val app = context.applicationContext as? WikiRandomizerPApplication
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val availableUpdate by UpdateState.available.collectAsState()
    val checkingUpdate by UpdateState.checking.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val versionLabel = remember {
        when {
            BuildConfig.VERSION_NAME.contains("alpha", ignoreCase = true) -> "αlpha"
            BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true) -> "beta"
            else -> null
        }
    }

    Scaffold(
        topBar = {
            AboutTopAppBar(scrollBehavior = scrollBehavior, onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { insets ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = insets,
            modifier = Modifier
                .background(topBarColors.containerColor)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(topListItemShape)
                        .fillMaxWidth()
                        .background(listItemColors.containerColor)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_launcher_foreground), null)
                        Text(
                            if (versionLabel == null) AnnotatedString(stringResource(R.string.app_name))
                            else buildAnnotatedString {
                                append(stringResource(R.string.app_name))
                                withStyle(
                                    SpanStyle(
                                        fontSize = typography.bodyLarge.fontSize,
                                        fontStyle = FontStyle.Italic,
                                        baselineShift = BaselineShift.Superscript
                                    )
                                ) {
                                    append(" $versionLabel")
                                }
                            },
                            style = typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        stringResource(R.string.appTagline),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.outline_info), null)
                        },
                        headlineContent = { Text(stringResource(R.string.version)) },
                        supportingContent = { Text(BuildConfig.VERSION_NAME) },
                        colors = listItemColors,
                        modifier = Modifier.clip(middleListItemShape)
                    )
                    ClickableListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.update), null)
                        },
                        headlineContent = {
                            Text(
                                availableUpdate?.let {
                                    stringResource(R.string.updateAvailable, it.version)
                                } ?: stringResource(R.string.checkForUpdates)
                            )
                        },
                        supportingContent = {
                            Text(
                                when {
                                    checkingUpdate -> stringResource(R.string.checkingForUpdates)
                                    availableUpdate != null -> stringResource(R.string.updateAvailableDesc)
                                    else -> stringResource(R.string.checkForUpdatesDesc)
                                }
                            )
                        },
                        trailingContent = if (checkingUpdate) {
                            {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else null,
                        colors = listItemColors,
                        items = 6,
                        index = 1
                    ) {
                        val info = availableUpdate
                        if (info != null) {
                            showUpdateDialog = true
                        } else if (!checkingUpdate) {
                            scope.launch {
                                val application = app
                                if (application == null) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.updateFailed)
                                    )
                                    return@launch
                                }
                                UpdateState.checking.value = true
                                when (
                                    val result = application.container.updateChecker
                                        .checkForUpdate(BuildConfig.VERSION_NAME)
                                ) {
                                    is UpdateResult.Available -> {
                                        UpdateState.available.value = result.info
                                        showUpdateDialog = true
                                    }

                                    is UpdateResult.UpToDate -> snackbarHostState.showSnackbar(
                                        context.getString(R.string.upToDate)
                                    )

                                    is UpdateResult.Error -> snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.updateCheckFailed,
                                            result.message
                                        )
                                    )
                                }
                                UpdateState.checking.value = false
                            }
                        }
                    }
                    ClickableListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.code), null)
                        },
                        headlineContent = { Text(stringResource(R.string.sourceCode)) },
                        supportingContent = { Text("github.com/PatrickJamesDev/WikiRandomizer-P") },
                        colors = listItemColors,
                        items = 6,
                        index = 2
                    ) { uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P") }
                    ClickableListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.gavel), null)
                        },
                        headlineContent = { Text(stringResource(R.string.license)) },
                        supportingContent = { Text("GPL v3.0") },
                        colors = listItemColors,
                        items = 6,
                        index = 3
                    ) { uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P/blob/main/LICENSE") }
                    ClickableListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.update), null)
                        },
                        headlineContent = { Text(stringResource(R.string.releases)) },
                        supportingContent = { Text(stringResource(R.string.releasesDesc)) },
                        colors = listItemColors,
                        items = 6,
                        index = 4
                    ) { uriHandler.openUri("https://github.com/PatrickJamesDev/WikiRandomizer-P/releases") }
                    ClickableListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.translate), null)
                        },
                        headlineContent = { Text(stringResource(R.string.translate)) },
                        supportingContent = { Text(stringResource(R.string.translateDesc)) },
                        colors = listItemColors,
                        items = 6,
                        index = 5
                    ) { uriHandler.openUri("https://hosted.weblate.org/engage/wikireader/") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.author),
                        style = typography.labelLarge,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                    )
                    ClickableListItem(
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.github), null
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.authorName)) },
                        supportingContent = { Text(stringResource(R.string.otherProjectsDesc)) },
                        colors = listItemColors,
                        items = 2,
                        index = 0
                    ) { uriHandler.openUri("https://github.com/PatrickJamesDev") }
                    ClickableListItem(
                        leadingContent = {
                            Icon(painterResource(R.drawable.heart), null)
                        },
                        headlineContent = { Text(stringResource(R.string.donate)) },
                        supportingContent = { Text(stringResource(R.string.donateDesc)) },
                        colors = listItemColors,
                        items = 2,
                        index = 1
                    ) { uriHandler.openUri("https://github.com/PatrickJamesDev") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.wikipedia),
                        style = typography.labelLarge,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                    )
                    ClickableListItem(
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.wikipedia_s_w), null
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.wikipedia)) },
                        supportingContent = { Text(stringResource(R.string.wikipediaWebsiteDesc)) },
                        colors = listItemColors,
                        items = 2,
                        index = 0
                    ) { uriHandler.openUri("https://wikipedia.org") }
                    ClickableListItem(
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.wikimedia_logo_black), null
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.supportWikipedia)) },
                        supportingContent = { Text(stringResource(R.string.donateToWikipedia)) },
                        colors = listItemColors,
                        items = 2,
                        index = 1
                    ) { uriHandler.openUri("https://wikimediafoundation.org/support/") }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    val updateInfo = availableUpdate
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            info = updateInfo,
            onDismiss = { showUpdateDialog = false },
            onOpenReleasePage = {
                uriHandler.openUri(updateInfo.releaseUrl)
            }
        )
    }
}

@Preview
@Composable
fun AboutScreenPreview() {
    WikiRandomizerPTheme(darkTheme = true) {
        AboutScreen {}
    }
}
