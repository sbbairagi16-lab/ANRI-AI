package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AnriDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalWorkspaceRepositoryTest {

  private lateinit var db: AnriDatabase
  private lateinit var repo: LocalWorkspaceRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AnriDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = LocalWorkspaceRepository(db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun profile_conversations_messages_and_prompts_persist_locally() = runBlocking {
    assertTrue(repo.ensureUserProfile().isSuccess)
    val initialProfile = repo.observeUserProfile().first()
    assertNotNull(initialProfile)
    assertEquals("ANRI Creator", initialProfile?.displayName)

    assertTrue(
      repo.updateUserProfile(
        displayName = "Sayan",
        bio = "Kotlin dev",
        preferredModelId = "gemini-3.8-flash",
        bengaliVoiceEnabled = true,
        voiceSpeed = 1.0,
        voicePitch = 1.08,
        darkModeOverride = "dark",
      ).isSuccess
    )
    assertEquals("Sayan", repo.observeUserProfile().first()?.displayName)

    val convId =
      repo.appendMessage(
        conversationId = null,
        conversationTitleIfNew = "Test Chat",
        role = "user",
        content = "Hello ANRI",
        modelId = "gemini-3.8-flash",
        modelName = "Gemini 3.8 Flash",
        writingMode = "chat",
        newMessageCount = 1L,
      ).getOrThrow()

    val conversations = repo.observeConversations().first()
    assertEquals(1, conversations.size)
    assertEquals("Test Chat", conversations.first().title)

    val messages = repo.observeMessages(convId).first()
    assertEquals(1, messages.size)
    assertEquals("Hello ANRI", messages.first().content)

    val promptId = repo.savePrompt(title = "Code Review", body = "Review this snippet", category = "Coding").getOrThrow()
    assertEquals(1, repo.observeSavedPrompts().first().size)
    assertTrue(repo.deletePrompt(promptId).isSuccess)
    assertEquals(0, repo.observeSavedPrompts().first().size)

    assertTrue(repo.deleteConversation(convId).isSuccess)
    assertEquals(0, repo.observeConversations().first().size)
    assertEquals(0, repo.observeMessages(convId).first().size)
  }
}
