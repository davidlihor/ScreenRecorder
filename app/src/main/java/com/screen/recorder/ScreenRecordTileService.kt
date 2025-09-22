package com.screen.recorder

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ScreenRecordTileService : TileService(), CoroutineScope by MainScope() {
    override fun onTileAdded() {
        updateTile(ScreenRecordService.isRecording.value)
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.label = "Screen Recording"
        qsTile.icon = Icon.createWithResource(this, R.drawable.quick_tile_vector)
        qsTile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        startActivityAndCollapse(pendingIntent)
    }


    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob = MainScope().launch {
            ScreenRecordService.isRecording.collect { running ->
                updateTile(running)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    private fun updateTile(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}