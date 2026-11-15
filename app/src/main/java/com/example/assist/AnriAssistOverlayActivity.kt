package com.example.assist

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainActivity
import com.example.R
import com.example.data.ai.AnriAiService
import com.example.firebase.configureAppCheck
import com.example.service.ScreenCaptureHolder
import com.example.service.ScreenShareService
import com.example.ui.theme.AnriTheme
import com.example.ui.viewmodel.LiveVoicePhase
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.workspace.anriGlowBorder
import com.example.voice.BengaliVoiceController

/**
 * Gemini-style floating bottom-sheet overlay launched when the user invokes Android Digital
 * Assistant (Power button long-press / Quick Settings Tile / in-app Power-Cast trigger).
 * Automatically starts Live Screen Cast consent + Live Two-Way Voice.
 */
class AnriAssistOverlayActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()

    val autoCast =
      intent?.getBooleanExtra(EXTRA_AUTO_CAST, true) ?: true

    setContent {
      val context = LocalContext.current
      val aiService = remember { AnriAiService() }
      val voiceController = remember { BengaliVoiceController(context.applicationContext) }
      DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
      }

      val voiceViewModel: LiveVoiceViewModel =
        viewModel(factory = LiveVoiceViewModel.provideFactory(aiService, voiceController))

      AnriTheme(darkModeOverride = "dark") {
        AnriAssistOverlaySheet(
          voiceViewModel = voiceViewModel,
          autoLaunchCast = autoCast,
          onOpenFull3DWorkspace = {
            startActivity(
              Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
              }
            )
            finish()
          },
          onDismiss = { finish() },
        )
      }
    }
  }

  companion object {
    const val ACTION_POWER_LIVE_CAST = "com.example.action.POWER_LIVE_CAST"
    const val EXTRA_AUTO_CAST = "extra_auto_cast"
  }
}

