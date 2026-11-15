package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AnriAiService
import com.example.service.ScreenCaptureHolder
import com.example.voice.BengaliVoiceController
import com.example.voice.VoiceAudioCapture
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceTurn(
  val id: String,
  val role: String,
  val text: String,
  val usedScreenCast: Boolean = false,
)

enum class LiveVoicePhase {
  IDLE,
  LISTENING,
  THINKING,
  SPEAKING,
}

class LiveVoiceViewModel(
  private val aiService: AnriAiService,
  val voiceController: BengaliVoiceController,
) : ViewModel() {

  private val audioCapture = VoiceAudioCapture()
  private var activeTurnJob: Job? = null

  private val _phase = MutableStateFlow(LiveVoicePhase.IDLE)
  val phase: StateFlow<LiveVoicePhase> = _phase.asStateFlow()

  private val _turns = MutableStateFlow<List<VoiceTurn>>(emptyList())
  val turns: StateFlow<List<VoiceTurn>> = _turns.asStateFlow()

  private val _streamingReply = MutableStateFlow("")
  val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

  private val _preferBengali = MutableStateFlow(true)
  val preferBengali: StateFlow<Boolean> = _preferBengali.asStateFlow()

  private val _autoLoopEnabled = MutableStateFlow(true)
  val autoLoopEnabled: StateFlow<Boolean> = _autoLoopEnabled.asStateFlow()

  private val _directGeminiAudioMode = MutableStateFlow(false)
  val directGeminiAudioMode: StateFlow<Boolean> = _directGeminiAudioMode.asStateFlow()

  private val _micAmplitude = MutableStateFlow(0f)
  val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  val isScreenCasting: StateFlow<Boolean> = ScreenCaptureHolder.isSharing
  val lastScreenFrame: StateFlow<Bitmap?> = ScreenCaptureHolder.lastCapturedFrame

  init {
    viewModelScope.launch {
      voiceController.rmsLevel.collect { rms ->
        if (!_directGeminiAudioMode.value && _phase.value == LiveVoicePhase.LISTENING) {
          _micAmplitude.value = rms
        }
      }
    }
  }

  fun syncVoiceSettings(useBengali: Boolean, speed: Double, pitch: Double) {
    _preferBengali.value = useBengali
    voiceController.configureVoice(useBengali, speed.toFloat(), pitch.toFloat())
  }

  fun toggleBengaliLanguage(useBengali: Boolean, speed: Double, pitch: Double) {
    _preferBengali.value = useBengali
    voiceController.configureVoice(useBengali, speed.toFloat(), pitch.toFloat())
  }

  fun setAutoLoop(enabled: Boolean) {
    _autoLoopEnabled.value = enabled
  }

  fun setDirectGeminiAudioMode(enabled: Boolean) {
    _directGeminiAudioMode.value = enabled
  }

  fun startListeningTurn() {
    // Support smooth Barge-In if ANRI was speaking or thinking
    activeTurnJob?.cancel()
    activeTurnJob = null
    voiceController.stopSpeaking()
    audioCapture.stopAndDiscard()
    _streamingReply.value = ""
    _errorMessage.value = null
    _phase.value = LiveVoicePhase.LISTENING

    if (_directGeminiAudioMode.value || !voiceController.isSpeechRecognizerAvailable()) {
      _directGeminiAudioMode.value = true
      startDirectAudioCapture()
      return
    }

    val langTag = if (_preferBengali.value) "bn-BD" else "en-US"
    voiceController.startListening(
      languageTag = langTag,
      onFinalResult = { spokenText ->
        submitSpokenText(spokenText)
      },
      onError = { err ->
        _phase.value = LiveVoicePhase.IDLE
        _micAmplitude.value = 0f
        _errorMessage.value = err
      },
      onNoSpeechTimeout = {
        // Gentle idle pause instead of scary red error banner
        _phase.value = LiveVoicePhase.IDLE
        _micAmplitude.value = 0f
      },
      onUnavailableOrLanguageMissing = {
        // Seamlessly fall back to Gemini native AudioRecord WAV capture!
        _directGeminiAudioMode.value = true
        if (_phase.value == LiveVoicePhase.LISTENING) {
          startDirectAudioCapture()
        }
      },
    )
  }

  private fun startDirectAudioCapture() {
    voiceController.stopSpeaking()
    _phase.value = LiveVoicePhase.LISTENING
    audioCapture.startCapture(
      scope = viewModelScope,
      onAmplitude = { amp -> _micAmplitude.value = amp },
      onCompletedWav = { wavBytes ->
        viewModelScope.launch {
          submitRecordedAudio(wavBytes)
        }
      },
      onError = { err ->
        viewModelScope.launch {
          _phase.value = LiveVoicePhase.IDLE
          _micAmplitude.value = 0f
          _errorMessage.value = err
        }
      },
    )
  }

  /**
   * Manually finish active direct audio capture immediately (push-to-send feel).
   */
  fun finishListeningEarly() {
    if (_phase.value != LiveVoicePhase.LISTENING) return
    if (audioCapture.isRecording) {
      val wav = audioCapture.finishAndDeliver()
      _micAmplitude.value = 0f
      if (wav != null) {
        submitRecordedAudio(wav)
      } else {
        _phase.value = LiveVoicePhase.IDLE
      }
    } else {
      voiceController.stopListening()
    }
  }

  fun stopSession() {
    activeTurnJob?.cancel()
    activeTurnJob = null
    voiceController.stopListening()
    voiceController.stopSpeaking()
    audioCapture.stopAndDiscard()
    _streamingReply.value = ""
    _micAmplitude.value = 0f
    _phase.value = LiveVoicePhase.IDLE
  }

  fun submitSpokenText(text: String) {
    val clean = text.trim()
    if (clean.isBlank()) {
      _phase.value = LiveVoicePhase.IDLE
      return
    }
    executeVoiceTurn(displayUserText = clean, spokenPrompt = clean, audioWavBytes = null)
  }

  private fun submitRecordedAudio(wavBytes: ByteArray) {
    val label =
      if (_preferBengali.value) "🎙️ ভয়েস মেসেজ (${wavBytes.size / 1024} KB)"
      else "🎙️ Voice input (${wavBytes.size / 1024} KB)"
    executeVoiceTurn(displayUserText = label, spokenPrompt = "", audioWavBytes = wavBytes)
  }

  private fun executeVoiceTurn(
    displayUserText: String,
    spokenPrompt: String,
    audioWavBytes: ByteArray?,
  ) {
    activeTurnJob?.cancel()
    voiceController.stopListening()
    voiceController.stopSpeaking()
    audioCapture.stopAndDiscard()
    _micAmplitude.value = 0f
    _errorMessage.value = null
    _streamingReply.value = ""

    val screenFrame =
      if (ScreenCaptureHolder.isSharing.value) ScreenCaptureHolder.captureFrame() else null

    _turns.update {
      it +
        VoiceTurn(
          id = UUID.randomUUID().toString(),
          role = "user",
          text = displayUserText,
          usedScreenCast = screenFrame != null,
        )
    }
    _phase.value = LiveVoicePhase.THINKING

    activeTurnJob =
      viewModelScope.launch {
        try {
          val recentPairs = _turns.value.dropLast(1).map { it.role to it.text }
          var latestReply = ""

          aiService
            .streamVoiceReply(
              spokenPrompt = spokenPrompt,
              recentTurns = recentPairs,
              preferBengali = _preferBengali.value,
              screenFrame = screenFrame,
              audioWavBytes = audioWavBytes,
            )
            .collect { partial ->
              latestReply = partial
              _streamingReply.value = partial
            }

          val finalReply = latestReply.trim()
          _streamingReply.value = ""
          if (finalReply.isBlank()) {
            _phase.value = LiveVoicePhase.IDLE
            return@launch
          }

          _turns.update {
            it +
              VoiceTurn(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                text = finalReply,
                usedScreenCast = screenFrame != null,
              )
          }

          _phase.value = LiveVoicePhase.SPEAKING
          voiceController.speak(finalReply) {
            viewModelScope.launch {
              if (_phase.value == LiveVoicePhase.SPEAKING) {
                if (_autoLoopEnabled.value) {
                  // Smooth acoustic settle delay prevents echo / recognizer-busy loop errors
                  delay(320L)
                  if (_phase.value == LiveVoicePhase.SPEAKING && _autoLoopEnabled.value) {
                    startListeningTurn()
                  } else if (_phase.value == LiveVoicePhase.SPEAKING) {
                    _phase.value = LiveVoicePhase.IDLE
                  }
                } else {
                  _phase.value = LiveVoicePhase.IDLE
                }
              }
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Live voice AI reply failed", e)
          _streamingReply.value = ""
          _phase.value = LiveVoicePhase.IDLE
          _errorMessage.value = e.localizedMessage ?: "Voice response failed. Tap mic or Retry."
        }
      }
  }

  fun clearTranscript() {
    _turns.value = emptyList()
    _streamingReply.value = ""
    _errorMessage.value = null
  }

  override fun onCleared() {
    stopSession()
    super.onCleared()
  }

  companion object {
    private const val TAG = "LiveVoiceVM"

    fun provideFactory(
      aiService: AnriAiService,
      voiceController: BengaliVoiceController,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { LiveVoiceViewModel(aiService, voiceController) }
    }
  }
}
