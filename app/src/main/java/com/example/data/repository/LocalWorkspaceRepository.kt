package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AnriDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.SavedPromptEntity
import com.example.data.local.UserProfileEntity
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.SavedPrompt
import com.example.data.model.UserProfile
import com.example.data.model.WorkspaceModelCatalog
import com.example.data.model.WritingMode
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalWorkspaceRepository(
  private val database: AnriDatabase,
) {
  private val dao = database.anriDao()

  fun observeUserProfile(): Flow<UserProfile?> =
    dao.observeProfile().map { it?.toDomain() }

  suspend fun ensureUserProfile(): Result<Unit> {
    return try {
      val existing = dao.getProfile()
      if (existing == null) {
        val now = System.currentTimeMillis()
        dao.upsertProfile(
          UserProfileEntity(
            id = "local_profile",
            displayName = "ANRI Creator",
            bio = "",
            preferredModelId = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
            bengaliVoiceEnabled = true,
            voiceSpeed = 1.0,
            voicePitch = 1.08,
            darkModeOverride = "dark",
            createdAtMillis = now,
            updatedAtMillis = now,
          )
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateUserProfile(
    displayName: String,
    bio: String,
    preferredModelId: String,
    bengaliVoiceEnabled: Boolean,
    voiceSpeed: Double,
    voicePitch: Double,
    darkModeOverride: String,
  ): Result<Unit> {
    return try {
      val now = System.currentTimeMillis()
      val existing = dao.getProfile()
      dao.upsertProfile(
        UserProfileEntity(
          id = "local_profile",
          displayName = displayName.trim().ifBlank { "ANRI Creator" }.take(80),
          bio = bio.trim().take(300),
          preferredModelId = preferredModelId,
          bengaliVoiceEnabled = bengaliVoiceEnabled,
          voiceSpeed = voiceSpeed.coerceIn(0.5, 2.0),
          voicePitch = voicePitch.coerceIn(0.5, 2.0),
          darkModeOverride = darkModeOverride,
          createdAtMillis = existing?.createdAtMillis ?: now,
          updatedAtMillis = now,
        )
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun observeConversations(): Flow<List<Conversation>> =
    dao.observeConversations().map { entities -> entities.map { it.toDomain() } }

  fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
    dao.observeMessages(conversationId).map { entities -> entities.map { it.toDomain() } }

  suspend fun createConversation(
    title: String,
    modelId: String,
    writingMode: String = WritingMode.CHAT.id,
  ): Result<String> {
    return try {
      val id = UUID.randomUUID().toString()
      val now = System.currentTimeMillis()
      dao.upsertConversation(
        ConversationEntity(
          id = id,
          title = title.trim().ifBlank { "New Conversation" }.take(120),
          modelId = modelId,
          lastMessagePreview = "",
          messageCount = 0L,
          pinned = false,
          writingMode = writingMode,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      )
      Result.success(id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun appendMessage(
    conversationId: String?,
    conversationTitleIfNew: String,
    role: String,
    content: String,
    modelId: String,
    modelName: String,
    writingMode: String,
    newMessageCount: Long,
    attachmentNames: List<String> = emptyList(),
    citationTitles: List<String> = emptyList(),
    citationUrls: List<String> = emptyList(),
  ): Result<String> {
    return try {
      val resolvedId =
        if (conversationId.isNullOrBlank()) UUID.randomUUID().toString() else conversationId
      val now = System.currentTimeMillis()
      val safePreview = content.replace("\n", " ").trim().take(280)

      database.withTransaction {
        val existingConv = dao.getConversation(resolvedId)
        if (existingConv == null) {
          dao.upsertConversation(
            ConversationEntity(
              id = resolvedId,
              title = conversationTitleIfNew.trim().ifBlank { "New Conversation" }.take(120),
              modelId = modelId,
              lastMessagePreview = safePreview,
              messageCount = newMessageCount,
              pinned = false,
              writingMode = writingMode,
              createdAtMillis = now,
              updatedAtMillis = now,
            )
          )
        } else {
          dao.upsertConversation(
            existingConv.copy(
              modelId = modelId,
              lastMessagePreview = safePreview,
              messageCount = newMessageCount,
              writingMode = writingMode,
              updatedAtMillis = now,
            )
          )
        }

        dao.insertMessage(
          ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = resolvedId,
            role = role,
            content = content.take(24900),
            modelId = modelId.take(64),
            modelName = modelName.take(80),
            attachmentNames = attachmentNames.take(10).map { it.take(200) },
            citationTitles = citationTitles.take(10).map { it.take(200) },
            citationUrls = citationUrls.take(10).map { it.take(500) },
            createdAtMillis = now,
          )
        )
      }
      Result.success(resolvedId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateConversationPinAndTitle(
    conversationId: String,
    title: String,
    pinned: Boolean,
  ): Result<Unit> {
    return try {
      val existing = dao.getConversation(conversationId)
      if (existing != null) {
        dao.upsertConversation(
          existing.copy(
            title = title.trim().ifBlank { "Untitled" }.take(120),
            pinned = pinned,
            updatedAtMillis = System.currentTimeMillis(),
          )
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateConversationModelAndMode(
    conversationId: String,
    modelId: String,
    writingMode: String,
  ): Result<Unit> {
    return try {
      val existing = dao.getConversation(conversationId)
      if (existing != null) {
        dao.upsertConversation(
          existing.copy(
            modelId = modelId,
            writingMode = writingMode,
            updatedAtMillis = System.currentTimeMillis(),
          )
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteConversation(conversationId: String): Result<Unit> {
    return try {
      dao.deleteConversation(conversationId)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun observeSavedPrompts(): Flow<List<SavedPrompt>> =
    dao.observeSavedPrompts().map { entities -> entities.map { it.toDomain() } }

  suspend fun savePrompt(
    promptId: String? = null,
    title: String,
    body: String,
    category: String,
  ): Result<String> {
    return try {
      val resolvedId = if (promptId.isNullOrBlank()) UUID.randomUUID().toString() else promptId
      val now = System.currentTimeMillis()
      val existing = if (promptId.isNullOrBlank()) null else dao.getSavedPrompt(promptId)
      dao.upsertSavedPrompt(
        SavedPromptEntity(
          id = resolvedId,
          title = title.trim().ifBlank { "Untitled Prompt" }.take(100),
          body = body.trim().ifBlank { "Help me with..." }.take(4000),
          category = category.trim().ifBlank { "Writing" }.take(40),
          createdAtMillis = existing?.createdAtMillis ?: now,
          updatedAtMillis = now,
        )
      )
      Result.success(resolvedId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deletePrompt(promptId: String): Result<Unit> {
    return try {
      dao.deleteSavedPrompt(promptId)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
