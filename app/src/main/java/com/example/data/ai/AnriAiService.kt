package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.CitationLink
import com.example.data.model.WorkspaceModelCatalog
import com.example.data.model.WritingMode
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AttachedDocument(
  val displayName: String,
  val mimeType: String,
  val textContent: String? = null,
  val bytes: ByteArray? = null,
)

sealed interface ChatStreamEvent {
  data class Delta(val text: String, val citations: List<CitationLink>) : ChatStreamEvent

  data class Completed(val fullText: String, val citations: List<CitationLink>) : ChatStreamEvent
}

data class StoryboardShot(
  val timeRange: String,
  val visualDescription: String,
  val cameraMovement: String,
  val bengaliNarrationOrAudio: String,
)

data class VideoStoryboard(
  val title: String,
  val durationSeconds: Int,
  val visualStyle: String,
  val shots: List<StoryboardShot>,
)

enum class StudioAspectRatio(val label: String, val ratio: AspectRatio?) {
  SQUARE("1:1 Square", null),
  LANDSCAPE_16_9("16:9 Wide", AspectRatio.LANDSCAPE_16x9),
  PORTRAIT_9_16("9:16 Vertical", AspectRatio.PORTRAIT_9x16),
  LANDSCAPE_4_3("4:3 Classic", AspectRatio.LANDSCAPE_4x3),
  PORTRAIT_3_4("3:4 Editorial", AspectRatio.PORTRAIT_3x4),
}

class AnriAiService {

  private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

