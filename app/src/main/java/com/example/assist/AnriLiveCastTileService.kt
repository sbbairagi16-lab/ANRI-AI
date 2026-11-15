package com.example.assist

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.service.ScreenCaptureHolder

@RequiresApi(Build.VERSION_CODES.N)
class AnriLiveCastTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    val tile = qsTile ?: return
    val isCasting = ScreenCaptureHolder.isSharing.value
    tile.state = if (isCasting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    tile.label = if (isCasting) "ANRI Live On" else "ANRI Live Cast"
    tile.updateTile()
  }

  override fun onClick() {
    super.onClick()
    val intent =
      Intent(this, AnriAssistOverlayActivity::class.java).apply {
        action = AnriAssistOverlayActivity.ACTION_POWER_LIVE_CAST
        putExtra(AnriAssistOverlayActivity.EXTRA_AUTO_CAST, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val pi =
        PendingIntent.getActivity(
          this,
          104,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      startActivityAndCollapse(pi)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(intent)
    }
  }
}
