package com.example.data.model

data class UserProfile(
  val id: String = "local_profile",
  val displayName: String = "ANRI Creator",
  val bio: String = "",
  val preferredModelId: String = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
  val bengaliVoiceEnabled: Boolean = true,
  val voiceSpeed: Double = 1.0,
  val voicePitch: Double = 1.08,
  val darkModeOverride: String = "dark",
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
)

data class Conversation(
  val id: String = "",
  val title: String = "New Conversation",
  val modelId: String = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
  val lastMessagePreview: String = "",
  val messageCount: Long = 0L,
  val pinned: Boolean = false,
  val writingMode: String = WritingMode.FAST.id,
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
)

data class ChatMessage(
  val id: String = "",
  val conversationId: String = "",
  val role: String = "user",
  val content: String = "",
  val modelId: String = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
  val modelName: String = "Gemini 3.5 Flash-Lite",
  val attachmentNames: List<String> = emptyList(),
  val citationTitles: List<String> = emptyList(),
  val citationUrls: List<String> = emptyList(),
  val createdAtMillis: Long = 0L,
) {
  val citations: List<CitationLink>
    get() =
      citationUrls.mapIndexed { idx, url ->
        CitationLink(
          title = citationTitles.getOrElse(idx) { url },
          url = url,
        )
      }
}

data class SavedPrompt(
  val id: String = "",
  val title: String = "",
  val body: String = "",
  val category: String = "Writing",
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
)

data class CitationLink(
  val title: String,
  val url: String,
)

enum class WritingMode(
  val id: String,
  val label: String,
  val description: String,
) {
  FAST("fast", "⚡ Turbo Fast", "Ultra-fast direct answers with minimal latency"),
  CHAT("chat", "General Chat", "Balanced assistant for everyday tasks"),
  WRITING("writing", "Writing Studio", "Editorial drafting, rewriting, and tone polish"),
  CODE("code", "Code Lab", "Architecture, debugging, and clean code"),
  RESEARCH("research", "Cited Search", "Grounded research with verifiable source links"),
  BENGALI("bengali", "বাংলা সহায়ক", "Natural Bengali & bilingual assistance"),
  ;

  companion object {
    fun fromId(id: String): WritingMode = entries.firstOrNull { it.id == id } ?: CHAT
  }
}

enum class AiToolkitAction(
  val id: String,
  val title: String,
  val subtitle: String,
  val placeholder: String,
  val promptPrefix: String,
) {
  FAST_QA(
    id = "fast_qa",
    title = "Instant Answer",
    subtitle = "Get a crisp 3-bullet answer right away",
    placeholder = "Ask any question...",
    promptPrefix = "Answer directly and concisely in crisp bullet points:\n\n",
  ),
  TRANSLATE_BN(
    id = "translate_bn",
    title = "English ↔ বাংলা",
    subtitle = "Natural bilingual translation & tone match",
    placeholder = "Paste English or Bengali text to translate...",
    promptPrefix =
      "Translate naturally between English and Bengali (বাংলা). Preserve meaning and tone:\n\n",
  ),
  SUMMARIZE(
    id = "summarize",
    title = "TL;DR Summarizer",
    subtitle = "Extract key takeaways & action items",
    placeholder = "Paste notes, article, or text to summarize...",
    promptPrefix =
      "Summarize the following into Key Takeaways and Next Action Items clearly:\n\n",
  ),
  CODE_FIX(
    id = "code_fix",
    title = "Code Fixer",
    subtitle = "Find bugs & output clean working code",
    placeholder = "Paste code or describe the bug...",
    promptPrefix = "Diagnose any bugs and provide the clean, fixed code with a brief note:\n\n",
  ),
  EMAIL_PRO(
    id = "email_pro",
    title = "Smart Writer",
    subtitle = "Draft polished emails, posts, or captions",
    placeholder = "What do you want to write and in what tone?",
    promptPrefix = "Draft a polished, engaging message ready to send for this request:\n\n",
  ),
  IDEA_SPARK(
    id = "idea_spark",
    title = "Idea & Hook Lab",
    subtitle = "5 creative hooks, titles, or concepts",
    placeholder = "Enter your topic or project idea...",
    promptPrefix = "Brainstorm 5 punchy, original ideas and hooks for:\n\n",
  ),
}