@Composable
private fun AnriAssistOverlaySheet(
  voiceViewModel: LiveVoiceViewModel,
  autoLaunchCast: Boolean,
  onOpenFull3DWorkspace: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val phase by voiceViewModel.phase.collectAsStateWithLifecycle()
  val turns by voiceViewModel.turns.collectAsStateWithLifecycle()
  val streamingReply by voiceViewModel.streamingReply.collectAsStateWithLifecycle()
  val preferBengali by voiceViewModel.preferBengali.collectAsStateWithLifecycle()
  val isCasting by voiceViewModel.isScreenCasting.collectAsStateWithLifecycle()
  val lastFrame by voiceViewModel.lastScreenFrame.collectAsStateWithLifecycle()
  val errorMessage by voiceViewModel.errorMessage.collectAsStateWithLifecycle()
  val partialTranscript by voiceViewModel.voiceController.partialTranscript.collectAsStateWithLifecycle()

  var promptInput by remember { mutableStateOf("") }
  var hasTriggeredAutoCast by remember { mutableStateOf(false) }

  val screenCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        ScreenShareService.start(context, result.resultCode, result.data!!)
      }
    }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        voiceViewModel.startListeningTurn()
      }
    }

  // Auto-trigger Gemini-style Live Screen Cast consent on Power / Assist launch if not already active
  LaunchedEffect(autoLaunchCast) {
    if (autoLaunchCast && !hasTriggeredAutoCast && !ScreenCaptureHolder.isSharing.value) {
      hasTriggeredAutoCast = true
      val manager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }
  }

  val pulse = rememberInfiniteTransition(label = "assistPulse")
  val glowAlpha by
    pulse.animateFloat(
      initialValue = 0.5f,
      targetValue = 0.95f,
      animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
      label = "assistGlow",
    )

  val latestReply =
    remember(streamingReply, turns) {
      streamingReply.ifBlank { turns.lastOrNull { it.role == "assistant" }?.text.orEmpty() }
    }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.35f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onDismiss,
        )
        .imePadding(),
    contentAlignment = Alignment.BottomCenter,
  ) {
    Surface(
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier =
        Modifier.fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          )
          .anriGlowBorder(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            width = 1.6.dp,
            elevation = 16.dp,
            intensity = glowAlpha,
          )
          .navigationBarsPadding(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Header Row: ANRI Avatar + Live Screen Cast Status + Close
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Image(
              painter = painterResource(id = R.drawable.img_anri_avatar),
              contentDescription = "ANRI AI Assist",
              modifier =
                Modifier.size(42.dp)
                  .anriGlowBorder(shape = CircleShape, width = 1.5.dp, intensity = glowAlpha)
                  .clip(CircleShape),
              contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "ANRI Live · Screen Assist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  when {
                    isCasting && phase == LiveVoicePhase.LISTENING -> "Live Casting · Listening..."
                    isCasting && phase == LiveVoicePhase.THINKING -> "Reading your screen..."
                    isCasting && phase == LiveVoicePhase.SPEAKING -> "Live Casting · Speaking"
                    isCasting -> "Live Screen Cast ON · Ask anything"
                    else -> "Tap Start Cast to share your screen"
                  },
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isCasting) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.secondary,
              )
            }
          }

          Row {
            IconButton(
              onClick = onOpenFull3DWorkspace,
              modifier = Modifier.testTag("assist_open_workspace_button"),
            ) {
              Icon(
                Icons.Filled.ViewInAr,
                contentDescription = "Open full 3D ANRI Core",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("assist_close_button"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Close overlay")
            }
          }
        }

        // Live Screen Cast Preview + Toggle Strip
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
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
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  if (isCasting) MaterialTheme.colorScheme.errorContainer
                  else MaterialTheme.colorScheme.primary,
                contentColor =
                  if (isCasting) MaterialTheme.colorScheme.onErrorContainer
                  else MaterialTheme.colorScheme.onPrimary,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("assist_cast_toggle_button"),
          ) {
            Icon(
              imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isCasting) "Stop Cast" else "Start Live Cast")
          }

          lastFrame?.let { bmp ->
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "Live cast screen preview",
              modifier =
                Modifier.height(44.dp)
                  .width(72.dp)
                  .clip(RoundedCornerShape(10.dp)),
              contentScale = ContentScale.Crop,
            )
          }

          OutlinedButton(
            onClick = {
              voiceViewModel.toggleBengaliLanguage(!preferBengali, 1.0, 1.08)
            },
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(if (preferBengali) "বাংলা" else "EN")
          }
        }

        // Live streaming reply or status bubble
        if (!errorMessage.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else if (latestReply.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = latestReply,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else if (phase == LiveVoicePhase.LISTENING && partialTranscript.isNotBlank()) {
          Text(
            text = "\"$partialTranscript\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        // Quick Screen-Aware Voice Starters
        val quickScreenPrompts =
          if (preferBengali) {
            listOf("এই স্ক্রিনে কী আছে?", "এটা বাংলায় সংক্ষেপে বুঝিয়ে দাও", "এখানে কোনো সমস্যা থাকলে বলো")
          } else {
            listOf("What's on my screen?", "Summarize what's visible", "Help me with this screen")
          }
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          quickScreenPrompts.forEach { sample ->
            AssistChip(
              onClick = { voiceViewModel.submitSpokenText(sample) },
              label = { Text(sample, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }

        // Bottom Live Talk & Text Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val isVoiceBusy = phase != LiveVoicePhase.IDLE
          Surface(
            shape = CircleShape,
            color =
              if (isVoiceBusy) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primaryContainer,
            modifier =
              Modifier.size(50.dp)
                .clickable {
                  when (phase) {
                    LiveVoicePhase.LISTENING -> voiceViewModel.finishListeningEarly()
                    LiveVoicePhase.THINKING,
                    LiveVoicePhase.SPEAKING -> {
                      // Barge-in: immediately interrupt and listen
                      voiceViewModel.startListeningTurn()
                    }
                    LiveVoicePhase.IDLE -> {
                      val granted =
                        ContextCompat.checkSelfPermission(
                          context,
                          Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                      if (granted) {
                        voiceViewModel.startListeningTurn()
                      } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                      }
                    }
                  }
                }
                .testTag("assist_mic_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector =
                  when (phase) {
                    LiveVoicePhase.LISTENING -> Icons.Filled.Stop
                    LiveVoicePhase.SPEAKING -> Icons.Filled.GraphicEq
                    else -> Icons.Filled.Mic
                  },
                contentDescription = "Voice ask about screen",
                tint =
                  if (isVoiceBusy) MaterialTheme.colorScheme.onError
                  else MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          OutlinedTextField(
            value = promptInput,
            onValueChange = { promptInput = it },
            placeholder = {
              Text(
                if (preferBengali) "স্ক্রিন সম্পর্কে বলুন বা লিখুন..." else "Ask about this screen..."
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              ),
            modifier = Modifier.weight(1f).testTag("assist_text_input"),
          )

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier =
              Modifier.size(50.dp)
                .clickable {
                  val clean = promptInput.trim()
                  if (clean.isNotEmpty()) {
                    promptInput = ""
                    voiceViewModel.submitSpokenText(clean)
                  }
                }
                .testTag("assist_send_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send prompt",
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
        }
      }
    }
  }
}
