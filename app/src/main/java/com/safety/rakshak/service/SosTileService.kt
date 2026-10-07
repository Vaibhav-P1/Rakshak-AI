package com.safety.rakshak.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.safety.rakshak.sos.SosSource

/**
 * Quick Settings tile. A tap goes through the same entry point as every other trigger,
 * so it gets the same 3-second countdown with Cancel and Send now.
 */
class SosTileService : TileService() {

    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        SOSService.trigger(this, SosSource.TILE)
    }
}
