package com.example.ui.workspace

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.service.ScreenShareService
import com.example.ui.viewmodel.LiveVoicePhase
import com.example.ui.viewmodel.LiveVoiceViewModel

@Composable
fun HoloFaceScreen(
  voiceViewModel: LiveVoiceViewModel,
  profile: UserProfile?,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val livePhase by voiceViewModel.phase.collectAsStateWithLifecycle()
  val turns by voiceViewModel.turns.collectAsStateWithLifecycle()
  val streamingReply by voiceViewModel.streamingReply.collectAsStateWithLifecycle()
  val preferBengali by voiceViewModel.preferBengali.collectAsStateWithLifecycle()
  val directAudioMode by voiceViewModel.directGeminiAudioMode.collectAsStateWithLifecycle()
  val isCasting by voiceViewModel.isScreenCasting.collectAsStateWithLifecycle()
  val errorMessage by voiceViewModel.errorMessage.collectAsStateWithLifecycle()

  var personaMode by remember { mutableStateOf(HoloPersonaMode.JARVIS) }
  var previewPhaseOverride by remember { mutableStateOf<LiveVoicePhase?>(null) }
  var wireframeEnabled by remember { mutableStateOf(true) }
  var resetCameraTick by remember { mutableIntStateOf(0) }
  var controlsExpanded by remember { mutableStateOf(true) }
  var quickInput by remember { mutableStateOf("") }

  LaunchedEffect(profile) {
    if (profile != null) {
      voiceViewModel.syncVoiceSettings(
        useBengali = profile.bengaliVoiceEnabled,
        speed = profile.voiceSpeed,
        pitch = profile.voicePitch,
      )
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      voiceViewModel.stopSession()
    }
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        previewPhaseOverride = null
        voiceViewModel.startListeningTurn()
      }
    }

  val screenCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        ScreenShareService.start(context, result.resultCode, result.data!!)
      }
    }

  val effectivePhase =
    if (livePhase != LiveVoicePhase.IDLE) livePhase else (previewPhaseOverride ?: LiveVoicePhase.IDLE)
  val latestReply =
    remember(streamingReply, turns) {
      streamingReply.ifBlank { turns.lastOrNull { it.role == "assistant" }?.text.orEmpty() }
    }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { _ ->
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
      // Full-bleed interactive Three.js WebGL ANRI Anime x J.A.R.V.I.S. Face
      HoloFaceCanvas(
        personaMode = personaMode,
        phase = effectivePhase,
        wireframeEnabled = wireframeEnabled,
        resetCameraTick = resetCameraTick,
        modifier = Modifier.fillMaxSize(),
      )

      // Compact Collapsible Floating Control Dock (solid/rgba surface over WebGL)
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        modifier =
          Modifier.align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .anriGlowBorder(shape = RoundedCornerShape(18.dp), width = 1.dp, elevation = 8.dp, intensity = 0.45f),
      ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("holo_back_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back to workspace",
                  tint = MaterialTheme.colorScheme.onSurface,
                )
              }
              Column {
                Text(
                  text = "ANRI AI · 3D Core",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    when {
                      isCasting && effectivePhase == LiveVoicePhase.IDLE -> "📺 Live Screen Cast ON · Drag to orbit"
                      effectivePhase == LiveVoicePhase.IDLE -> "${personaMode.label} · Drag to orbit"
                      effectivePhase == LiveVoicePhase.LISTENING -> "🎙️ Listening to you..."
                      effectivePhase == LiveVoicePhase.THINKING -> "⚡ Chakra Processing..."
                      else -> "🔊 Speaking (${if (preferBengali) "বাংলা" else "EN"})"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = {
                  if (isCasting) {
                    ScreenShareService.stop(context)
                  } else {
                    val manager =
                      context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                    screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                  }
                },
                modifier = Modifier.testTag("holo_cast_toggle_button"),
              ) {
                Icon(
                  imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                  contentDescription = if (isCasting) "Stop live cast" else "Start live screen cast",
                  tint =
                    if (isCasting) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { wireframeEnabled = !wireframeEnabled },
                modifier = Modifier.testTag("holo_wireframe_toggle"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Grid4x4,
                  contentDescription = "Toggle J.A.R.V.I.S. holo lattice",
                  tint =
                    if (wireframeEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { resetCameraTick += 1 },
                modifier = Modifier.testTag("holo_reset_camera_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.CenterFocusStrong,
                  contentDescription = "Reset 3D camera",
                  tint = MaterialTheme.colorScheme.secondary,
                )
              }
              IconButton(
                onClick = { controlsExpanded = !controlsExpanded },
                modifier = Modifier.testTag("holo_toggle_dock_button"),
              ) {
                Icon(
                  imageVector =
                    if (controlsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                  contentDescription = if (controlsExpanded) "Collapse controls" else "Expand controls",
                )
              }
            }
          }

          AnimatedVisibility(visible = controlsExpanded) {
            Column(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                HoloPersonaMode.entries.forEach { mode ->
                  FilterChip(
                    selected = personaMode == mode,
                    onClick = { personaMode = mode },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("holo_mode_${mode.id}"),
                  )
                }
              }

              // State demo / live override strip
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                listOf(
                  null to "Auto Live",
                  LiveVoicePhase.THINKING to "⚡ Think FX",
                  LiveVoicePhase.SPEAKING to "Speak FX",
                ).forEach { (stateOpt, label) ->
                  FilterChip(
                    selected = previewPhaseOverride == stateOpt && livePhase == LiveVoicePhase.IDLE,
                    onClick = { previewPhaseOverride = stateOpt },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                  )
                }
              }
            }
          }
        }
      }

      // Bottom Live Interaction Overlay (Streaming Subtitle + Direct Talk Bar)
      Column(
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (errorMessage != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        } else if (latestReply.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            modifier =
              Modifier.fillMaxWidth()
                .anriGlowBorder(shape = RoundedCornerShape(14.dp), width = 1.dp, elevation = 6.dp, intensity = 0.4f),
          ) {
            Text(
              text = "ANRI: $latestReply",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
          modifier =
            Modifier.fillMaxWidth()
              .anriGlowBorder(shape = RoundedCornerShape(18.dp), width = 1.dp, elevation = 8.dp, intensity = 0.5f),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val isMicActive = livePhase != LiveVoicePhase.IDLE
            Surface(
              shape = CircleShape,
              color =
                when (livePhase) {
                  LiveVoicePhase.LISTENING -> MaterialTheme.colorScheme.secondary
                  LiveVoicePhase.SPEAKING,
                  LiveVoicePhase.THINKING -> MaterialTheme.colorScheme.error
                  LiveVoicePhase.IDLE -> MaterialTheme.colorScheme.primaryContainer
                },
              modifier =
                Modifier.size(48.dp)
                  .clickable {
                    previewPhaseOverride = null
                    when (livePhase) {
                      LiveVoicePhase.LISTENING -> {
                        if (directAudioMode) voiceViewModel.finishListeningEarly()
                        else voiceViewModel.stopSession()
                      }
                      LiveVoicePhase.THINKING,
                      LiveVoicePhase.SPEAKING -> voiceViewModel.startListeningTurn()
                      LiveVoicePhase.IDLE -> {
                        val granted =
                          ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                          ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                          voiceViewModel.startListeningTurn()
                        } else {
                          permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                      }
                    }
                  }
                  .testTag("holo_voice_mic_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector =
                    when (livePhase) {
                      LiveVoicePhase.LISTENING ->
                        if (directAudioMode) Icons.Filled.Bolt else Icons.Filled.Stop
                      LiveVoicePhase.SPEAKING,
                      LiveVoicePhase.THINKING -> Icons.Filled.Stop
                      LiveVoicePhase.IDLE -> Icons.Filled.Mic
                    },
                  contentDescription = if (isMicActive) "Stop or send voice" else "Speak to 3D ANRI",
                  tint =
                    if (isMicActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            OutlinedTextField(
              value = quickInput,
              onValueChange = { quickInput = it },
              placeholder = {
                Text(
                  if (preferBengali) "ANRI-কে বলুন বা লিখুন..." else "Ask 3D ANRI anything..."
                )
              },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
              modifier = Modifier.weight(1f).testTag("holo_text_input"),
            )

            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier =
                Modifier.size(48.dp)
                  .clickable {
                    val clean = quickInput.trim()
                    if (clean.isNotEmpty()) {
                      previewPhaseOverride = null
                      quickInput = ""
                      voiceViewModel.submitSpokenText(clean)
                    }
                  }
                  .testTag("holo_send_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Send to 3D ANRI",
                  tint = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }
          }
        }
      }
    }
  }
}