enum class ImageStylePreset(
  val label: String,
  val styleSuffix: String,
) {
  NONE("Natural", ""),
  CYBER_GLOW("Cyber Glow", ", cybernetic obsidian atmosphere, glowing amber and electric teal neon rim lighting, volumetric mist, 8k ultra-detailed"),
  PHOTOREAL("Photoreal", ", photorealistic studio photography, dramatic cinematic lighting, sharp focus, shallow depth of field"),
  RENDER_3D("3D Glass", ", sleek 3D octane render, translucent glassmorphism, glowing accents, studio softbox reflections"),
  ANIME("Anime Art", ", vibrant high-detail anime illustration, expressive dynamic lighting, crisp linework"),
  LOGO_BADGE("Brand Logo", ", minimalist iconic vector-style emblem centered on dark background, clean geometric symmetry, glowing metallic edge"),
}

enum class ModelTask(val label: String) {
  CHAT("Chat & Writing"),
  IMAGE("Image Studio"),
  VOICE("Voice & TTS"),
}

data class AiModelSpec(
  val id: String,
  val displayName: String,
  val family: String,
  val task: ModelTask,
  val capabilities: List<String>,
  val summary: String,
  val requiresBlaze: Boolean = false,
  val isDefault: Boolean = false,
)

object WorkspaceModelCatalog {
  const val FAST_MODEL_ID = "gemini-3.5-flash-lite"
  const val BALANCED_MODEL_ID = "gemini-3.8-flash"
  const val DEFAULT_CHAT_MODEL_ID = FAST_MODEL_ID
  const val DEFAULT_IMAGE_MODEL_ID = "gemini-nano-banana-2.1"
  const val DEFAULT_VOICE_MODEL_ID = "gemini-3.1-flash-tts-preview"

  val models: List<AiModelSpec> =
    listOf(
      AiModelSpec(
        id = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        family = "Gemini 3",
        task = ModelTask.CHAT,
        capabilities = listOf("Ultra-Fast", "Instant Q&A", "Drafting", "Summaries"),
        summary = "Lowest-latency Turbo model for instant answers, chats, and translations.",
        requiresBlaze = false,
        isDefault = true,
      ),
      AiModelSpec(
        id = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        family = "Gemini 3",
        task = ModelTask.CHAT,
        capabilities = listOf("Balanced", "Writing", "Code", "Vision", "Cited Search"),
        summary = "Versatile multimodal model with streaming, vision, and grounded web search.",
        requiresBlaze = false,
      ),
      AiModelSpec(
        id = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        family = "Gemini 4",
        task = ModelTask.CHAT,
        capabilities = listOf("Deep Reasoning", "Advanced Code", "STEM"),
        summary = "High-reasoning flagship for complex engineering, math, and analysis.",
        requiresBlaze = true,
      ),
      AiModelSpec(
        id = "gemini-nano-banana-2.1",
        displayName = "Gemini Nano Banana 2.1",
        family = "Gemini Image",
        task = ModelTask.IMAGE,
        capabilities = listOf("Text-to-Image", "Image Editing", "1K–4K"),
        summary = "Fast, high-efficiency image synthesis and multimodal editing.",
        requiresBlaze = true,
        isDefault = true,
      ),
      AiModelSpec(
        id = "gemini-3-pro-image",
        displayName = "Gemini 3 Pro Image",
        family = "Gemini Image",
        task = ModelTask.IMAGE,
        capabilities = listOf("Studio Quality", "Typography", "Editing"),
        summary = "Pro-grade asset creation with accurate in-image text rendering.",
        requiresBlaze = true,
      ),
      AiModelSpec(
        id = "gemini-3.1-flash-tts-preview",
        displayName = "Gemini 3.1 Flash TTS",
        family = "Gemini Audio",
        task = ModelTask.VOICE,
        capabilities = listOf("Low-Latency TTS", "Bengali", "Multilingual"),
        summary = "Natural neural speech model paired with device Bengali voice synthesis.",
        requiresBlaze = false,
        isDefault = true,
      ),
    )

  const val AVAILABILITY_DISCLOSURE =
    "This workspace runs on Firebase AI Logic. Only verified Gemini models provisioned in your project are selectable; external Claude and GPT variants are not available in this catalog."

  fun chatModels(): List<AiModelSpec> = models.filter { it.task == ModelTask.CHAT }

  fun imageModels(): List<AiModelSpec> = models.filter { it.task == ModelTask.IMAGE }

  fun findById(modelId: String): AiModelSpec =
    models.firstOrNull { it.id == modelId } ?: models.first()
}
