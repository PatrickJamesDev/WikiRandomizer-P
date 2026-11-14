package com.wikirandomizerp.app.widget

import android.content.Intent
import android.service.quicksettings.TileService
import com.wikirandomizerp.app.MainActivity
import com.wikirandomizerp.app.data.AppLaunchExtras

/**
 * Quick Settings entry point for the same "random from my topics" action as the home-screen
 * widget. The activity resolves topics after its library is initialized, so the tile never needs
 * to duplicate that work.
 */
class RandomArticleTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = AppLaunchExtras.ACTION_RANDOM_FROM_TOPICS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivityAndCollapse(intent)
    }
}
