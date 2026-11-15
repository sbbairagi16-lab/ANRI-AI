package com.example.ui.workspace

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.StudioAspectRatio
import com.example.data.model.ImageStylePreset
import com.example.data.model.ModelTask
import com.example.data.model.WorkspaceModelCatalog
import com.example.ui.viewmodel.ImageStudioUiState
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.VideoStoryboardUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStudioScreen(viewModel: StudioViewModel) {
  var activeTab by remember { mutableIntStateOf(0) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Media Studio") },
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
    ) {
      TabRow(
        selectedTabIndex = activeTab,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      ) {
        Tab(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          text = { Text("Image Generation") },
          icon = { Icon(Icons.Filled.Image, contentDescription = null) },
        )
        Tab(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          text = { Text("Video (≤10s Cap)") },
          icon = { Icon(Icons.Filled.Movie, contentDescription = null) },
        )
      }

      if (activeTab == 0) {
        ImageStudioPane(viewModel)
      } else {
        VideoStoryboardPane(viewModel)
      }
    }
  }
}

@Composable
private fun ImageStudioPane(viewModel: StudioViewModel) {
  val context = LocalContext.current
  val selectedModelId by viewModel.selectedImageModelId.collectAsStateWithLifecycle()
  val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
  val stylePreset by viewModel.stylePreset.collectAsStateWithLifecycle()
  val referenceImage by viewModel.referenceImage.collectAsStateWithLifecycle()
  val imageState by viewModel.imageState.collectAsStateWithLifecycle()

  var prompt by remember { mutableStateOf("") }
  var showModelSheet by remember { mutableStateOf(false) }
  var saveStatus by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        viewModel.setReferenceImage(decodeBitmapFromUri(context, uri))
      }
    }

  val modelSpec = remember(selectedModelId) { WorkspaceModelCatalog.findById(selectedModelId) }
  val samplePrompts =
    listOf(
      "Futuristic cybernetic AI guardian in glowing amber armor",
      "Rainy Dhaka street at dusk with golden reflections",
      "Minimalist obsidian & gold emblem for ANRI studio",
    )

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier =
        Modifier.fillMaxWidth()
          .anriGlowBorder(shape = RoundedCornerShape(14.dp), width = 1.dp, elevation = 6.dp, intensity = 0.5f)
          .clickable { showModelSheet = true },
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Active Image Model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
          Text(modelSpec.displayName, style = MaterialTheme.typography.titleMedium)
          Text(modelSpec.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.Tune, contentDescription = "Switch image model", tint = MaterialTheme.colorScheme.primary)
      }
    }

    Text("Visual Style Preset", style = MaterialTheme.typography.titleSmall)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ImageStylePreset.entries.forEach { preset ->
        FilterChip(
          selected = stylePreset == preset,
          onClick = { viewModel.selectStylePreset(preset) },
          label = { Text(preset.label) },
        )
      }
    }

    Text("Aspect Ratio", style = MaterialTheme.typography.titleSmall)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      StudioAspectRatio.entries.forEach { ratio ->
        FilterChip(
          selected = aspectRatio == ratio,
          onClick = { viewModel.selectAspectRatio(ratio) },
          label = { Text(ratio.label) },
        )
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        }
      ) {
        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (referenceImage == null) "Optional Reference Photo" else "Change Reference")
      }
      if (referenceImage != null) {
        IconButton(onClick = { viewModel.setReferenceImage(null) }) {
          Icon(Icons.Filled.Close, contentDescription = "Remove reference photo")
        }
      }
    }

    referenceImage?.let { refBmp ->
      Image(
        bitmap = refBmp.asImageBitmap(),
        contentDescription = "Reference image for editing",
        modifier = Modifier.height(120.dp).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit,
      )
    }

    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      samplePrompts.forEach { idea ->
        AssistChip(
          onClick = { prompt = idea },
          label = { Text(idea, maxLines = 1) },
        )
      }
    }

    OutlinedTextField(
      value = prompt,
      onValueChange = { prompt = it },
      label = { Text(if (referenceImage != null) "Describe how to edit this photo" else "Describe the image to create") },
      minLines = 3,
      maxLines = 5,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().testTag("image_studio_prompt_input"),
    )

    Button(
      onClick = {
        saveStatus = null
        viewModel.generateImage(prompt)
      },
      enabled = prompt.isNotBlank() && imageState !is ImageStudioUiState.Loading,
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_image_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      Icon(Icons.Filled.AutoAwesome, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(if (referenceImage != null) "Edit Image" else "Generate Image")
    }

    when (val state = imageState) {
      ImageStudioUiState.Idle -> {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth().height(190.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              "Generated artwork will appear here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      ImageStudioUiState.Loading -> {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.fillMaxWidth()
              .height(220.dp)
              .anriGlowBorder(shape = RoundedCornerShape(16.dp), width = 1.5.dp, elevation = 12.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Synthesizing with ${modelSpec.displayName}...")
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
          }
        }
      }
      is ImageStudioUiState.Success -> {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.fillMaxWidth()
              .anriGlowBorder(shape = RoundedCornerShape(16.dp), width = 1.5.dp, elevation = 12.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Image(
              bitmap = state.bitmap.asImageBitmap(),
              contentDescription = state.prompt,
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
              contentScale = ContentScale.FillWidth,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Created with ${state.modelName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              OutlinedButton(
                onClick = {
                  val saved = saveBitmapToGallery(context, state.bitmap)
                  saveStatus = if (saved) "Saved image to Pictures/ANRI_AI" else "Could not save image."
                }
              ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save")
              }
            }
            if (saveStatus != null) {
              Text(
                text = saveStatus ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
      is ImageStudioUiState.Error -> {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = state.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!state.retryPrompt.isNullOrBlank()) {
              Button(onClick = viewModel::retryImage) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry Image")
              }
            }
          }
        }
      }
    }
  }

  if (showModelSheet) {
    ModelSelectorSheet(
      selectedModelId = selectedModelId,
      taskFilter = ModelTask.IMAGE,
      onSelectModel = { viewModel.selectImageModel(it.id) },
      onDismiss = { showModelSheet = false },
    )
  }
}

