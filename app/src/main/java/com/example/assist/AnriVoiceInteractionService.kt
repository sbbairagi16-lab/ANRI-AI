package com.example.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AnriVoiceInteractionService : VoiceInteractionService()

class AnriVoiceInteractionSessionService : VoiceInteractionSessionService() {
  override fun onNewSession(args: Bundle?): VoiceInteractionSession {
    return AnriAssistSession(this)
  }
}

private class AnriAssistSession(private val serviceContext: Context) :
  VoiceInteractionSession(serviceContext) {

  override fun onShow(args: Bundle?, showFlags: Int) {
    super.onShow(args, showFlags)
    val overlayIntent =
      Intent(serviceContext, AnriAssistOverlayActivity::class.java).apply {
        action = AnriAssistOverlayActivity.ACTION_POWER_LIVE_CAST
        putExtra(AnriAssistOverlayActivity.EXTRA_AUTO_CAST, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    runCatching { serviceContext.startActivity(overlayIntent) }
    finish()
  }
}
