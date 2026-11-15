package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.SavedPrompt
import com.example.data.model.UserProfile
import com.example.data.model.WorkspaceModelCatalog
import com.example.data.model.WritingMode
import org.json.JSONArray

@Entity(tableName = "user_profile")
data class UserProfileEntity(
  @PrimaryKey val id: String = "local_profile",
  val displayName: String = "ANRI Creator",
  val bio: String = "",
  val preferredModelId: String = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
  val bengaliVoiceEnabled: Boolean = true,
  val voiceSpeed: Double = 1.0,
  val voicePitch: Double = 1.08,
  val darkModeOverride: String = "dark",
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
) {
  fun toDomain(): UserProfile =
    UserProfile(
      id = id,
      displayName = displayName,
      bio = bio,
      preferredModelId = preferredModelId,
      bengaliVoiceEnabled = bengaliVoiceEnabled,
      voiceSpeed = voiceSpeed,
      voicePitch = voicePitch,
      darkModeOverride = darkModeOverride,
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis,
    )
}

@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val title: String = "New Conversation",
  val modelId: String = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
  val lastMessagePreview: String = "",
  val messageCount: Long = 0L,
  val pinned: Boolean = false,
  val writingMode: String = WritingMode.CHAT.id,
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
) {
  fun toDomain(): Conversation =
    Conversation(
      id = id,
      title = title,
      modelId = modelId,
      lastMessagePreview = lastMessagePreview,
      messageCount = messageCount,
      pinned = pinned,
      writingMode = writingMode,
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis,
    )
}

@Entity(
  tableName = "messages",
  foreignKeys =
    [
      ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["conversationId"])],
)
data class ChatMessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val role: String = "user",
  val content: String = "",
  val modelId: String = WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID,
  val modelName: String = "Gemini 3.8 Flash",
  val attachmentNames: List<String> = emptyList(),
  val citationTitles: List<String> = emptyList(),
  val citationUrls: List<String> = emptyList(),
  val createdAtMillis: Long = 0L,
) {
  fun toDomain(): ChatMessage =
    ChatMessage(
      id = id,
      conversationId = conversationId,
      role = role,
      content = content,
      modelId = modelId,
      modelName = modelName,
      attachmentNames = attachmentNames,
      citationTitles = citationTitles,
      citationUrls = citationUrls,
      createdAtMillis = createdAtMillis,
    )
}

@Entity(tableName = "saved_prompts")
data class SavedPromptEntity(
  @PrimaryKey val id: String,
  val title: String = "",
  val body: String = "",
  val category: String = "Writing",
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
) {
  fun toDomain(): SavedPrompt =
    SavedPrompt(
      id = id,
      title = title,
      body = body,
      category = category,
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis,
    )
}

class AnriTypeConverters {
  @TypeConverter
  fun fromStringList(list: List<String>): String {
    val array = JSONArray()
    list.forEach { array.put(it) }
    return array.toString()
  }

  @TypeConverter
  fun toStringList(serialized: String): List<String> {
    if (serialized.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(serialized)
        (0 until array.length()).map { idx -> array.optString(idx, "") }
      }
      .getOrDefault(emptyList())
  }
}
