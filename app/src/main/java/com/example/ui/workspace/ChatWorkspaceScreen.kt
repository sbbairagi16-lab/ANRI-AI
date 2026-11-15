package com.example.ui.workspace

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AttachedDocument
import com.example.data.model.AiToolkitAction
import com.example.data.model.ChatMessage
import com.example.data.model.CitationLink
import com.example.data.model.ModelTask
import com.example.data.model.SavedPrompt
import com.example.data.model.WorkspaceModelCatalog
import com.example.data.model.WritingMode
import com.example.service.ScreenCaptureHolder
import com.example.ui.viewmodel.ChatGenerationState
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.voice.BengaliVoiceController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatWorkspaceScreen(
  viewModel: WorkspaceViewModel,
  voiceController: BengaliVoiceController,
  isCompactScreen: Boolean,
  onOpenDrawer: () -> Unit,
  onOpenLiveVoice: () -> Unit,
  onOpenHoloFace: () -> Unit,
  onStartScreenShareFlow: () -> Unit,
  onStopScreenShare: () -> Unit,
  onRequestOpenLink: (CitationLink) -> Unit,
  onConversationCreated: (String) -> Unit,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
  val conversations by viewModel.conversations.collectAsStateWithLifecycle()
  val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
  val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
  val writingMode by viewModel.writingMode.collectAsStateWithLifecycle()
  val webSearchEnabled by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
  val autoSpeak by viewModel.autoSpeakReplies.collectAsStateWithLifecycle()
  val lastCompletedReply by viewModel.lastCompletedReply.collectAsStateWithLifecycle()
  val composerText by viewModel.composerText.collectAsStateWithLifecycle()
  val pendingImages by viewModel.pendingImages.collectAsStateWithLifecycle()
  val pendingDocs by viewModel.pendingDocs.collectAsStateWithLifecycle()
  val genState by viewModel.generationState.collectAsStateWithLifecycle()
  val savedPrompts by viewModel.savedPrompts.collectAsStateWithLifecycle()
  val profile by viewModel.userProfile.collectAsStateWithLifecycle()

  val isScreenSharing by ScreenCaptureHolder.isSharing.collectAsStateWithLifecycle()
  val isMicListening by voiceController.isListening.collectAsStateWithLifecycle()

  var showModelSheet by remember { mutableStateOf(false) }
  var showPromptsSheet by remember { mutableStateOf(false) }
  var showToolkitSheet by remember { mutableStateOf(false) }
  var voiceInputError by remember { mutableStateOf<String?>(null) }

  val activeModel = remember(selectedModelId) { WorkspaceModelCatalog.findById(selectedModelId) }
  val activeConv = remember(conversations, activeConvId) {
    conversations.firstOrNull { it.id == activeConvId }
  }
  val isBusy =
    genState is ChatGenerationState.Connecting || genState is ChatGenerationState.Streaming

  LaunchedEffect(lastCompletedReply, autoSpeak) {
    val reply = lastCompletedReply
    if (autoSpeak && !reply.isNullOrBlank()) {
      voiceController.configureVoice(
        useBengali = profile?.bengaliVoiceEnabled ?: true,
        speed = (profile?.voiceSpeed ?: 1.0).toFloat(),
        pitch = (profile?.voicePitch ?: 1.08).toFloat(),
      )
      voiceController.speak(reply)
      viewModel.clearLastCompletedReply()
    }
  }

  val listState = rememberLazyListState()
  LaunchedEffect(messages.size, genState) {
    if (messages.isNotEmpty() || isBusy) {
      val targetIndex = (messages.size - 1).coerceAtLeast(0)
      listState.animateScrollToItem(targetIndex)
    }
  }

  val glowTransition = rememberInfiniteTransition(label = "anriGlow")
  val glowAlpha by
    glowTransition.animateFloat(
      initialValue = 0.42f,
      targetValue = 0.92f,
      animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
      label = "glowAlpha",
    )

  // Zero-permission Android Photo Picker
  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        val bmp = decodeBitmapFromUri(context, uri)
        if (bmp != null) {
          val label = resolveDisplayName(context.contentResolver, uri) ?: "Image"
          viewModel.addPendingImage(label, bmp)
        }
      }
    }

  // Zero-permission Storage Access Framework Document Picker
  val documentPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        val doc = readAttachedDocument(context, uri)
        if (doc != null) {
          viewModel.addPendingDocument(doc)
        }
      }
    }

  // Runtime RECORD_AUDIO permission for chat voice input
  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val langTag = if (profile?.bengaliVoiceEnabled != false) "bn-BD" else "en-US"
        voiceController.startListening(
          languageTag = langTag,
          onFinalResult = { text -> viewModel.appendTranscriptToComposer(text) },
          onError = { err -> voiceInputError = err },
        )
      } else {
        voiceInputError = "Microphone permission denied. Voice input requires microphone access."
      }
    }

  Column(
    modifier = Modifier.fillMaxSize().anriAuroraBackground().imePadding()
  ) {
    TopAppBar(
      colors =
        TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
          titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
      navigationIcon = {
        if (isCompactScreen) {
          IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.testTag("open_conversations_drawer_button"),
          ) {
            Icon(Icons.Filled.Menu, contentDescription = "Open conversations list")
          }
        }
      },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier.size(38.dp)
                .anriGlowBorder(
                  shape = CircleShape,
                  width = if (isBusy) 2.dp else 1.2.dp,
                  elevation = if (isBusy) 12.dp else 6.dp,
                  intensity = if (isBusy) glowAlpha else 0.7f,
                )
                .clip(CircleShape)
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_anri_avatar),
              contentDescription = "ANRI AI logo",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = activeConv?.title ?: "ANRI AI Studio",
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                when {
                  isBusy -> "⚡ Streaming · ${activeModel.displayName}"
                  writingMode == WritingMode.FAST -> "⚡ Turbo Fast · Ready"
                  activeConvId != null -> "anri://chat/${activeConvId!!.take(8)}"
                  else -> "Workspace Ready"
                },
              style = MaterialTheme.typography.labelSmall,
              color =
                if (writingMode == WritingMode.FAST || isBusy) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
            )
          }
        }
      },
      actions = {
        // Launch 3D Anime Naruto x JARVIS Face
        IconButton(
          onClick = onOpenHoloFace,
          modifier = Modifier.testTag("open_holo_face_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.ViewInAr,
            contentDescription = "Launch 3D ANRI Face",
            tint = MaterialTheme.colorScheme.primary,
          )
        }

        // AI Toolkit Sheet trigger
        IconButton(
          onClick = { showToolkitSheet = true },
          modifier = Modifier.testTag("ai_toolkit_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "Open ANRI AI Toolkit",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }

        // Model Selector Pill with luminous border
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier =
            Modifier.padding(end = 4.dp)
              .anriGlowBorder(shape = RoundedCornerShape(999.dp), width = 1.dp, elevation = 4.dp, intensity = 0.55f)
              .clickable { showModelSheet = true }
              .testTag("model_selector_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Tune,
              contentDescription = "Select AI Model",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = activeModel.displayName.removePrefix("Gemini "),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
            )
          }
        }

        if (messages.isNotEmpty()) {
          IconButton(
            onClick = {
              val transcript = viewModel.buildShareTranscript()
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, transcript)
                }
              context.startActivity(Intent.createChooser(shareIntent, "Share ANRI AI Chat"))
            },
            modifier = Modifier.testTag("share_conversation_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = "Share conversation",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Live Bengali Voice Mode shortcut
        IconButton(
          onClick = onOpenLiveVoice,
          modifier = Modifier.testTag("live_voice_shortcut_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = "Open Bengali Live Voice",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      },
    )

    // Active screen share banner
    if (isScreenSharing) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              Icons.Filled.ScreenShare,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Screen sharing active. Tap Capture to attach your screen.",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = {
                val frame = ScreenCaptureHolder.captureFrame()
                if (frame != null) {
                  viewModel.addPendingImage("Shared Screen", frame)
                }
              },
              modifier = Modifier.testTag("capture_screen_frame_button"),
            ) {
              Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Capture")
            }
            TextButton(onClick = onStopScreenShare) {
              Text("Stop", color = MaterialTheme.colorScheme.error)
            }
          }
        }
      }
    }

    // Writing Modes & Web Search Bar
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      WritingMode.entries.forEach { mode ->
        val isFast = mode == WritingMode.FAST
        FilterChip(
          selected = writingMode == mode,
          onClick = { viewModel.selectWritingMode(mode) },
          leadingIcon =
            if (isFast) {
              {
                Icon(
                  Icons.Filled.Bolt,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            } else null,
          label = { Text(if (isFast) "Turbo Fast" else mode.label) },
          colors =
            if (isFast) {
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
              )
            } else {
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            },
          modifier = Modifier.testTag("writing_mode_${mode.id}"),
        )
      }

      FilterChip(
        selected = webSearchEnabled || writingMode == WritingMode.RESEARCH,
        onClick = { viewModel.toggleWebSearch(!webSearchEnabled) },
        label = { Text("Cited Search") },
        leadingIcon = {
          Icon(
            Icons.Filled.Public,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
          ),
        modifier = Modifier.testTag("cited_search_chip"),
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Messages Feed or Empty Workspace Welcome
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (messages.isEmpty() && genState is ChatGenerationState.Idle) {
        EmptyChatHero(
          savedPrompts = savedPrompts,
          onSelectStarter = { starter ->
            viewModel.setComposerText(starter)
          },
          onRunStarter = { starter ->
            viewModel.sendMessage(starter, onConversationCreated = onConversationCreated)
          },
          onOpenHoloFace = onOpenHoloFace,
          onOpenToolkit = { showToolkitSheet = true },
          onOpenSavedPrompts = { showPromptsSheet = true },
        )
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          items(messages, key = { it.id }) { msg ->
            ChatMessageBubble(
              message = msg,
              onCopy = { clipboardManager.setText(AnnotatedString(msg.content)) },
              onSpeak = {
                voiceController.configureVoice(
                  useBengali = profile?.bengaliVoiceEnabled ?: true,
                  speed = (profile?.voiceSpeed ?: 1.0).toFloat(),
                  pitch = (profile?.voicePitch ?: 1.08).toFloat(),
                )
                voiceController.speak(msg.content)
              },
              onSaveAsPrompt = {
                viewModel.savePrompt(
                  title = msg.content.lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "Saved Reply" },
                  body = msg.content.take(2000),
                  category = "Creative",
                )
              },
              onFollowUp = { followUpInstruction ->
                viewModel.sendMessage(
                  overridePrompt = "$followUpInstruction\n\nReference:\n${msg.content.take(1500)}",
                  onConversationCreated = onConversationCreated,
                )
              },
              onLinkTap = onRequestOpenLink,
            )
          }

          when (val state = genState) {
            is ChatGenerationState.Connecting -> {
              item(key = "streaming_connecting") {
                StreamingAssistantCard(
                  text = "Connecting ${activeModel.displayName}...",
                  citations = emptyList(),
                  isConnecting = true,
                  glowIntensity = glowAlpha,
                  onLinkTap = onRequestOpenLink,
                )
              }
            }
            is ChatGenerationState.Streaming -> {
              item(key = "streaming_active") {
                StreamingAssistantCard(
                  text = state.partialText,
                  citations = state.citations,
                  isConnecting = false,
                  glowIntensity = glowAlpha,
                  onLinkTap = onRequestOpenLink,
                )
              }
            }
            is ChatGenerationState.Error -> {
              item(key = "error_card") {
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color = MaterialTheme.colorScheme.errorContainer,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                  modifier = Modifier.fillMaxWidth().testTag("chat_error_banner"),
                ) {
                  Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = "AI Response Interrupted",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                      text = state.message,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      if (!state.retryPrompt.isNullOrBlank()) {
                        Button(
                          onClick = { viewModel.retryLastMessage(onConversationCreated) },
                          modifier = Modifier.testTag("retry_chat_error_button"),
                        ) {
                          Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Retry Answer")
                        }
                      }
                      if (writingMode != WritingMode.FAST) {
                        OutlinedButton(
                          onClick = {
                            viewModel.selectWritingMode(WritingMode.FAST)
                            viewModel.retryLastMessage(onConversationCreated)
                          }
                        ) {
                          Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("Try Turbo Fast")
                        }
                      }
                    }
                  }
                }
              }
            }
            ChatGenerationState.Idle -> Unit
          }
        }
      }
    }

    // Voice error notice
    if (voiceInputError != null) {
      Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = voiceInputError ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { voiceInputError = null }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
          }
        }
      }
    }

    // Pending Attachments Bar
    if (pendingImages.isNotEmpty() || pendingDocs.isNotEmpty()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pendingImages.forEach { img ->
          Box(
            modifier =
              Modifier.size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
          ) {
            Image(
              bitmap = img.bitmap.asImageBitmap(),
              contentDescription = img.label,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
            IconButton(
              onClick = { viewModel.removePendingImage(img.id) },
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .size(24.dp)
                  .background(Color.Black.copy(alpha = 0.65f), CircleShape),
            ) {
              Icon(
                Icons.Filled.Close,
                contentDescription = "Remove ${img.label}",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }

        pendingDocs.forEach { item ->
          AssistChip(
            onClick = { viewModel.removePendingDocument(item.id) },
            label = { Text(item.doc.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
              Icon(Icons.Filled.Close, contentDescription = "Remove file", modifier = Modifier.size(16.dp))
            },
          )
        }
      }
    }

    // Luminous Composer Bar
    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
      tonalElevation = 6.dp,
      modifier =
        Modifier.fillMaxWidth()
          .anriGlowBorder(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            width = 1.dp,
            elevation = 8.dp,
            intensity = if (isBusy || composerText.isNotBlank()) 0.72f else 0.30f,
          ),
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = composerText,
            onValueChange = viewModel::setComposerText,
            placeholder = {
              Text(
                when (writingMode) {
                  WritingMode.BENGALI -> "বাংলায় বা ইংরেজিতে লিখুন..."
                  WritingMode.FAST -> "⚡ Ask anything for a fast answer..."
                  else -> "Ask ANRI AI anything..."
                }
              )
            },
            maxLines = 5,
            shape = RoundedCornerShape(18.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              ),
            modifier = Modifier.weight(1f).testTag("chat_composer_input"),
          )

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(50.dp)
                .clip(CircleShape)
                .background(
                  brush =
                    if (isBusy) {
                      Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error)
                      )
                    } else {
                      anriAccentGradientBrush()
                    }
                )
                .clickable {
                  if (isBusy) {
                    viewModel.cancelGeneration()
                  } else {
                    viewModel.sendMessage(onConversationCreated = onConversationCreated)
                  }
                }
                .testTag("send_message_button"),
          ) {
            Icon(
              imageVector =
                if (isBusy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
              contentDescription = if (isBusy) "Stop generation" else "Send message",
              tint =
                if (isBusy) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onPrimary,
            )
          }
        }

        // Composer Action Tools
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              modifier = Modifier.testTag("attach_image_button"),
            ) {
              Icon(
                Icons.Filled.Image,
                contentDescription = "Attach photo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(
              onClick = {
                documentPickerLauncher.launch(
                  arrayOf("text/*", "application/pdf", "application/json")
                )
              },
              modifier = Modifier.testTag("attach_file_button"),
            ) {
              Icon(
                Icons.Filled.AttachFile,
                contentDescription = "Attach document",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(
              onClick = { showPromptsSheet = true },
              modifier = Modifier.testTag("insert_saved_prompt_button"),
            ) {
              Icon(
                Icons.Filled.Bookmark,
                contentDescription = "Insert saved prompt",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(
              onClick = {
                if (isScreenSharing) onStopScreenShare() else onStartScreenShareFlow()
              },
              modifier = Modifier.testTag("screen_share_toggle_button"),
            ) {
              Icon(
                imageVector =
                  if (isScreenSharing) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare,
                contentDescription =
                  if (isScreenSharing) "Stop screen sharing" else "Start screen sharing",
                tint =
                  if (isScreenSharing) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(
              onClick = { viewModel.toggleAutoSpeakReplies(!autoSpeak) },
              modifier = Modifier.testTag("auto_speak_toggle"),
            ) {
              Icon(
                imageVector = if (autoSpeak) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription =
                  if (autoSpeak) "Disable auto-speak replies" else "Auto-speak AI replies",
                tint =
                  if (autoSpeak) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(
              onClick = {
                if (isMicListening) {
                  voiceController.stopListening()
                } else {
                  val hasAudio =
                    ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                  if (hasAudio) {
                    val langTag = if (profile?.bengaliVoiceEnabled != false) "bn-BD" else "en-US"
                    voiceController.startListening(
                      languageTag = langTag,
                      onFinalResult = { text -> viewModel.appendTranscriptToComposer(text) },
                      onError = { err -> voiceInputError = err },
                    )
                  } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  }
                }
              },
              modifier = Modifier.testTag("voice_input_mic_button"),
            ) {
              Icon(
                imageVector = if (isMicListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isMicListening) "Stop listening" else "Voice input",
                tint =
                  if (isMicListening) MaterialTheme.colorScheme.error
                  else MaterialTheme.colorScheme.primary,
              )
            }
          }

          Text(
            text = if (isMicListening) "Listening..." else activeModel.displayName,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isMicListening) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }

  if (showModelSheet) {
    ModelSelectorSheet(
      selectedModelId = selectedModelId,
      taskFilter = ModelTask.CHAT,
      onSelectModel = { spec -> viewModel.selectModel(spec.id) },
      onDismiss = { showModelSheet = false },
    )
  }

  if (showPromptsSheet) {
    SavedPromptPickerSheet(
      prompts = savedPrompts,
      onPickPrompt = { prompt ->
        viewModel.setComposerText(prompt.body)
        showPromptsSheet = false
      },
      onDismiss = { showPromptsSheet = false },
    )
  }

  if (showToolkitSheet) {
    QuickToolsSheet(
      onRunToolPrompt = { _, fullPrompt ->
        viewModel.sendMessage(fullPrompt, onConversationCreated = onConversationCreated)
      },
      onDismiss = { showToolkitSheet = false },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChatHero(
  savedPrompts: List<SavedPrompt>,
  onSelectStarter: (String) -> Unit,
  onRunStarter: (String) -> Unit,
  onOpenHoloFace: () -> Unit,
  onOpenToolkit: () -> Unit,
  onOpenSavedPrompts: () -> Unit,
) {
  val starters =
    listOf(
      "Give me 5 quick ways to stay productive today." to "⚡ Instant Answer",
      "একটি সুন্দর বাংলা কবিতা লিখে দাও বর্ষাকাল নিয়ে।" to "বাংলা সৃজনশীল",
      "Write a clean Kotlin coroutine retry helper with exponential backoff." to "Code Lab",
      "Summarize the top breakthroughs in mobile AI with sources." to "Cited Search",
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.background,
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(16f / 9f)
              .anriGlowBorder(
                shape = RoundedCornerShape(22.dp),
                width = 1.5.dp,
                elevation = 14.dp,
                intensity = 0.78f,
              ),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_anri_banner),
            contentDescription = "ANRI AI logo banner",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Welcome to ANRI AI",
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Turbo-fast AI answers, 3D Anime Holo Core, Live Voice & Studio.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(
            onClick = onOpenToolkit,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("hero_toolkit_button"),
          ) {
            Icon(
              Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Toolkit")
          }
        }

        // Featured 3D Three.js Naruto x J.A.R.V.I.S. Hologram Launch Card
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
          modifier =
            Modifier.fillMaxWidth()
              .anriGlowBorder(
                shape = RoundedCornerShape(18.dp),
                width = 1.4.dp,
                elevation = 10.dp,
                intensity = 0.72f,
              )
              .clickable { onOpenHoloFace() }
              .testTag("hero_launch_3d_face_card"),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Filled.ViewInAr,
                    contentDescription = "Launch 3D ANRI Core",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "3D ANRI AI Face (Three.js)",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                  text = "Interactive Anime × J.A.R.V.I.S. hologram with Sharingan & Chakra modes",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
              }
            }
          }
        }
      }
    }

    // One-tap AI Toolkit chips
    item {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AiToolkitAction.entries.forEach { action ->
          AssistChip(
            onClick = onOpenToolkit,
            label = { Text(action.title) },
            leadingIcon = {
              Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
              )
            },
            colors =
              AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
              ),
          )
        }
      }
    }

    item {
      Text(
        text = "Quick Starters (Tap to run instantly)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        starters.forEach { (prompt, tag) ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            modifier =
              Modifier.widthIn(min = 160.dp, max = 280.dp)
                .anriGlowBorder(
                  shape = RoundedCornerShape(16.dp),
                  width = 1.dp,
                  elevation = 4.dp,
                  intensity = 0.38f,
                )
                .clickable { onRunStarter(prompt) },
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }

    if (savedPrompts.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Your Saved Prompts",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          TextButton(onClick = onOpenSavedPrompts) { Text("Browse All") }
        }
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          savedPrompts.take(5).forEach { saved ->
            AssistChip(
              onClick = { onSelectStarter(saved.body) },
              label = { Text(saved.title) },
              leadingIcon = {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChatMessageBubble(
  message: ChatMessage,
  onCopy: () -> Unit,
  onSpeak: () -> Unit,
  onSaveAsPrompt: () -> Unit,
  onFollowUp: (String) -> Unit,
  onLinkTap: (CitationLink) -> Unit,
) {
  val isUser = message.role == "user"
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(bottom = 4.dp),
    ) {
      if (!isUser) {
        Image(
          painter = painterResource(id = R.drawable.img_anri_avatar),
          contentDescription = "ANRI AI",
          modifier = Modifier.size(24.dp).clip(CircleShape),
          contentScale = ContentScale.Crop,
        )
        Text(
          text = "ANRI · ${message.modelName}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      } else {
        Text(
          text = "You",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    val bubbleShape =
      RoundedCornerShape(
        topStart = if (isUser) 18.dp else 6.dp,
        topEnd = if (isUser) 6.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp,
      )

    Surface(
      shape = bubbleShape,
      color =
        if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
      modifier =
        Modifier.widthIn(max = 560.dp)
          .let { mod ->
            if (isUser) mod
            else mod.anriGlowBorder(shape = bubbleShape, width = 1.dp, elevation = 6.dp, intensity = 0.42f)
          },
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        if (message.attachmentNames.isNotEmpty()) {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            message.attachmentNames.forEach { name ->
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
              ) {
                Text(
                  text = name,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
        }

        LinkifiedMessageText(
          text = message.content,
          textColor =
            if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
          linkColor = MaterialTheme.colorScheme.primary,
          onLinkClick = { url -> onLinkTap(CitationLink(title = url, url = url)) },
        )

        if (!isUser && message.citations.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Sources (Tap to review & open)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            message.citations.forEach { cit ->
              AssistChip(
                onClick = { onLinkTap(cit) },
                label = { Text(cit.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                  Icon(
                    Icons.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                  )
                },
                colors =
                  AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                  ),
              )
            }
          }
        }

        if (!isUser) {
          Row(
            modifier =
              Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
              Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy response",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
            IconButton(onClick = onSpeak, modifier = Modifier.size(34.dp)) {
              Icon(
                Icons.Filled.VolumeUp,
                contentDescription = "Speak response aloud",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
            }
            IconButton(onClick = onSaveAsPrompt, modifier = Modifier.size(34.dp)) {
              Icon(
                Icons.Filled.BookmarkAdd,
                contentDescription = "Save as prompt",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
              )
            }
            AssistChip(
              onClick = { onFollowUp("Make this much shorter and summarize in 3 bullets.") },
              label = { Text("Shorter", style = MaterialTheme.typography.labelSmall) },
            )
            AssistChip(
              onClick = { onFollowUp("Expand with clear practical examples.") },
              label = { Text("Examples", style = MaterialTheme.typography.labelSmall) },
            )
            AssistChip(
              onClick = { onFollowUp("Translate this clearly into natural Bengali (বাংলা).") },
              label = { Text("বাংলা", style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StreamingAssistantCard(
  text: String,
  citations: List<CitationLink>,
  isConnecting: Boolean,
  glowIntensity: Float,
  onLinkTap: (CitationLink) -> Unit,
) {
  val cardShape =
    RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
  Surface(
    shape = cardShape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier =
      Modifier.fillMaxWidth()
        .widthIn(max = 560.dp)
        .anriGlowBorder(shape = cardShape, width = 1.5.dp, elevation = 12.dp, intensity = glowIntensity),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isConnecting) "⚡ ANRI AI is connecting..." else "⚡ ANRI AI is answering...",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      if (isConnecting) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      } else {
        LinkifiedMessageText(
          text = text,
          textColor = MaterialTheme.colorScheme.onSurface,
          linkColor = MaterialTheme.colorScheme.primary,
          onLinkClick = { url -> onLinkTap(CitationLink(url, url)) },
        )
        if (citations.isNotEmpty()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "${citations.size} cited source(s) found...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
    }
  }
}

@Suppress("DEPRECATION")
@Composable
private fun LinkifiedMessageText(
  text: String,
  textColor: Color,
  linkColor: Color,
  onLinkClick: (String) -> Unit,
) {
  val urlRegex = remember { Regex("https?://[^\\s)\\]>\"']+") }
  val annotated =
    remember(text, textColor, linkColor) {
      buildAnnotatedString {
        append(text)
        urlRegex.findAll(text).forEach { match ->
          addStyle(
            style =
              SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium,
              ),
            start = match.range.first,
            end = match.range.last + 1,
          )
          addStringAnnotation(
            tag = "URL",
            annotation = match.value,
            start = match.range.first,
            end = match.range.last + 1,
          )
        }
      }
    }

  ClickableText(
    text = annotated,
    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
    onClick = { offset ->
      annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let {
        onLinkClick(it.item)
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPromptPickerSheet(
  prompts: List<SavedPrompt>,
  onPickPrompt: (SavedPrompt) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Insert Saved Prompt", style = MaterialTheme.typography.headlineSmall)
      if (prompts.isEmpty()) {
        Text(
          text = "No saved prompts yet. Create reusable templates in the Prompts tab.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 16.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(300.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(prompts, key = { it.id }) { p ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth().clickable { onPickPrompt(p) },
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(p.title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  p.body,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

internal fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
  return runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        decoder.isMutableRequired = true
      }
    } else {
      @Suppress("DEPRECATION")
      MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
  }.getOrNull()
}

private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
  resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (idx >= 0 && cursor.moveToFirst()) {
      return cursor.getString(idx)
    }
  }
  return uri.lastPathSegment
}

private fun readAttachedDocument(context: Context, uri: Uri): AttachedDocument? {
  val resolver = context.contentResolver
  val name = resolveDisplayName(resolver, uri) ?: "document"
  val mime = resolver.getType(uri) ?: "text/plain"
  return runCatching {
    resolver.openInputStream(uri)?.use { stream ->
      val bytes = stream.readBytes()
      if (mime.startsWith("text/") || mime.contains("json")) {
        AttachedDocument(
          displayName = name,
          mimeType = mime,
          textContent = bytes.toString(Charsets.UTF_8),
        )
      } else {
        AttachedDocument(
          displayName = name,
          mimeType = mime,
          bytes = bytes,
        )
      }
    }
  }.getOrNull()
}
