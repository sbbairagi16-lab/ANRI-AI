package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnriDao {

  @Query("SELECT * FROM user_profile WHERE id = 'local_profile' LIMIT 1")
  fun observeProfile(): Flow<UserProfileEntity?>

  @Query("SELECT * FROM user_profile WHERE id = 'local_profile' LIMIT 1")
  suspend fun getProfile(): UserProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfile(profile: UserProfileEntity)

  @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAtMillis DESC")
  fun observeConversations(): Flow<List<ConversationEntity>>

  @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
  suspend fun getConversation(conversationId: String): ConversationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertConversation(conversation: ConversationEntity)

  @Query("DELETE FROM conversations WHERE id = :conversationId")
  suspend fun deleteConversation(conversationId: String)

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC")
  fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity)

  @Query("SELECT * FROM saved_prompts ORDER BY updatedAtMillis DESC")
  fun observeSavedPrompts(): Flow<List<SavedPromptEntity>>

  @Query("SELECT * FROM saved_prompts WHERE id = :promptId LIMIT 1")
  suspend fun getSavedPrompt(promptId: String): SavedPromptEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSavedPrompt(prompt: SavedPromptEntity)

  @Query("DELETE FROM saved_prompts WHERE id = :promptId")
  suspend fun deleteSavedPrompt(promptId: String)
}
