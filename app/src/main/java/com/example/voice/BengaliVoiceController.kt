package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BengaliVoiceController(private val appContext: Context) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val utteranceCallbacks = ConcurrentHashMap<String, () -> Unit>()

  private var tts: TextToSpeech? = null
  private var ttsInitFailed: Boolean = false
  private var pendingSpeakText: String? = null
  private var pendingSpeakDone: (() -> Unit)? = null
  private var speechRecognizer: SpeechRecognizer? = null
  private var activeFallbackAttempted: Boolean = false

  private val _isTtsReady = MutableStateFlow(false)
  val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _partialTranscript = MutableStateFlow("")
  val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

  private val _rmsLevel = MutableStateFlow(0f)
  val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

  private val _activeVoiceLabel = MutableStateFlow("Initializing Bengali Voice...")
  val activeVoiceLabel: StateFlow<String> = _activeVoiceLabel.asStateFlow()

  private val _voiceError = MutableStateFlow<String?>(null)
  val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

  private var pendingPitch: Float = 1.08f
  private var pendingSpeed: Float = 1.0f
  private var bengaliEnabled: Boolean = true

  init {
    initTts()
  }

  private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      mainHandler.post(block)
    }
  }

  private fun initTts() {
    runCatching {
      tts =
        TextToSpeech(appContext) { status ->
          runOnMain {
            if (status == TextToSpeech.SUCCESS) {
              ttsInitFailed = false
              configureVoice(bengaliEnabled, pendingSpeed, pendingPitch)
              tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                  override fun onStart(utteranceId: String?) {
                    mainHandler.post { _isSpeaking.value = true }
                  }

                  override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                      _isSpeaking.value = false
                      utteranceId?.let { id -> utteranceCallbacks.remove(id)?.invoke() }
                    }
                  }

                  @Deprecated("Deprecated in Java")
                  override fun onError(utteranceId: String?) {
                    mainHandler.post {
                      _isSpeaking.value = false
                      utteranceId?.let { id -> utteranceCallbacks.remove(id)?.invoke() }
                    }
                  }

                  override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                      _isSpeaking.value = false
                      utteranceId?.let { id -> utteranceCallbacks.remove(id)?.invoke() }
                    }
                  }
                }
              )
              _isTtsReady.value = true

              // Flush queued speech if user spoke before TTS engine finished warming up
              val queuedText = pendingSpeakText
              val queuedDone = pendingSpeakDone
              pendingSpeakText = null
              pendingSpeakDone = null
              if (!queuedText.isNullOrBlank()) {
                speak(queuedText, queuedDone)
              }
            } else {
              ttsInitFailed = true
              _isTtsReady.value = false
              _activeVoiceLabel.value = "TTS unavailable (Text mode active)"
              val queuedDone = pendingSpeakDone
              pendingSpeakText = null
              pendingSpeakDone = null
              queuedDone?.invoke()
            }
          }
        }
    }.onFailure { err ->
      Log.w(TAG, "TextToSpeech constructor failed", err)
      ttsInitFailed = true
      _isTtsReady.value = false
    }
  }

  fun configureVoice(useBengali: Boolean, speed: Float, pitch: Float) {
    bengaliEnabled = useBengali
    pendingSpeed = speed.coerceIn(0.5f, 2.0f)
    pendingPitch = pitch.coerceIn(0.5f, 2.0f)
    val engine = tts ?: return

    runCatching {
      engine.setSpeechRate(pendingSpeed)
      engine.setPitch(pendingPitch)

      if (useBengali) {
        val bdLocale = Locale("bn", "BD")
        val inLocale = Locale("bn", "IN")
        val voices = runCatching { engine.voices?.toList() ?: emptyList() }.getOrElse { emptyList() }
        val bengaliVoice = pickFeminineBengaliVoice(voices)
        if (bengaliVoice != null) {
          engine.voice = bengaliVoice
          _activeVoiceLabel.value = "Feminine Bengali (${bengaliVoice.locale.displayName})"
          return
        }
        val bdResult = engine.setLanguage(bdLocale)
        if (bdResult != TextToSpeech.LANG_MISSING_DATA && bdResult != TextToSpeech.LANG_NOT_SUPPORTED) {
          _activeVoiceLabel.value = "Bengali (bn-BD)"
          return
        }
        val inResult = engine.setLanguage(inLocale)
        if (inResult != TextToSpeech.LANG_MISSING_DATA && inResult != TextToSpeech.LANG_NOT_SUPPORTED) {
          _activeVoiceLabel.value = "Bengali (bn-IN)"
          return
        }
        engine.setLanguage(Locale.US)
        _activeVoiceLabel.value = "English fallback (Install Bengali TTS pack for বাংলা audio)"
      } else {
        val voices = runCatching { engine.voices?.toList() ?: emptyList() }.getOrElse { emptyList() }
        val enFemale =
          voices.firstOrNull {
            it.locale.language == "en" &&
              (it.name.contains("female", true) ||
                it.name.contains("sfg", true) ||
                it.name.contains("tpf", true))
          }
        if (enFemale != null) {
          engine.voice = enFemale
        } else {
          engine.setLanguage(Locale.US)
        }
        _activeVoiceLabel.value = "English (Natural)"
      }
    }
  }

  private fun pickFeminineBengaliVoice(voices: List<Voice>): Voice? {
    val bengaliVoices = voices.filter { it.locale.language.equals("bn", ignoreCase = true) }
    if (bengaliVoices.isEmpty()) return null
    val femaleMatch =
      bengaliVoices.firstOrNull { voice ->
        val n = voice.name.lowercase(Locale.ROOT)
        n.contains("female") ||
          n.contains("bng-x-bn") ||
          n.contains("bn-bd-x-ban") ||
          n.contains("sfg") ||
          n.contains("woman") ||
          voice.features.any { it.contains("female", ignoreCase = true) }
      }
    return femaleMatch ?: bengaliVoices.firstOrNull { it.locale.country.equals("BD", true) }
      ?: bengaliVoices.first()
  }

  fun speak(text: String, onDone: (() -> Unit)? = null) {
    val cleanText =
      text
        .replace(Regex("```[\\s\\S]*?```"), " code block ")
        .replace(Regex("[*_`#>]"), "")
        .trim()
    if (cleanText.isBlank()) {
      onDone?.invoke()
      return
    }
    if (ttsInitFailed) {
      onDone?.invoke()
      return
    }
    val engine = tts
    if (engine == null || !_isTtsReady.value) {
      pendingSpeakText = cleanText
      pendingSpeakDone = onDone
      return
    }

    runOnMain {
      utteranceCallbacks.clear()
      val utteranceId = UUID.randomUUID().toString()
      if (onDone != null) {
        utteranceCallbacks[utteranceId] = onDone
      }
      _isSpeaking.value = true
      val status =
        runCatching {
          engine.speak(cleanText.take(3500), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
      if (status == TextToSpeech.ERROR) {
        _isSpeaking.value = false
        utteranceCallbacks.remove(utteranceId)?.invoke()
      }
    }
  }

  fun stopSpeaking() {
    pendingSpeakText = null
    pendingSpeakDone = null
    utteranceCallbacks.clear()
    runCatching { tts?.stop() }
    _isSpeaking.value = false
  }

  fun isSpeechRecognizerAvailable(): Boolean =
    runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

  fun startListening(
    languageTag: String = "bn-BD",
    onFinalResult: (String) -> Unit,
    onError: (String) -> Unit = {},
    onNoSpeechTimeout: (() -> Unit)? = null,
    onUnavailableOrLanguageMissing: (() -> Unit)? = null,
  ) {
    activeFallbackAttempted = false
    startListeningInternal(
      languageTag = languageTag,
      onFinalResult = onFinalResult,
      onError = onError,
      onNoSpeechTimeout = onNoSpeechTimeout,
      onUnavailableOrLanguageMissing = onUnavailableOrLanguageMissing,
    )
  }

  private fun startListeningInternal(
    languageTag: String,
    onFinalResult: (String) -> Unit,
    onError: (String) -> Unit,
    onNoSpeechTimeout: (() -> Unit)?,
    onUnavailableOrLanguageMissing: (() -> Unit)?,
  ) {
    runOnMain {
      if (!isSpeechRecognizerAvailable()) {
        if (onUnavailableOrLanguageMissing != null) {
          onUnavailableOrLanguageMissing()
        } else {
          val msg = "Device speech recognizer unavailable; using direct mic capture."
          _voiceError.value = msg
          onError(msg)
        }
        return@runOnMain
      }

      stopSpeaking()
      _voiceError.value = null
      _partialTranscript.value = ""
      _rmsLevel.value = 0f

      runCatching {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
      }

      val recognizer =
        runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
      if (recognizer == null) {
        if (onUnavailableOrLanguageMissing != null) {
          onUnavailableOrLanguageMissing()
        } else {
          onError("Could not initialize speech recognizer.")
        }
        return@runOnMain
      }

      speechRecognizer =
        recognizer.apply {
          setRecognitionListener(
            object : RecognitionListener {
              override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
              }

              override fun onBeginningOfSpeech() {
                _isListening.value = true
              }

              override fun onRmsChanged(rmsdB: Float) {
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsLevel.value = norm
              }

              override fun onBufferReceived(buffer: ByteArray?) {}

              override fun onEndOfSpeech() {
                _isListening.value = false
                _rmsLevel.value = 0f
              }

              override fun onError(error: Int) {
                _isListening.value = false
                _rmsLevel.value = 0f

                // Transparent fallback: if bn-BD language pack missing, try en-US once before switching to Gemini Audio
                if (
                  (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) &&
                    languageTag.startsWith("bn") &&
                    !activeFallbackAttempted
                ) {
                  activeFallbackAttempted = true
                  mainHandler.postDelayed(
                    {
                      startListeningInternal(
                        languageTag = "en-US",
                        onFinalResult = onFinalResult,
                        onError = onError,
                        onNoSpeechTimeout = onNoSpeechTimeout,
                        onUnavailableOrLanguageMissing = onUnavailableOrLanguageMissing,
                      )
                    },
                    180L,
                  )
                  return
                }

                if (
                  (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                    error == SpeechRecognizer.ERROR_CLIENT) &&
                    onUnavailableOrLanguageMissing != null
                ) {
                  onUnavailableOrLanguageMissing()
                  return
                }

                if (
                  (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                    onNoSpeechTimeout != null
                ) {
                  onNoSpeechTimeout()
                  return
                }

                val message =
                  when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio busy. Retrying or tap mic."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                      "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                      "Network issue during voice input. Switched to offline-ready mic."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to speak again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic when ready."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                      "Voice service was busy. Tap mic to resume."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                      "Bengali voice pack not installed on-device; switched to Gemini Direct Audio."
                    else -> "Voice input paused ($error). Tap mic or type below."
                  }
                Log.w(TAG, "SpeechRecognizer error: $message")
                _voiceError.value = message
                onError(message)
              }

              override fun onResults(results: Bundle?) {
                _isListening.value = false
                _rmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty().trim()
                _partialTranscript.value = text
                if (text.isNotBlank()) {
                  onFinalResult(text)
                } else if (onNoSpeechTimeout != null) {
                  onNoSpeechTimeout()
                }
              }

              override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                  partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                  _partialTranscript.value = partial
                }
              }

              override fun onEvent(eventType: Int, params: Bundle?) {}
            }
          )
        }

      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
          putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
      _isListening.value = true
      runCatching { recognizer.startListening(intent) }.onFailure { err ->
        _isListening.value = false
        if (onUnavailableOrLanguageMissing != null) {
          onUnavailableOrLanguageMissing()
        } else {
          onError(err.localizedMessage ?: "Could not start voice listener.")
        }
      }
    }
  }

  fun stopListening() {
    runOnMain {
      runCatching { speechRecognizer?.stopListening() }
      _isListening.value = false
      _rmsLevel.value = 0f
    }
  }

  fun release() {
    runOnMain {
      utteranceCallbacks.clear()
      pendingSpeakText = null
      pendingSpeakDone = null
      runCatching { speechRecognizer?.cancel() }
      runCatching { speechRecognizer?.destroy() }
      speechRecognizer = null
      runCatching {
        tts?.stop()
        tts?.shutdown()
      }
      tts = null
      _isSpeaking.value = false
      _isListening.value = false
      _rmsLevel.value = 0f
    }
  }

  private companion object {
    const val TAG = "BengaliVoiceController"
  }
}