  private val fastChatModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = WorkspaceModelCatalog.FAST_MODEL_ID,
      systemInstruction =
        content {
          text(
            "You are ANRI AI, an ultra-fast creative assistant. Give direct, accurate, crisp answers immediately without filler."
          )
        },
    )
  }

  private val defaultImageModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = WorkspaceModelCatalog.DEFAULT_IMAGE_MODEL_ID,
      generationConfig = generationConfig { responseModalities = listOf(ResponseModality.IMAGE) },
    )
  }

  private val storyboardSchema by lazy {
    Schema.obj(
      properties =
        mapOf(
          "title" to Schema.string(description = "Short creative title for the video"),
          "durationSeconds" to Schema.integer(description = "Total duration capped at 10 seconds"),
          "visualStyle" to Schema.string(description = "Lighting, palette, and lens direction"),
          "shots" to
            Schema.array(
              items =
                Schema.obj(
                  properties =
                    mapOf(
                      "timeRange" to Schema.string(description = "Timestamp span e.g. 0s - 3s"),
                      "visualDescription" to Schema.string(description = "What happens in frame"),
                      "cameraMovement" to Schema.string(description = "Camera motion cue"),
                      "bengaliNarrationOrAudio" to
                        Schema.string(description = "Audio or voiceover cue"),
                    )
                ),
              description = "Sequential shots totaling <= 10 seconds",
            ),
        )
    )
  }

  private val storyboardModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = WorkspaceModelCatalog.FAST_MODEL_ID,
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = storyboardSchema
        },
    )
  }

  private fun getOrCreateChatModel(
    modelId: String,
    writingMode: WritingMode,
    userBio: String,
    enableWebSearch: Boolean,
  ): GenerativeModel {
    val safeModelId =
      if (WorkspaceModelCatalog.chatModels().any { it.id == modelId }) modelId
      else WorkspaceModelCatalog.FAST_MODEL_ID

    val cacheKey = "${safeModelId}|${writingMode.id}|${enableWebSearch}|${userBio.hashCode()}"
    modelCache[cacheKey]?.let { return it }

    val modeInstruction =
      when (writingMode) {
        WritingMode.FAST ->
          "You are ANRI AI in Turbo Fast mode. Be direct, accurate, and concise. Lead with the answer in clean bullets or short code."
        WritingMode.CHAT ->
          "You are ANRI AI, a sharp, helpful creative workspace assistant. Be concise, accurate, and well-structured."
        WritingMode.WRITING ->
          "You are ANRI AI in Writing Studio mode. Deliver crisp editorial prose, clear structure, and offer thoughtful stylistic polish."
        WritingMode.CODE ->
          "You are ANRI AI in Code Lab mode. Write clean, production-grade code, explain architecture decisions concisely, and avoid placeholder stubs."
        WritingMode.RESEARCH ->
          "You are ANRI AI in Cited Search mode. Ground factual claims using search results, cite verifiable sources clearly, and include source URLs."
        WritingMode.BENGALI ->
          "You are ANRI AI (আনরি এআই), a warm, articulate bilingual assistant fluent in natural Bengali (বাংলা) and English. Respond naturally in Bengali when addressed in Bengali or asked for Bengali output."
      }

    val bioSection = if (userBio.isNotBlank()) "\nUser profile context: $userBio" else ""
    val systemContent = content { text(modeInstruction + bioSection) }
    val tools =
      if (enableWebSearch || writingMode == WritingMode.RESEARCH) {
        listOf(Tool.googleSearch())
      } else {
        null
      }

    return Firebase.ai
      .generativeModel(
        modelName = safeModelId,
        systemInstruction = systemContent,
        tools = tools,
      )
      .also { modelCache[cacheKey] = it }
  }

  /**
   * Ensures strict user/model turn alternation ending on "model" (or empty) before sending a new user turn.
   * Prevents 400 Bad Request errors when an earlier failed request left an unanswered user message in history.
   */
  private fun buildSanitizedContents(
    history: List<ChatMessage>,
    userPrompt: String,
    attachedImages: List<Bitmap>,
    attachedDocuments: List<AttachedDocument>,
    maxTurns: Int,
  ): List<Content> {
    val normalizedTurns = mutableListOf<Pair<String, StringBuilder>>()
    for (msg in history.takeLast(maxTurns)) {
      val text = msg.content.trim()
      if (text.isEmpty()) continue
      val role = if (msg.role == "assistant") "model" else "user"
      if (normalizedTurns.isEmpty()) {
        // Conversation history should start cleanly with a user turn
        if (role == "user") {
          normalizedTurns.add(role to StringBuilder(text))
        }
      } else if (normalizedTurns.last().first == role) {
        normalizedTurns.last().second.append("\n\n").append(text)
      } else {
        normalizedTurns.add(role to StringBuilder(text))
      }
    }

    // If history ends with an unanswered "user" message (e.g. from a prior error), merge it into the new turn
    val trailingUnansweredUser =
      if (normalizedTurns.lastOrNull()?.first == "user") {
        normalizedTurns.removeAt(normalizedTurns.lastIndex).second.toString()
      } else {
        null
      }

    val historyContents: List<Content> =
      normalizedTurns.map { (role, sb) ->
        content(role) { text(sb.toString()) }
      }

    val finalUserPrompt =
      if (!trailingUnansweredUser.isNullOrBlank()) {
        "$trailingUnansweredUser\n\n$userPrompt"
      } else {
        userPrompt
      }

    val currentContent = content("user") {
      attachedImages.forEach { bmp ->
        image(scaleBitmapForUpload(bmp, maxDim = 1024))
      }
      attachedDocuments.forEach { doc ->
        if (!doc.textContent.isNullOrBlank()) {
          text("Attached file (${doc.displayName}):\n${doc.textContent.take(12000)}")
        } else if (doc.bytes != null && doc.bytes.size <= 15 * 1024 * 1024) {
          inlineData(doc.bytes, doc.mimeType)
        }
      }
      text(finalUserPrompt)
    }

    return historyContents + currentContent
  }

  fun streamChatResponse(
    modelId: String,
    writingMode: WritingMode,
    userBio: String,
    history: List<ChatMessage>,
    userPrompt: String,
    attachedImages: List<Bitmap> = emptyList(),
    attachedDocuments: List<AttachedDocument> = emptyList(),
    enableWebSearch: Boolean = false,
  ): Flow<ChatStreamEvent> = flow {
    val maxTurns = if (writingMode == WritingMode.FAST) 6 else 12
    val fullInput =
      buildSanitizedContents(
        history = history,
        userPrompt = userPrompt,
        attachedImages = attachedImages,
        attachedDocuments = attachedDocuments,
        maxTurns = maxTurns,
      )

    val effectiveModelId =
      if (writingMode == WritingMode.FAST && !enableWebSearch && attachedImages.isEmpty()) {
        WorkspaceModelCatalog.FAST_MODEL_ID
      } else {
        modelId
      }

    // Build ordered attempts: primary -> fast fallback without tools -> balanced fallback without tools
    val attempts = buildList {
      val primary =
        if (
          effectiveModelId == WorkspaceModelCatalog.FAST_MODEL_ID &&
            writingMode == WritingMode.FAST &&
            userBio.isBlank() &&
            !enableWebSearch &&
            attachedImages.isEmpty() &&
            attachedDocuments.isEmpty()
        ) {
          fastChatModel
        } else {
          getOrCreateChatModel(effectiveModelId, writingMode, userBio, enableWebSearch)
        }
      add(primary)

      if (enableWebSearch || effectiveModelId != WorkspaceModelCatalog.FAST_MODEL_ID || writingMode != WritingMode.FAST) {
        add(getOrCreateChatModel(WorkspaceModelCatalog.FAST_MODEL_ID, WritingMode.FAST, userBio, enableWebSearch = false))
      }
      if (effectiveModelId != WorkspaceModelCatalog.BALANCED_MODEL_ID) {
        add(getOrCreateChatModel(WorkspaceModelCatalog.BALANCED_MODEL_ID, WritingMode.CHAT, userBio, enableWebSearch = false))
      }
    }

    var lastError: Exception? = null
    for ((attemptIndex, model) in attempts.withIndex()) {
      val accumulated = StringBuilder()
      val citationMap = linkedMapOf<String, CitationLink>()
      try {
        withTimeout(45_000L) {
          model.generateContentStream(fullInput).collect { chunk ->
            val chunkText =
              chunk.text
                ?: chunk.candidates
                  .firstOrNull()
                  ?.content
                  ?.parts
                  ?.filterIsInstance<TextPart>()
                  ?.joinToString("") { it.text }
                  .orEmpty()
            if (chunkText.isNotEmpty()) {
              accumulated.append(chunkText)
            }
            val groundingChunks =
              chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks.orEmpty()
            for (gc in groundingChunks) {
              val web = gc.web
              val uri = web?.uri
              if (!uri.isNullOrBlank()) {
                val title = web.title?.takeIf { it.isNotBlank() } ?: uri
                citationMap[uri] = CitationLink(title = title, url = uri)
              }
            }
            if (accumulated.isNotEmpty() || citationMap.isNotEmpty()) {
              emit(ChatStreamEvent.Delta(accumulated.toString(), citationMap.values.toList()))
            }
          }
        }

        val finalText = accumulated.toString().trim()
        if (finalText.isNotBlank()) {
          emit(ChatStreamEvent.Completed(finalText, citationMap.values.toList()))
          return@flow
        } else {
          throw IllegalStateException("Model returned an empty response.")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        Log.w(TAG, "Chat attempt ${attemptIndex + 1}/${attempts.size} failed: ${e.message}", e)
        // If partial text was already streamed to the user, don't retry and duplicate; handle cleanly
        if (accumulated.isNotBlank()) {
          emit(ChatStreamEvent.Completed(accumulated.toString().trim(), citationMap.values.toList()))
          return@flow
        }
      }
    }

    val root = lastError
    Log.e(TAG, "All AI chat attempts failed", root)
    throw IllegalStateException(formatFriendlyAiError(root), root)
  }

  suspend fun generateImage(
    modelId: String,
    prompt: String,
    aspectRatio: StudioAspectRatio,
    referenceImage: Bitmap? = null,
  ): Bitmap {
    val safeModelId =
      if (WorkspaceModelCatalog.imageModels().any { it.id == modelId }) modelId
      else WorkspaceModelCatalog.DEFAULT_IMAGE_MODEL_ID

    val model =
      if (safeModelId == WorkspaceModelCatalog.DEFAULT_IMAGE_MODEL_ID && aspectRatio == StudioAspectRatio.SQUARE) {
        defaultImageModel
      } else {
        val config = generationConfig {
          responseModalities = listOf(ResponseModality.IMAGE)
          if (aspectRatio.ratio != null) {
            imageConfig =
              ImageConfig.builder()
                .setAspectRatio(aspectRatio.ratio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
          }
        }
        Firebase.ai.generativeModel(modelName = safeModelId, generationConfig = config)
      }

    try {
      val response =
        withTimeout(60_000L) {
          if (referenceImage != null) {
            val scaled = scaleBitmapForUpload(referenceImage, maxDim = 1024)
            model.generateContent(
              content {
                image(scaled)
                text(prompt)
              }
            )
          } else {
            model.generateContent(prompt)
          }
        }
      val imagePart =
        response.candidates.firstOrNull()?.content?.parts?.filterIsInstance<ImagePart>()?.firstOrNull()
      return imagePart?.image
        ?: throw IllegalStateException("The image model did not return an image.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Image generation failed", e)
      throw IllegalStateException(formatFriendlyAiError(e), e)
    }
  }

  suspend fun generateVideoStoryboard(
    prompt: String,
    durationSeconds: Int,
  ): VideoStoryboard {
    val cappedDuration = durationSeconds.coerceIn(1, 10)
    val fullPrompt =
      """
      Create a concise shot-by-shot video storyboard capped strictly at $cappedDuration seconds total duration.
      Concept: $prompt
      Ensure shot timeRanges add up to exactly ${cappedDuration}s and include natural Bengali narration or audio cues.
      """.trimIndent()

    try {
      val response = withTimeout(45_000L) { storyboardModel.generateContent(fullPrompt) }
      val jsonStr = response.text ?: throw IllegalStateException("Model returned empty storyboard.")
      val json = JSONObject(jsonStr)
      val shotsArr = json.getJSONArray("shots")
      val shots =
        (0 until shotsArr.length()).map { idx ->
          val obj = shotsArr.getJSONObject(idx)
          StoryboardShot(
            timeRange = obj.optString("timeRange", "${idx * 2}s-${(idx + 1) * 2}s"),
            visualDescription = obj.optString("visualDescription", ""),
            cameraMovement = obj.optString("cameraMovement", "Static"),
            bengaliNarrationOrAudio = obj.optString("bengaliNarrationOrAudio", ""),
          )
        }
      return VideoStoryboard(
        title = json.optString("title", "10s Video Storyboard"),
        durationSeconds = json.optInt("durationSeconds", cappedDuration).coerceIn(1, 10),
        visualStyle = json.optString("visualStyle", "Cinematic"),
        shots = shots,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Storyboard generation failed", e)
      throw IllegalStateException(formatFriendlyAiError(e), e)
    }
  }

  fun streamVoiceReply(
    spokenPrompt: String,
    recentTurns: List<Pair<String, String>>,
    preferBengali: Boolean,
    screenFrame: Bitmap? = null,
    audioWavBytes: ByteArray? = null,
  ): Flow<String> = flow {
    val sysText =
      if (preferBengali) {
        "You are ANRI, a warm, ultra-fast feminine Bengali voice assistant with live screen vision. Reply immediately in natural spoken Bengali (বাংলা লিপি) in 1 to 2 concise sentences without markdown symbols. If a live screen capture is attached, directly answer about what is visible on screen."
      } else {
        "You are ANRI, a warm, ultra-fast voice assistant with live screen vision. Reply immediately in 1 to 2 concise spoken sentences without markdown symbols. If a live screen capture is attached, directly answer about what is visible on screen."
      }

    val sanitizedTurns = mutableListOf<Pair<String, StringBuilder>>()
    for ((rawRole, rawText) in recentTurns.takeLast(6)) {
      val clean = rawText.trim()
      if (clean.isEmpty()) continue
      val role = if (rawRole == "assistant") "model" else "user"
      if (sanitizedTurns.isEmpty()) {
        if (role == "user") sanitizedTurns.add(role to StringBuilder(clean))
      } else if (sanitizedTurns.last().first == role) {
        sanitizedTurns.last().second.append(" ").append(clean)
      } else {
        sanitizedTurns.add(role to StringBuilder(clean))
      }
    }
    if (sanitizedTurns.lastOrNull()?.first == "user") {
      sanitizedTurns.removeAt(sanitizedTurns.lastIndex)
    }

    val currentTurn = content("user") {
      if (screenFrame != null) {
        image(scaleBitmapForUpload(screenFrame, maxDim = 960))
      }
      if (audioWavBytes != null) {
        inlineData(audioWavBytes, "audio/wav")
      }
      val promptText =
        when {
          audioWavBytes != null && spokenPrompt.isBlank() ->
            if (preferBengali) "আমার কথা শুনে উত্তর দাও।" else "Listen to my voice message and respond directly."
          spokenPrompt.isNotBlank() -> spokenPrompt
          else -> "Describe what you see on my screen concisely."
        }
      text(promptText)
    }

    val contents =
      sanitizedTurns.map { (role, sb) -> content(role) { text(sb.toString()) } } + currentTurn

    // Prefer balanced multimodal Flash when raw audio/screen image is included, with fast fallback
    val candidateModels =
      if (audioWavBytes != null || screenFrame != null) {
        listOf(WorkspaceModelCatalog.BALANCED_MODEL_ID, WorkspaceModelCatalog.FAST_MODEL_ID)
      } else {
        listOf(WorkspaceModelCatalog.FAST_MODEL_ID, WorkspaceModelCatalog.BALANCED_MODEL_ID)
      }

    var lastError: Exception? = null
    for (mId in candidateModels) {
      val accumulated = StringBuilder()
      try {
        val cacheKey = "voice|$mId|$preferBengali"
        val voiceModel =
          modelCache[cacheKey]
            ?: Firebase.ai
              .generativeModel(
                modelName = mId,
                systemInstruction = content { text(sysText) },
              )
              .also { modelCache[cacheKey] = it }

        withTimeout(30_000L) {
          voiceModel.generateContentStream(contents).collect { chunk ->
            val chunkText =
              chunk.text
                ?: chunk.candidates
                  .firstOrNull()
                  ?.content
                  ?.parts
                  ?.filterIsInstance<TextPart>()
                  ?.joinToString("") { it.text }
                  .orEmpty()
            if (chunkText.isNotEmpty()) {
              accumulated.append(chunkText)
              emit(accumulated.toString())
            }
          }
        }
        val finalReply = accumulated.toString().trim()
        if (finalReply.isNotBlank()) return@flow
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        Log.w(TAG, "Voice stream attempt with $mId failed: ${e.message}", e)
        if (accumulated.isNotBlank()) return@flow
      }
    }

    throw IllegalStateException(formatFriendlyAiError(lastError), lastError)
  }

  suspend fun generateVoiceReply(
    spokenPrompt: String,
    recentTurns: List<Pair<String, String>>,
    preferBengali: Boolean,
    screenFrame: Bitmap? = null,
    audioWavBytes: ByteArray? = null,
  ): String {
    var latest = ""
    streamVoiceReply(
      spokenPrompt = spokenPrompt,
      recentTurns = recentTurns,
      preferBengali = preferBengali,
      screenFrame = screenFrame,
      audioWavBytes = audioWavBytes,
    ).collect { partial ->
      latest = partial
    }
    return latest.trim().ifBlank {
      throw IllegalStateException("Voice model returned an empty response.")
    }
  }

  private fun formatFriendlyAiError(error: Throwable?): String {
    if (error == null) return "AI request failed. Tap Retry to try again."
    if (error is TimeoutCancellationException) {
      return "Response timed out. Check your internet connection or switch to Turbo Fast and tap Retry."
    }
    val raw = error.localizedMessage.orEmpty()
    val lower = raw.lowercase()
    return when {
      lower.contains("403") || lower.contains("app check") || lower.contains("attestation") ->
        "App verification (App Check) was rejected for this request. Reopen the app or switch to a standard-tier model and tap Retry."
      lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") ->
        "Rate limit reached right now. Wait a few seconds and tap Retry."
      lower.contains("billing") || lower.contains("blaze") ->
        "This Pro/Image model requires Firebase Blaze billing enabled on the project. Try Gemini 3.5 Flash-Lite or Gemini 3.8 Flash."
      lower.contains("unable to resolve host") ||
        lower.contains("network") ||
        lower.contains("connect") ->
        "Network connection issue. Please check internet connectivity and tap Retry."
      raw.isNotBlank() -> raw
      else -> "AI request could not be completed. Tap Retry to try again."
    }
  }

  private fun scaleBitmapForUpload(bitmap: Bitmap, maxDim: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDim && h <= maxDim) return bitmap
    val ratio = minOf(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat())
    val targetW = (w * ratio).toInt().coerceAtLeast(1)
    val targetH = (h * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
  }

  private companion object {
    const val TAG = "AnriAiService"
  }
}
