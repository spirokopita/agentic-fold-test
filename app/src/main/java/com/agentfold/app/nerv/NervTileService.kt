package com.agentfold.app.nerv

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.agentfold.app.NervActivity
import com.agentfold.app.R

class NervTileService : TileService() {

    companion object {

        const val ACTION_OPEN_MAGI = "com.agentfold.app.nerv.ACTION_TILE_OPEN_MAGI"

        const val ACTION_LISTEN = "com.agentfold.app.nerv.ACTION_TILE_LISTEN"

        const val EXTRA_OPEN_SCREEN = "nerv_tile_screen"

        private const val PREFS = "nerv_tile"
        private const val KEY_ARMED = "armed"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun isArmed(context: Context): Boolean =
            prefs(context).getBoolean(KEY_ARMED, false)

        private fun setArmed(context: Context, armed: Boolean) {
            prefs(context).edit().putBoolean(KEY_ARMED, armed).apply()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        setArmed(this, false)
        updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        setArmed(this, false)
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (isArmed(this)) {

            setArmed(this, false)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
            val intent = Intent(this, NervActivity::class.java).apply {
                action = ACTION_LISTEN
                putExtra(NervVoiceWidget.EXTRA_LISTEN, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivityAndCollapse(intent)
        } else {

            setArmed(this, true)
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
            val intent = Intent(this, NervActivity::class.java).apply {
                action = ACTION_OPEN_MAGI
                putExtra(EXTRA_OPEN_SCREEN, "magi")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = "NERV MAGI"
        tile.state = if (isArmed(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = if (isArmed(this)) {
            "NERV MAGI — armed. Tap to talk to MAGI."
        } else {
            "NERV MAGI — tap to open MAGI."
        }
        tile.icon = Icon.createWithResource(this, R.drawable.nerv_tile_icon)
        tile.updateTile()
    }
}
