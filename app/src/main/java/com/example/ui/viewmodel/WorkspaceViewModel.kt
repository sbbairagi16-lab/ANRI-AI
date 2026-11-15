package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AnriAiService
import com.example.data.ai.AttachedDocument
import com.example.data.ai.ChatStreamEvent
import com.example.data.model.ChatMessage
import com.example.data.model.CitationLink
import com.example.data.model.Conversation
import com.example.data.model.SavedPrompt
import com.example.data.model.UserProfile
import com.example.data.model.WorkspaceModelCatalog
import com.example.data.model.WritingMode
import com.example.data.repository.LocalWorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatGenerationState {
  data object Idle : ChatGenerationState

  data object Connecting : ChatGenerationState

  data class Streaming(val partialText: String, val citations: List<CitationLink>) : ChatGenerationState

  data class Error(val message: String, val retryPrompt: String? = null) : ChatGenerationState
}

data class PendingAttachmentImage(
  val id: String,
  val label: String,
  val bitmap: Bitmap,
)

data class PendingAttachmentDoc(
  val id: String,
  val doc: AttachedDocument,
)

class WorkspaceViewModel(
  private val repository: LocalWorkspaceRepository,
  private val aiService: AnriAiService,
) : ViewModel() {

  private val _activeConversationId = MutableStateFlow<String?>(null)
  val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

  private val _selectedModelId = MutableStateFlow(WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID)
  val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

  private val _writingMode = MutableStateFlow(WritingMode.FAST)
  val writingMode: StateFlow<WritingMode> = _writingMode.asStateFlow()

  private val _webSearchEnabled = MutableStateFlow(false)
  val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

  private val _autoSpeakReplies = MutableStateFlow(false)
  val autoSpeakReplies: StateFlow<Boolean> = _autoSpeakReplies.asStateFlow()

  private val _composerText = MutableStateFlow("")
  val composerText: StateFlow<String> = _composerText.asStateFlow()

  private val _pendingImages = MutableStateFlow<List<PendingAttachmentImage>>(emptyList())
  val pendingImages: StateFlow<List<PendingAttachmentImage>> = _pendingImages.asStateFlow()

  private val _pendingDocs = MutableStateFlow<List<PendingAttachmentDoc>>(emptyList())
  val pendingDocs: StateFlow<List<PendingAttachmentDoc>> = _pendingDocs.asStateFlow()

  private val _generationState = MutableStateFlow<ChatGenerationState>(ChatGenerationState.Idle)
  val generationState: StateFlow<ChatGenerationState> = _generationState.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _lastCompletedReply = MutableStateFlow<String?>(null)
  val lastCompletedReply: StateFlow<String?> = _lastCompletedReply.asStateFlow()

  private var activeStreamJob: Job? = null

  val userProfile: StateFlow<UserProfile?> =
    repository
      .observeUserProfile()
      .catch { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Profile observation failed", error)
        emit(null)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val conversations: StateFlow<List<Conversation>> =
    repository
      .observeConversations()
      .catch { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Conversations observation failed", error)
        _statusBanner.value = error.message ?: "Failed to load conversations"
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeMessages: StateFlow<List<ChatMessage>> =
    _activeConversationId
      .flatMapLatest { convId ->
        if (convId.isNullOrBlank()) {
          flowOf(emptyList())
        } else {
          repository.observeMessages(convId).catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Messages observation failed", error)
            _statusBanner.value = error.message ?: "Failed to load messages"
            emit(emptyList())
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val savedPrompts: StateFlow<List<SavedPrompt>> =
    repository
      .observeSavedPrompts()
      .catch { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Saved prompts observation failed", error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  init {
    viewModelScope.launch {
      repository.ensureUserProfile()
      userProfile.collect { profile ->
        if (profile != null && _activeConversationId.value == null) {
          _selectedModelId.value = profile.preferredModelId
        }
      }
    }
  }

  fun selectConversation(conversationId: String?) {
    if (_activeConversationId.value == conversationId) return
    cancelGeneration()
    _activeConversationId.value = conversationId
    _generationState.value = ChatGenerationState.Idle
    if (conversationId != null) {
      val conv = conversations.value.firstOrNull { it.id == conversationId }
      if (conv != null) {
        _selectedModelId.value = conv.modelId
        _writingMode.value = WritingMode.fromId(conv.writingMode)
      }
    }
  }

  fun startNewConversation() {
    cancelGeneration()
    _activeConversationId.value = null
    _generationState.value = ChatGenerationState.Idle
    _pendingImages.value = emptyList()
    _pendingDocs.value = emptyList()
    _selectedModelId.value =
      userProfile.value?.preferredModelId ?: WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID
  }

  fun setComposerText(value: String) {
    _composerText.value = value
  }

  fun appendTranscriptToComposer(transcript: String) {
    val current = _composerText.value.trim()
    _composerText.value = if (current.isBlank()) transcript else "$current $transcript"
  }

  fun selectModel(modelId: String) {
    _selectedModelId.value = modelId
    val convId = _activeConversationId.value ?: return
    viewModelScope.launch {
      repository.updateConversationModelAndMode(convId, modelId, _writingMode.value.id)
    }
  }

  fun selectWritingMode(mode: WritingMode) {
    _writingMode.value = mode
    if (mode == WritingMode.RESEARCH) {
      _webSearchEnabled.value = true
    } else if (mode == WritingMode.FAST) {
      _selectedModelId.value = WorkspaceModelCatalog.FAST_MODEL_ID
      _webSearchEnabled.value = false
    }
    val convId = _activeConversationId.value ?: return
    viewModelScope.launch {
      repository.updateConversationModelAndMode(convId, _selectedModelId.value, mode.id)
    }
  }

  fun toggleWebSearch(enabled: Boolean) {
    _webSearchEnabled.value = enabled
  }

  fun toggleAutoSpeakReplies(enabled: Boolean) {
    _autoSpeakReplies.value = enabled
  }

  fun clearLastCompletedReply() {
    _lastCompletedReply.value = null
  }

  fun addPendingImage(label: String, bitmap: Bitmap) {
    _pendingImages.update { list ->
      (list + PendingAttachmentImage(UUID.randomUUID().toString(), label, bitmap)).take(4)
    }
  }

  fun removePendingImage(id: String) {
    _pendingImages.update { list -> list.filterNot { it.id == id } }
  }

  fun addPendingDocument(doc: AttachedDocument) {
    _pendingDocs.update { list ->
      (list + PendingAttachmentDoc(UUID.randomUUID().toString(), doc)).take(3)
    }
  }

  fun removePendingDocument(id: String) {
    _pendingDocs.update { list -> list.filterNot { it.id == id } }
  }

  fun clearStatusBanner() {
    _statusBanner.value = null
  }

  fun cancelGeneration() {
    activeStreamJob?.cancel()
    activeStreamJob = null
    if (_generationState.value is ChatGenerationState.Connecting ||
      _generationState.value is ChatGenerationState.Streaming
    ) {
      _generationState.value = ChatGenerationState.Idle
    }
  }

  fun retryLastMessage(onConversationCreated: ((String) -> Unit)? = null) {
    val retryText = (_generationState.value as? ChatGenerationState.Error)?.retryPrompt
    if (!retryText.isNullOrBlank()) {
      sendMessage(
        overridePrompt = retryText,
        skipSavingUserTurn = true,
        onConversationCreated = onConversationCreated,
      )
    }
  }

  fun sendMessage(
    overridePrompt: String? = null,
    skipSavingUserTurn: Boolean = false,
    onConversationCreated: ((String) -> Unit)? = null,
  ) {
    val prompt = (overridePrompt ?: _composerText.value).trim()
    val imagesToSend = _pendingImages.value
    val docsToSend = _pendingDocs.value
    if (prompt.isBlank() && imagesToSend.isEmpty() && docsToSend.isEmpty()) return
    val effectivePrompt =
      if (prompt.isBlank()) "Please analyze the attached media/files." else prompt

    val existingConvId = _activeConversationId.value
    val resolvedConvId =
      if (existingConvId.isNullOrBlank()) UUID.randomUUID().toString() else existingConvId
    val historySnapshot = activeMessages.value
    val modelSpec = WorkspaceModelCatalog.findById(_selectedModelId.value)
    val modeSnapshot = _writingMode.value
    val searchEnabled = _webSearchEnabled.value || modeSnapshot == WritingMode.RESEARCH
    val bio = userProfile.value?.bio.orEmpty()

    if (overridePrompt == null) {
      _composerText.value = ""
    }
    _pendingImages.value = emptyList()
    _pendingDocs.value = emptyList()

    val attachmentLabels =
      imagesToSend.map { it.label } + docsToSend.map { it.doc.displayName }

    if (existingConvId == null) {
      _activeConversationId.value = resolvedConvId
      onConversationCreated?.invoke(resolvedConvId)
    }

    activeStreamJob?.cancel()
    activeStreamJob =
      viewModelScope.launch {
        _generationState.value = ChatGenerationState.Connecting
        val titleIfNew =
          effectivePrompt
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .take(56)
            .ifBlank { "New Conversation" }

        // Save user message concurrently so AI streaming begins immediately with 0ms DB latency
        val userSaveJob =
          if (!skipSavingUserTurn) {
            async {
              repository.appendMessage(
                conversationId = resolvedConvId,
                conversationTitleIfNew = titleIfNew,
                role = "user",
                content = effectivePrompt,
                modelId = modelSpec.id,
                modelName = modelSpec.displayName,
                writingMode = modeSnapshot.id,
                newMessageCount = historySnapshot.size.toLong() + 1L,
                attachmentNames = attachmentLabels,
              )
            }
          } else {
            null
          }

        try {
          var finalAssistantText = ""
          var finalCitations = emptyList<CitationLink>()

          aiService
            .streamChatResponse(
              modelId = modelSpec.id,
              writingMode = modeSnapshot,
              userBio = bio,
              history = historySnapshot,
              userPrompt = effectivePrompt,
              attachedImages = imagesToSend.map { it.bitmap },
              attachedDocuments = docsToSend.map { it.doc },
              enableWebSearch = searchEnabled,
            )
            .collect { event ->
              when (event) {
                is ChatStreamEvent.Delta -> {
                  _generationState.value =
                    ChatGenerationState.Streaming(event.text, event.citations)
                }
                is ChatStreamEvent.Completed -> {
                  finalAssistantText = event.fullText
                  finalCitations = event.citations
                }
              }
            }

          userSaveJob?.await()

          if (finalAssistantText.isNotBlank()) {
            val expectedCount =
              if (skipSavingUserTurn) historySnapshot.size.toLong() + 1L
              else historySnapshot.size.toLong() + 2L
            repository.appendMessage(
              conversationId = resolvedConvId,
              conversationTitleIfNew = titleIfNew,
              role = "assistant",
              content = finalAssistantText,
              modelId = modelSpec.id,
              modelName = modelSpec.displayName,
              writingMode = modeSnapshot.id,
              newMessageCount = expectedCount,
              citationTitles = finalCitations.map { it.title },
              citationUrls = finalCitations.map { it.url },
            )
            if (_autoSpeakReplies.value) {
              _lastCompletedReply.value = finalAssistantText
            }
          }
          _generationState.value = ChatGenerationState.Idle
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "AI generation failed", e)
          userSaveJob?.await()
          _generationState.value =
            ChatGenerationState.Error(
              message = e.localizedMessage ?: "AI generation failed. Tap Retry.",
              retryPrompt = effectivePrompt,
            )
        }
      }
  }

  fun buildShareTranscript(): String {
    val title =
      conversations.value.firstOrNull { it.id == _activeConversationId.value }?.title
        ?: "ANRI AI Conversation"
    val body =
      activeMessages.value.joinToString("\n\n") { msg ->
        val speaker = if (msg.role == "user") "You" else "ANRI (${msg.modelName})"
        "### $speaker\n${msg.content}"
      }
    return "# $title\n\n$body".trim()
  }

  fun togglePinConversation(conversation: Conversation) {
    viewModelScope.launch {
      repository.updateConversationPinAndTitle(
        conversationId = conversation.id,
        title = conversation.title,
        pinned = !conversation.pinned,
      )
    }
  }

  fun renameConversation(conversation: Conversation, newTitle: String) {
    viewModelScope.launch {
      repository.updateConversationPinAndTitle(
        conversationId = conversation.id,
        title = newTitle,
        pinned = conversation.pinned,
      )
    }
  }

  fun deleteConversation(conversationId: String) {
    viewModelScope.launch {
      if (_activeConversationId.value == conversationId) {
        startNewConversation()
      }
      repository.deleteConversation(conversationId)
    }
  }

  fun savePrompt(promptId: String? = null, title: String, body: String, category: String) {
    viewModelScope.launch {
      val res = repository.savePrompt(promptId, title, body, category)
      _statusBanner.value =
        if (res.isSuccess) "Saved prompt to your workspace."
        else res.exceptionOrNull()?.localizedMessage ?: "Failed to save prompt."
    }
  }

  fun deletePrompt(promptId: String) {
    viewModelScope.launch { repository.deletePrompt(promptId) }
  }

  fun saveProfileSettings(
    displayName: String,
    bio: String,
    preferredModelId: String,
    bengaliVoiceEnabled: Boolean,
    voiceSpeed: Double,
    voicePitch: Double,
    darkModeOverride: String,
  ) {
    viewModelScope.launch {
      val res =
        repository.updateUserProfile(
          displayName = displayName,
          bio = bio,
          preferredModelId = preferredModelId,
          bengaliVoiceEnabled = bengaliVoiceEnabled,
          voiceSpeed = voiceSpeed,
          voicePitch = voicePitch,
          darkModeOverride = darkModeOverride,
        )
      _statusBanner.value =
        if (res.isSuccess) "Profile settings saved."
        else res.exceptionOrNull()?.localizedMessage ?: "Failed to save profile settings."
    }
  }

  companion object {
    private const val TAG = "WorkspaceVM"

    fun provideFactory(
      repository: LocalWorkspaceRepository,
      aiService: AnriAiService,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        WorkspaceViewModel(
          repository = repository,
          aiService = aiService,
        )
      }
    }
  }
}
