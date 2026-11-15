package com.example.ui.workspace

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.example.R
import com.example.assist.AnriAssistOverlayActivity
import com.example.data.model.UserProfile
import com.example.service.ScreenShareService
import com.example.ui.viewmodel.LiveVoicePhase
import com.example.ui.viewmodel.LiveVoiceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceScreen(
  viewModel: LiveVoiceViewModel,
  profile: UserProfile?,
  onOpenFullHoloFace: () -> Unit = {},
) {
  val context = LocalContext.current
  val phase by viewModel.phase.collectAsStateWithLifecycle()
  val turns by viewModel.turns.collectAsStateWithLifecycle()
  val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
  val preferBengali by viewModel.preferBengali.collectAsStateWithLifecycle()
  val autoLoop by viewModel.autoLoopEnabled.collectAsStateWithLifecycle()
  val directAudioMode by viewModel.directGeminiAudioMode.collectAsStateWithLifecycle()
  val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
  val isCasting by viewModel.isScreenCasting.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val activeVoiceLabel by viewModel.voiceController.activeVoiceLabel.collectAsStateWithLifecycle()
  val partialTranscript by viewModel.voiceController.partialTranscript.collectAsStateWithLifecycle()

  var textInput by remember { mutableStateOf("") }
  var show3dAvatar by remember { mutableStateOf(true) }
  var personaMode by remember { mutableStateOf(HoloPersonaMode.JARVIS) }

  LaunchedEffect(profile) {
    if (profile != null) {
      viewModel.syncVoiceSettings(
        useBengali = profile.bengaliVoiceEnabled,
        speed = profile.voiceSpeed,
        pitch = profile.voicePitch,
      )
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      viewModel.stopSession()
    }
  }

  val listState = rememberLazyListState()
  LaunchedEffect(turns.size, streamingReply) {
    if (turns.isNotEmpty() || streamingReply.isNotBlank()) {
      listState.animateScrollToItem((turns.size).coerceAtLeast(0))
    }
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        viewModel.startListeningTurn()
      }
    }

  val screenCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        ScreenShareService.start(context, result.resultCode, result.data!!)
      }
    }

  val pulseTransition = rememberInfiniteTransition(label = "voiceOrb")
  val pulseScale by
    pulseTransition.animateFloat(
      initialValue = 1f,
      targetValue = 1.24f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 820),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulseScale",
    )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Live Two-Way Voice")
            Text(
              text =
                if (isCasting) "Live Screen Cast ON · $activeVoiceLabel" else activeVoiceLabel,
              style = MaterialTheme.typography.labelSmall,
              color =
                if (isCasting) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
            )
          }
        },
        actions = {
          // Live Screen Cast toggle right inside Live Voice
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
            modifier = Modifier.testTag("voice_live_cast_button"),
          ) {
            Icon(
              imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast,
              contentDescription = if (isCasting) "Stop live screen cast" else "Start live screen cast",
              tint =
                if (isCasting) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = onOpenFullHoloFace,
            modifier = Modifier.testTag("voice_full_3d_button"),
          ) {
            Icon(
              Icons.Filled.Fullscreen,
              contentDescription = "Open full-screen 3D ANRI face",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          if (turns.isNotEmpty()) {
            IconButton(onClick = viewModel::clearTranscript) {
              Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear voice transcript")
            }
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
          .padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Language, Direct Audio & Auto-Turn Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = preferBengali,
            onClick = {
              viewModel.toggleBengaliLanguage(
                useBengali = true,
                speed = profile?.voiceSpeed ?: 1.0,
                pitch = profile?.voicePitch ?: 1.08,
              )
            },
            label = { Text("বাংলা") },
            modifier = Modifier.testTag("voice_bengali_chip"),
          )
          FilterChip(
            selected = !preferBengali,
            onClick = {
              viewModel.toggleBengaliLanguage(
                useBengali = false,
                speed = profile?.voiceSpeed ?: 1.0,
                pitch = profile?.voicePitch ?: 1.08,
              )
            },
            label = { Text("English") },
          )
          FilterChip(
            selected = directAudioMode,
            onClick = { viewModel.setDirectGeminiAudioMode(!directAudioMode) },
            leadingIcon = {
              Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
            },
            label = { Text("Gemini Mic") },
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Auto", style = MaterialTheme.typography.labelSmall)
          Spacer(modifier = Modifier.width(4.dp))
          Switch(checked = autoLoop, onCheckedChange = viewModel::setAutoLoop)
        }
      }

      // Power-Button Gemini Assist + Live Cast Strip
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 6.dp)
            .anriGlowBorder(shape = RoundedCornerShape(14.dp), width = 1.dp, elevation = 4.dp, intensity = 0.38f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              Icons.Filled.PowerSettingsNew,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Power Button -> Live Cast Overlay",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
              onClick = {
                context.startActivity(
                  Intent(context, AnriAssistOverlayActivity::class.java).apply {
                    action = AnriAssistOverlayActivity.ACTION_POWER_LIVE_CAST
                    putExtra(AnriAssistOverlayActivity.EXTRA_AUTO_CAST, true)
                  }
                )
              },
              modifier = Modifier.testTag("launch_power_cast_overlay_button"),
            ) {
              Text("Test Cast")
            }
            TextButton(
              onClick = {
                runCatching {
                  context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                }
              },
              modifier = Modifier.testTag("power_assistant_settings_button"),
            ) {
              Text("Bind Power")
            }
          }
        }
      }

      // Avatar Mode Switcher + 3D Stage / Orb
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilterChip(
          selected = show3dAvatar,
          onClick = { show3dAvatar = true },
          leadingIcon = { Icon(Icons.Filled.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text("3D Anime Core") },
        )
        FilterChip(
          selected = !show3dAvatar,
          onClick = { show3dAvatar = false },
          label = { Text("Pulse Orb") },
        )
        if (show3dAvatar) {
          HoloPersonaMode.entries.forEach { mode ->
            FilterChip(
              selected = personaMode == mode,
              onClick = { personaMode = mode },
              label = { Text(mode.label) },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      val orbColor =
        when (phase) {
          LiveVoicePhase.LISTENING -> MaterialTheme.colorScheme.primary
          LiveVoicePhase.THINKING -> MaterialTheme.colorScheme.secondary
          LiveVoicePhase.SPEAKING -> MaterialTheme.colorScheme.tertiary
          LiveVoicePhase.IDLE -> MaterialTheme.colorScheme.primary
        }

      val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
      val isScreenResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

      if (show3dAvatar) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.background,
          modifier =
            Modifier.fillMaxWidth()
              .height(190.dp)
              .anriGlowBorder(shape = RoundedCornerShape(20.dp), width = 1.5.dp, elevation = 12.dp, intensity = 0.7f),
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            if (isScreenResumed) {
              HoloFaceCanvas(
                personaMode = personaMode,
                phase = phase,
                wireframeEnabled = true,
                resetCameraTick = 0,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
      } else {
        Box(
          modifier = Modifier.size(144.dp),
          contentAlignment = Alignment.Center,
        ) {
          val secondaryGlow = MaterialTheme.colorScheme.secondary
          val reactiveScale =
            if (phase == LiveVoicePhase.LISTENING) 1.0f + micAmplitude * 0.35f
            else if (phase == LiveVoicePhase.IDLE) 1.04f
            else pulseScale
          Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension * 0.34f
            drawCircle(
              brush =
                Brush.radialGradient(
                  listOf(orbColor.copy(alpha = 0.38f), secondaryGlow.copy(alpha = 0.12f), orbColor.copy(alpha = 0f))
                ),
              radius = baseRadius * reactiveScale * 1.45f,
            )
            drawCircle(
              color = orbColor.copy(alpha = if (phase == LiveVoicePhase.IDLE) 0.45f else 0.88f),
              radius = baseRadius * reactiveScale,
              style = Stroke(width = 4.5f),
            )
          }
          Box(
            modifier =
              Modifier.size(88.dp)
                .anriGlowBorder(shape = CircleShape, width = 2.dp, elevation = 14.dp, intensity = 0.85f)
                .clip(CircleShape)
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_anri_avatar),
              contentDescription = "ANRI AI Voice Orb",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text =
          when (phase) {
            LiveVoicePhase.IDLE ->
              if (preferBengali) "কথা বলতে মাইক ট্যাপ করুন বা নিচে লিখুন" else "Tap mic to talk or type below"
            LiveVoicePhase.LISTENING ->
              if (directAudioMode) {
                if (preferBengali) "🎙️ শুনছি... শেষ হলে ট্যাপ করুন" else "🎙️ Listening... Tap button when done"
              } else {
                if (preferBengali) "🎙️ শুনছি..." else "🎙️ Listening..."
              }
            LiveVoicePhase.THINKING ->
              if (preferBengali) "⚡ ANRI উত্তর তৈরি করছে..." else "⚡ ANRI is responding..."
            LiveVoicePhase.SPEAKING ->
              if (preferBengali) "🔊 ANRI বলছে... (থামাতে ট্যাপ করুন)" else "🔊 ANRI speaking... (Tap mic to interrupt)"
          },
        style = MaterialTheme.typography.titleSmall,
        color = orbColor,
      )

      if (phase == LiveVoicePhase.LISTENING && partialTranscript.isNotBlank()) {
        Text(
          text = "\"$partialTranscript\"",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errorMessage ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      val voiceStarters =
        if (isCasting) {
          if (preferBengali) {
            listOf("এই স্ক্রিনে কী দেখা যাচ্ছে?", "এটা বাংলায় সংক্ষেপে বুঝিয়ে বলো")
          } else {
            listOf("What is on my screen right now?", "Summarize what is visible on screen")
          }
        } else if (preferBengali) {
          listOf("নমস্কার আনরি, কেমন আছো?", "আজকে কাজের একটা সুন্দর পরিকল্পনা দাও")
        } else {
          listOf("Hi ANRI, give me a quick morning briefing", "Tell me an inspiring tech fact")
        }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        voiceStarters.forEach { sample ->
          AssistChip(
            onClick = { viewModel.submitSpokenText(sample) },
            label = { Text(sample, style = MaterialTheme.typography.labelSmall) },
          )
        }
      }

      // Transcript + Live Streaming bubble
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(turns, key = { it.id }) { turn ->
          val isUser = turn.role == "user"
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isUser) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text =
                  when {
                    isUser && turn.usedScreenCast -> "You · 📺 Screen Shared"
                    isUser -> "You"
                    turn.usedScreenCast -> "ANRI AI · 👁️ Screen Vision"
                    else -> "ANRI AI"
                  },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = turn.text, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        if (streamingReply.isNotBlank()) {
          item(key = "voice_streaming_reply") {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier =
                Modifier.fillMaxWidth()
                  .anriGlowBorder(shape = RoundedCornerShape(14.dp), width = 1.2.dp, elevation = 8.dp, intensity = 0.7f),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "ANRI AI ⚡ Streaming...",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = streamingReply, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }

      // Text-to-Voice Fallback Bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = textInput,
          onValueChange = { textInput = it },
          placeholder = {
            Text(
              if (preferBengali) "লিখে কথা বলুন..." else "Type to speak with ANRI..."
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).testTag("live_voice_text_input"),
        )
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier =
            Modifier.size(48.dp)
              .clickable {
                val prompt = textInput.trim()
                if (prompt.isNotEmpty()) {
                  textInput = ""
                  viewModel.submitSpokenText(prompt)
                }
              }
              .testTag("live_voice_text_send_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send text to Live Voice",
              tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
      }

      // Primary Voice Action Row (Smooth Start / Send Early / Barge-In / Stop)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val isActive = phase != LiveVoicePhase.IDLE
        Button(
          onClick = {
            when (phase) {
              LiveVoicePhase.LISTENING -> {
                if (directAudioMode) viewModel.finishListeningEarly() else viewModel.stopSession()
              }
              LiveVoicePhase.THINKING,
              LiveVoicePhase.SPEAKING -> {
                // Smooth Barge-In: immediately stop TTS and listen to user
                val granted =
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                  ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.startListeningTurn()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
              LiveVoicePhase.IDLE -> {
                val granted =
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                  ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                  viewModel.startListeningTurn()
                } else {
                  permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            }
          },
          modifier = Modifier.weight(1f).height(50.dp).testTag("live_voice_action_button"),
          shape = RoundedCornerShape(16.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                when (phase) {
                  LiveVoicePhase.LISTENING -> MaterialTheme.colorScheme.secondary
                  LiveVoicePhase.SPEAKING -> MaterialTheme.colorScheme.tertiary
                  else -> MaterialTheme.colorScheme.primary
                },
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Icon(
            imageVector =
              when (phase) {
                LiveVoicePhase.LISTENING -> Icons.Filled.Bolt
                LiveVoicePhase.SPEAKING -> Icons.Filled.Mic
                else -> if (preferBengali) Icons.Filled.GraphicEq else Icons.Filled.Mic
              },
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            when (phase) {
              LiveVoicePhase.LISTENING ->
                if (directAudioMode) "Send Voice Now" else "Listening..."
              LiveVoicePhase.THINKING -> "Thinking... (Tap to Restart)"
              LiveVoicePhase.SPEAKING -> "Interrupt & Speak"
              LiveVoicePhase.IDLE ->
                if (preferBengali) "বাংলায় কথা শুরু করুন" else "Start Two-Way Voice"
            }
          )
        }

        if (isActive) {
          OutlinedButton(
            onClick = viewModel::stopSession,
            modifier = Modifier.height(50.dp).testTag("live_voice_stop_button"),
            shape = RoundedCornerShape(16.dp),
          ) {
            Icon(Icons.Filled.Stop, contentDescription = "Stop Voice Session", tint = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }
}
