package org.nsh07.wikireader.ui.homeScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nsh07.wikireader.R

/**
 * A tiny replacement for the system text-selection toolbar. The platform toolbar has no way to
 * tell the app *what* was selected (it only exposes copy/paste callbacks), so "Highlight" first
 * asks the editor to copy the selection and then reads the clipboard once the copy has landed.
 * The clipboard round-trip is local, happens only on an explicit tap, and is far cheaper than
 * rebuilding the article around a custom selection engine.
 */
class HighlightTextToolbar : TextToolbar {
    data class Menu(
        val rect: Rect,
        val copySelection: () -> Unit,
        val selectAllSelection: () -> Unit
    )

    var menu by mutableStateOf<Menu?>(null)
        private set

    private var currentStatus = TextToolbarStatus.Hidden

    override val status: TextToolbarStatus
        get() = currentStatus

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menu = Menu(
            rect = rect,
            copySelection = { onCopyRequested?.invoke() },
            selectAllSelection = { onSelectAllRequested?.invoke() }
        )
        currentStatus = TextToolbarStatus.Shown
    }

    override fun hide() {
        menu = null
        currentStatus = TextToolbarStatus.Hidden
    }

    fun dismiss() = hide()
}

/**
 * Wraps a [androidx.compose.foundation.text.selection.SelectionContainer] and provides the
 * highlight/search actions for the current selection. The popup is positioned from the selection
 * rectangle reported by Compose, so it appears right above the selected words.
 */
@Composable
fun HighlightSelectionContainer(
    onSaveHighlight: (String) -> Unit,
    onSearchInBrowser: (String) -> Unit,
    onDefineWord: (String) -> Unit = {},
    onAddVocabulary: (String) -> Unit = {},
    content: @Composable () -> Unit
) {
    val toolbar = remember { HighlightTextToolbar() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val menu = toolbar.menu

    fun selectedText(onReady: (String) -> Unit) {
        // The copy callback is synchronous for Compose's Android clipboard implementation, but a
        // single frame of slack makes this reliable on slower devices and in tests.
        scope.launch {
            delay(120)
            val text = runCatching { clipboard.getText()?.text }.getOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) onReady(text)
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()

        if (menu != null) {
            Popup(
                popupPositionProvider = SelectionPopupPositionProvider(menu.rect),
                onDismissRequest = { toolbar.dismiss() },
                // Non-focusable: a focusable popup steals the selection when the user tries to
                // drag a handle further (multi-sentence selection). The menu now floats above the
                // selection and lets the handle keep moving; clearing the selection hides it.
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        HighlightAction(
                            icon = R.drawable.translate,
                            label = stringResource(R.string.defineWord)
                        ) {
                            menu.copySelection()
                            toolbar.dismiss()
                            selectedText(onDefineWord)
                        }
                        HighlightAction(
                            icon = R.drawable.add,
                            label = stringResource(R.string.addToVocabulary)
                        ) {
                            menu.copySelection()
                            toolbar.dismiss()
                            selectedText(onAddVocabulary)
                        }
                        HighlightAction(
                            icon = R.drawable.highlight,
                            label = stringResource(R.string.highlight)
                        ) {
                            menu.copySelection()
                            toolbar.dismiss()
                            selectedText(onSaveHighlight)
                        }
                        HighlightAction(
                            icon = R.drawable.search,
                            label = stringResource(R.string.searchHighlightInBrowser)
                        ) {
                            menu.copySelection()
                            toolbar.dismiss()
                            selectedText(onSearchInBrowser)
                        }
                        HighlightAction(
                            icon = R.drawable.copy,
                            label = stringResource(R.string.copy)
                        ) {
                            menu.copySelection()
                            toolbar.dismiss()
                        }
                        HighlightAction(
                            icon = R.drawable.expand_all,
                            label = stringResource(R.string.selectAll)
                        ) {
                            menu.selectAllSelection()
                            toolbar.dismiss()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row of the highlight menu. A plain clickable row (instead of a TextButton) gives the whole
 * width and height of the row a tap target, not just the icon/text glyphs.
 */
@Composable
private fun HighlightAction(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(label)
    }
}

private class SelectionPopupPositionProvider(
    private val selectionRect: Rect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = selectionRect.left.toInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        // Prefer the space above the selection: the lower handle is usually the one being dragged,
        // and a menu below it visually covers the words the user is still extending the range over.
        val above = selectionRect.top.toInt() - popupContentSize.height - 8
        val below = selectionRect.bottom.toInt() + 8
        val y = if (above >= 0) above
        else below.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
