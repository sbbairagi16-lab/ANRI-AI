package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class ScreenShareService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      ScreenCaptureHolder.stopSession()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    ensureNotificationChannel()
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
      ?: Activity.RESULT_CANCELED
    @Suppress("DEPRECATION")
    val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

    if (resultCode == Activity.RESULT_OK && resultData != null) {
      val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val projection = manager.getMediaProjection(resultCode, resultData)
      if (projection != null) {
        val metrics = resources.displayMetrics
        val targetW = 720
        val targetH = ((metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()) * targetW)
          .toInt()
          .coerceIn(640, 1600)
        ScreenCaptureHolder.startSession(projection, targetW, targetH, metrics.densityDpi)
      } else {
        stopSelf()
      }
    } else {
      stopSelf()
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    ScreenCaptureHolder.stopSession()
    super.onDestroy()
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "ANRI AI Screen Sharing",
          NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Active while you share your screen with ANRI AI." }
      val nm = getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, ScreenShareService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle("ANRI AI Screen Share Active")
      .setContentText("Tap Stop to end screen sharing anytime.")
      .setOngoing(true)
      .setContentIntent(openIntent)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopIntent)
      .build()
  }

  companion object {
    const val CHANNEL_ID = "anri_screen_share"
    const val NOTIFICATION_ID = 4102
    const val ACTION_START = "com.example.action.START_SCREEN_SHARE"
    const val ACTION_STOP = "com.example.action.STOP_SCREEN_SHARE"
    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_RESULT_DATA = "result_data"

    fun start(context: Context, resultCode: Int, data: Intent) {
      val intent =
        Intent(context, ScreenShareService::class.java).apply {
          action = ACTION_START
          putExtra(EXTRA_RESULT_CODE, resultCode)
          putExtra(EXTRA_RESULT_DATA, data)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent =
        Intent(context, ScreenShareService::class.java).apply { action = ACTION_STOP }
      context.startService(intent)
    }
  }
}
