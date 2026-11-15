package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Direct microphone PCM-to-WAV recorder with live RMS amplitude tracking and simple VAD
 * auto-stop, enabling Gemini multimodal audio understanding even when Android's
 * SpeechRecognizer service or Bengali language pack is unavailable.
 */
class VoiceAudioCapture {

  private var audioRecord: AudioRecord? = null
  private var captureJob: Job? = null
  private val bufferStream = ByteArrayOutputStream()

  @Volatile
  var isRecording: Boolean = false
    private set

  @SuppressLint("MissingPermission")
  fun startCapture(
    scope: CoroutineScope,
    onAmplitude: (Float) -> Unit,
    onCompletedWav: (ByteArray) -> Unit,
    onError: (String) -> Unit,
  ) {
    stopAndDiscard()

    val minBuf =
      AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
      onError("Microphone hardware is unavailable on this device.")
      return
    }

    val recorder =
      try {
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          maxOf(minBuf * 2, SAMPLE_RATE),
        )
      } catch (e: SecurityException) {
        onError("Microphone permission is required.")
        return
      } catch (e: Exception) {
        Log.e(TAG, "AudioRecord init failed", e)
        onError("Could not initialize microphone.")
        return
      }

    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
      recorder.release()
      onError("Microphone could not start recording.")
      return
    }

    audioRecord = recorder
    bufferStream.reset()
    isRecording = true

    captureJob =
      scope.launch(Dispatchers.IO) {
        val pcmChunk = ShortArray(minBuf / 2)
        val byteBuffer = ByteArray(pcmChunk.size * 2)
        var heardSpeech = false
        var silenceMs = 0L
        var elapsedMs = 0L
        val maxDurationMs = 15_000L
        val postSpeechSilenceMs = 1_450L

        try {
          recorder.startRecording()
          while (isActive && isRecording && elapsedMs < maxDurationMs) {
            val readCount = recorder.read(pcmChunk, 0, pcmChunk.size)
            if (readCount <= 0) continue

            var sumSq = 0.0
            for (i in 0 until readCount) {
              val s = pcmChunk[i].toInt()
              sumSq += s * s
              byteBuffer[i * 2] = (s and 0xFF).toByte()
              byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            bufferStream.write(byteBuffer, 0, readCount * 2)

            val rms = sqrt(sumSq / readCount).toFloat() / 32768f
            val normalizedAmp = (rms * 7.5f).coerceIn(0f, 1f)
            onAmplitude(normalizedAmp)

            val chunkMs = (readCount * 1000L) / SAMPLE_RATE
            elapsedMs += chunkMs
            if (normalizedAmp > 0.10f) {
              heardSpeech = true
              silenceMs = 0L
            } else if (heardSpeech) {
              silenceMs += chunkMs
              if (silenceMs >= postSpeechSilenceMs && elapsedMs >= 900L) {
                break
              }
            }
          }

          val rawPcm = bufferStream.toByteArray()
          stopRecorderInternal()
          onAmplitude(0f)

          if (rawPcm.size < SAMPLE_RATE / 2) {
            onError("No voice audio captured. Speak closer or type below.")
          } else {
            onCompletedWav(wrapPcmAsWav(rawPcm, SAMPLE_RATE, 1, 16))
          }
        } catch (e: Exception) {
          Log.e(TAG, "Audio capture loop failed", e)
          stopRecorderInternal()
          onAmplitude(0f)
          onError(e.localizedMessage ?: "Voice capture interrupted.")
        }
      }
  }

  fun finishAndDeliver(): ByteArray? {
    if (!isRecording) return null
    isRecording = false
    val bytes = bufferStream.toByteArray()
    stopRecorderInternal()
    return if (bytes.size >= SAMPLE_RATE / 2) wrapPcmAsWav(bytes, SAMPLE_RATE, 1, 16) else null
  }

  fun stopAndDiscard() {
    isRecording = false
    captureJob?.cancel()
    captureJob = null
    stopRecorderInternal()
    bufferStream.reset()
  }

  private fun stopRecorderInternal() {
    isRecording = false
    runCatching { audioRecord?.stop() }
    runCatching { audioRecord?.release() }
    audioRecord = null
  }

  private fun wrapPcmAsWav(
    pcmData: ByteArray,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
  ): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray())
    header.putInt(36 + pcmData.size)
    header.put("WAVE".toByteArray())
    header.put("fmt ".toByteArray())
    header.putInt(16)
    header.putShort(1.toShort())
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort((channels * bitsPerSample / 8).toShort())
    header.putShort(bitsPerSample.toShort())
    header.put("data".toByteArray())
    header.putInt(pcmData.size)
    return header.array() + pcmData
  }

  private companion object {
    const val TAG = "VoiceAudioCapture"
    const val SAMPLE_RATE = 16_000
  }
}
