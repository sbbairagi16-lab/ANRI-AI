package com.example.ui.workspace

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.assist.AnriAssistOverlayActivity
import com.example.data.model.ModelTask
import com.example.data.model.WorkspaceModelCatalog
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.voice.BengaliVoiceController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
  viewModel: WorkspaceViewModel,
  voiceController: BengaliVoiceController,
) {
  val context = LocalContext.current
  val profile by viewModel.userProfile.collectAsStateWithLifecycle()
  val activeVoiceLabel by voiceController.activeVoiceLabel.collectAsStateWithLifecycle()

  var displayName by remember { mutableStateOf(profile?.displayName ?: "ANRI Creator") }
  var bio by remember { mutableStateOf(profile?.bio ?: "") }
  var preferredModelId by remember {
    mutableStateOf(profile?.preferredModelId ?: WorkspaceModelCatalog.DEFAULT_CHAT_MODEL_ID)
  }
  var bengaliVoiceEnabled by remember { mutableStateOf(profile?.bengaliVoiceEnabled ?: true) }
  var voiceSpeed by remember { mutableDoubleStateOf(profile?.voiceSpeed ?: 1.0) }
  var voicePitch by remember { mutableDoubleStateOf(profile?.voicePitch ?: 1.08) }
  var darkModeOverride by remember { mutableStateOf(profile?.darkModeOverride ?: "dark") }
  var showModelSheet by remember { mutableStateOf(false) }

  LaunchedEffect(profile) {
    profile?.let {
      displayName = it.displayName
      bio = it.bio
      preferredModelId = it.preferredModelId
      bengaliVoiceEnabled = it.bengaliVoiceEnabled
      voiceSpeed = it.voiceSpeed
      voicePitch = it.voicePitch
      darkModeOverride = it.darkModeOverride
    }
  }

  val modelSpec = remember(preferredModelId) { WorkspaceModelCatalog.findById(preferredModelId) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Profile & Workspace Settings") },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    }
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .anriAuroraBackground()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Workspace Header Card
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.fillMaxWidth()
            .anriGlowBorder(shape = RoundedCornerShape(18.dp), width = 1.5.dp, intensity = 0.55f),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_anri_avatar),
            contentDescription = "ANRI AI emblem",
            modifier =
              Modifier.size(54.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentScale = ContentScale.Crop,
          )
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "On-device workspace · ANRI AI",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Personal Identity & Custom Instructions
      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text("Display Name") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
      )

      OutlinedTextField(
        value = bio,
        onValueChange = { bio = it },
        label = { Text("Personal Instructions for ANRI AI") },
        placeholder = { Text("e.g., I am an Android developer; prefer concise Kotlin examples.") },
        minLines = 3,
        maxLines = 4,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
      )

      // Default Chat Model
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { showModelSheet = true },
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Default Chat Model",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(modelSpec.displayName, style = MaterialTheme.typography.titleMedium)
          }
          Icon(
            Icons.Filled.Tune,
            contentDescription = "Change default model",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      // Theme Override
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Workspace Theme", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("dark" to "Obsidian Dark", "light" to "Alabaster Light", "system" to "System")
            .forEach { (key, label) ->
              FilterChip(
                selected = darkModeOverride == key,
                onClick = { darkModeOverride = key },
                label = { Text(label) },
              )
            }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Feminine Bengali Voice Settings
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.fillMaxWidth()
            .anriGlowBorder(shape = RoundedCornerShape(16.dp), intensity = 0.38f),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Feminine Bengali Voice (বাংলা)", style = MaterialTheme.typography.titleSmall)
              Text(
                text = activeVoiceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Switch(
              checked = bengaliVoiceEnabled,
              onCheckedChange = { bengaliVoiceEnabled = it },
            )
          }

          Text("Speed: ${"%.2f".format(voiceSpeed)}x", style = MaterialTheme.typography.labelMedium)
          Slider(
            value = voiceSpeed.toFloat(),
            onValueChange = { voiceSpeed = it.toDouble() },
            valueRange = 0.6f..1.6f,
          )

          Text("Pitch: ${"%.2f".format(voicePitch)}x", style = MaterialTheme.typography.labelMedium)
          Slider(
            value = voicePitch.toFloat(),
            onValueChange = { voicePitch = it.toDouble() },
            valueRange = 0.7f..1.5f,
          )

          OutlinedButton(
            onClick = {
              voiceController.configureVoice(
                useBengali = bengaliVoiceEnabled,
                speed = voiceSpeed.toFloat(),
                pitch = voicePitch.toFloat(),
              )
              val sample =
                if (bengaliVoiceEnabled) "নমস্কার! আমি আনরি এআই, আপনার ব্যক্তিগত সহকারী।"
                else "Hello! I am ANRI AI, your personal creative workspace assistant."
              voiceController.speak(sample)
            }
          ) {
            Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Voice")
          }
        }
      }

      // Power Button -> Live Screen Cast Assistant Setup
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier.fillMaxWidth()
            .anriGlowBorder(shape = RoundedCornerShape(16.dp), intensity = 0.42f),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Filled.PowerSettingsNew,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "Power Button Live Screen Cast (Gemini-Style)",
              style = MaterialTheme.typography.titleSmall,
            )
          }
          Text(
            text =
              "Set ANRI AI as Android's Default Digital Assistant so holding the Power button launches the floating Live Screen Cast & Voice overlay over any app. You can also add the 'ANRI Live Cast' Quick Settings tile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = {
                runCatching {
                  context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                }
              },
              modifier = Modifier.testTag("settings_bind_power_assistant_button"),
            ) {
              Text("Set Power Assist")
            }
            OutlinedButton(
              onClick = {
                context.startActivity(
                  Intent(context, AnriAssistOverlayActivity::class.java).apply {
                    action = AnriAssistOverlayActivity.ACTION_POWER_LIVE_CAST
                    putExtra(AnriAssistOverlayActivity.EXTRA_AUTO_CAST, true)
                  }
                )
              },
              modifier = Modifier.testTag("settings_test_live_cast_button"),
            ) {
              Icon(Icons.Filled.Cast, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Launch Cast Overlay")
            }
          }
        }
      }

      Button(
        onClick = {
          viewModel.saveProfileSettings(
            displayName = displayName,
            bio = bio,
            preferredModelId = preferredModelId,
            bengaliVoiceEnabled = bengaliVoiceEnabled,
            voiceSpeed = voiceSpeed,
            voicePitch = voicePitch,
            darkModeOverride = darkModeOverride,
          )
        },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(Icons.Filled.Check, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save Workspace Settings")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showModelSheet) {
    ModelSelectorSheet(
      selectedModelId = preferredModelId,
      taskFilter = ModelTask.CHAT,
      onSelectModel = { preferredModelId = it.id },
      onDismiss = { showModelSheet = false },
    )
  }
}
