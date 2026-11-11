package com.wikirandomizerp.app.ui.historyScreen.viewModel

import com.wikirandomizerp.app.data.ViewHistoryItem

sealed class HistoryAction {
    data class InsertItem(val item: ViewHistoryItem) : HistoryAction()
    data class RemoveItem(val item: ViewHistoryItem) : HistoryAction()
    object RemoveAll : HistoryAction()
}