@Composable
private fun VideoStoryboardPane(viewModel: StudioViewModel) {
  val duration by viewModel.videoDurationSeconds.collectAsStateWithLifecycle()
  val storyboardState by viewModel.storyboardState.collectAsStateWithLifecycle()
  var concept by remember { mutableStateOf("") }
  var includeKeyframe by remember { mutableStateOf(true) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
          Icons.Filled.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text =
            "Workspace Catalog Notice: Direct Veo video rendering is not available in this Firebase AI Logic SDK. ANRI AI enforces the 10-second duration cap and generates a timed shot-by-shot storyboard + opening keyframe image.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }

    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Duration (Max 10s Cap)", style = MaterialTheme.typography.titleSmall)
        Text(
          text = "${duration}s / 10s max",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("video_duration_label"),
        )
      }
      Slider(
        value = duration.toFloat(),
        onValueChange = { viewModel.setVideoDuration(it.roundToInt().coerceIn(1, 10)) },
        valueRange = 1f..10f,
        steps = 8,
        modifier = Modifier.testTag("video_duration_slider"),
      )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.clickable { includeKeyframe = !includeKeyframe },
    ) {
      Checkbox(checked = includeKeyframe, onCheckedChange = { includeKeyframe = it })
      Text("Also render opening keyframe with Gemini Nano Banana 2.1", style = MaterialTheme.typography.bodyMedium)
    }

    OutlinedTextField(
      value = concept,
      onValueChange = { concept = it },
      label = { Text("Video concept (≤10 seconds)") },
      minLines = 3,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().testTag("video_concept_input"),
    )

    Button(
      onClick = { viewModel.generateStoryboard(concept, includeKeyframe) },
      enabled = concept.isNotBlank() && storyboardState !is VideoStoryboardUiState.Loading,
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_storyboard_button"),
      shape = RoundedCornerShape(14.dp),
    ) {
      Icon(Icons.Filled.Movie, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Generate ${duration}s Storyboard")
    }

    when (val state = storyboardState) {
      VideoStoryboardUiState.Idle -> Unit
      VideoStoryboardUiState.Loading -> {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(8.dp))
          Text("Planning ${duration}s shots and keyframe...")
        }
      }
      is VideoStoryboardUiState.Success -> {
        val board = state.storyboard
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier =
            Modifier.fillMaxWidth()
              .anriGlowBorder(shape = RoundedCornerShape(16.dp), width = 1.2.dp, elevation = 8.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${board.title} (${board.durationSeconds}s)", style = MaterialTheme.typography.titleLarge)
            Text("Style: ${board.visualStyle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            state.keyframe?.let { bmp ->
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Opening video keyframe",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth,
              )
            }
            board.shots.forEach { shot ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "${shot.timeRange} · ${shot.cameraMovement}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(shot.visualDescription, style = MaterialTheme.typography.bodyMedium)
                  if (shot.bengaliNarrationOrAudio.isNotBlank()) {
                    Text(
                      text = "Audio: ${shot.bengaliNarrationOrAudio}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                }
              }
            }
          }
        }
      }
      is VideoStoryboardUiState.Error -> {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = state.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!state.retryConcept.isNullOrBlank()) {
              Button(onClick = viewModel::retryStoryboard) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry Storyboard")
              }
            }
          }
        }
      }
    }
  }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
  return runCatching {
    val filename = "ANRI_AI_${System.currentTimeMillis()}.png"
    val values =
      ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ANRI_AI")
        }
      }
    val uri =
      context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    context.contentResolver.openOutputStream(uri)?.use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    } ?: return false
    true
  }.getOrDefault(false)
}
