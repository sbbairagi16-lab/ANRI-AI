package com.example

import com.example.data.ai.AnriAiService
import com.example.data.model.AiToolkitAction
import com.example.data.model.ImageStylePreset
import com.example.data.model.ModelTask
import com.example.data.model.WorkspaceModelCatalog
import com.example.data.model.WritingMode
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.workspace.HoloPersonaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun workspaceCatalog_onlyContainsVerifiedAvailableModels_andDefaultsToFast() {
    val chatModels = WorkspaceModelCatalog.chatModels()
    assertEquals(WorkspaceModelCatalog.FAST_MODEL_ID, WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID)
    assertTrue(chatModels.any { it.id == "gemini-3.5-flash-lite" })
    assertTrue(chatModels.any { it.id == "gemini-3.8-flash" })
    assertTrue(chatModels.all { it.task == ModelTask.CHAT })
    assertFalse(chatModels.any { it.id.contains("claude", ignoreCase = true) })
    assertFalse(chatModels.any { it.id.contains("gpt", ignoreCase = true) })
  }

  @Test
  fun studioViewModel_enforces10SecondVideoDurationCap_andStylePreset() {
    val studioVm = StudioViewModel(AnriAiService())
    studioVm.setVideoDuration(25)
    assertEquals(10, studioVm.videoDurationSeconds.value)

    studioVm.setVideoDuration(0)
    assertEquals(1, studioVm.videoDurationSeconds.value)

    studioVm.setVideoDuration(7)
    assertEquals(7, studioVm.videoDurationSeconds.value)

    assertEquals(ImageStylePreset.CYBER_GLOW, studioVm.stylePreset.value)
    studioVm.selectStylePreset(ImageStylePreset.LOGO_BADGE)
    assertEquals(ImageStylePreset.LOGO_BADGE, studioVm.stylePreset.value)
  }

  @Test
  fun writingMode_andAiToolkit_andHoloModes_resolveExpectedValues() {
    assertEquals(WritingMode.FAST, WritingMode.fromId("fast"))
    assertEquals(WritingMode.BENGALI, WritingMode.fromId("bengali"))
    assertEquals(WritingMode.RESEARCH, WritingMode.fromId("research"))
    assertEquals(WritingMode.CHAT, WritingMode.fromId("unknown"))

    assertEquals(6, AiToolkitAction.entries.size)
    assertTrue(AiToolkitAction.entries.all { it.promptPrefix.isNotBlank() && it.title.isNotBlank() })

    assertEquals(
      listOf("jarvis", "chakra", "sharingan", "sage"),
      HoloPersonaMode.entries.map { it.id },
    )
  }
}
