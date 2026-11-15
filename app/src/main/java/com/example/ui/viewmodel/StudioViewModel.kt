package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AnriAiService
import com.example.data.ai.StudioAspectRatio
import com.example.data.ai.VideoStoryboard
import com.example.data.model.ImageStylePreset
import com.example.data.model.WorkspaceModelCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImageStudioUiState {
  data object Idle : ImageStudioUiState

  data object Loading : ImageStudioUiState

  data class Success(val bitmap: Bitmap, val prompt: String, val modelName: String) :
    ImageStudioUiState

  data class Error(val message: String, val retryPrompt: String? = null) : ImageStudioUiState
}

sealed interface VideoStoryboardUiState {
  data object Idle : VideoStoryboardUiState

  data object Loading : VideoStoryboardUiState

  data class Success(val storyboard: VideoStoryboard, val keyframe: Bitmap?) :
    VideoStoryboardUiState

  data class Error(
    val message: String,
    val retryConcept: String? = null,
    val includeKeyframe: Boolean = true,
  ) : VideoStoryboardUiState
}

class StudioViewModel(
  private val aiService: AnriAiService,
) : ViewModel() {

  private val _selectedImageModelId =
    MutableStateFlow(WorkspaceModelCatalog.DEFAULT_IMAGE_MODEL_ID)
  val selectedImageModelId: StateFlow<String> = _selectedImageModelId.asStateFlow()

  private val _aspectRatio = MutableStateFlow(StudioAspectRatio.SQUARE)
  val aspectRatio: StateFlow<StudioAspectRatio> = _aspectRatio.asStateFlow()

  private val _stylePreset = MutableStateFlow(ImageStylePreset.CYBER_GLOW)
  val stylePreset: StateFlow<ImageStylePreset> = _stylePreset.asStateFlow()

  private val _referenceImage = MutableStateFlow<Bitmap?>(null)
  val referenceImage: StateFlow<Bitmap?> = _referenceImage.asStateFlow()

  private val _imageState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
  val imageState: StateFlow<ImageStudioUiState> = _imageState.asStateFlow()

  private val _videoDurationSeconds = MutableStateFlow(8)
  val videoDurationSeconds: StateFlow<Int> = _videoDurationSeconds.asStateFlow()

  private val _storyboardState =
    MutableStateFlow<VideoStoryboardUiState>(VideoStoryboardUiState.Idle)
  val storyboardState: StateFlow<VideoStoryboardUiState> = _storyboardState.asStateFlow()

  fun selectImageModel(modelId: String) {
    _selectedImageModelId.value = modelId
  }

  fun selectAspectRatio(ratio: StudioAspectRatio) {
    _aspectRatio.value = ratio
  }

  fun selectStylePreset(preset: ImageStylePreset) {
    _stylePreset.value = preset
  }

  fun setReferenceImage(bitmap: Bitmap?) {
    _referenceImage.value = bitmap
  }

  fun setVideoDuration(seconds: Int) {
    // Enforce strict 10-second maximum cap
    _videoDurationSeconds.value = seconds.coerceIn(1, 10)
  }

  fun generateImage(prompt: String) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isBlank()) return
    val styledPrompt = cleanPrompt + _stylePreset.value.styleSuffix
    val modelId = _selectedImageModelId.value
    val modelName = WorkspaceModelCatalog.findById(modelId).displayName
    val ratio = _aspectRatio.value
    val refBitmap = _referenceImage.value

    viewModelScope.launch {
      _imageState.value = ImageStudioUiState.Loading
      try {
        val resultBitmap =
          aiService.generateImage(
            modelId = modelId,
            prompt = styledPrompt,
            aspectRatio = ratio,
            referenceImage = refBitmap,
          )
        _imageState.value = ImageStudioUiState.Success(resultBitmap, cleanPrompt, modelName)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Image Studio error", e)
        _imageState.value =
          ImageStudioUiState.Error(
            message = e.localizedMessage ?: "Image generation failed.",
            retryPrompt = cleanPrompt,
          )
      }
    }
  }

  fun retryImage() {
    val retry = (_imageState.value as? ImageStudioUiState.Error)?.retryPrompt ?: return
    generateImage(retry)
  }

  fun generateStoryboard(concept: String, includeKeyframe: Boolean) {
    val cleanConcept = concept.trim()
    if (cleanConcept.isBlank()) return
    val cappedSeconds = _videoDurationSeconds.value.coerceIn(1, 10)

    viewModelScope.launch {
      _storyboardState.value = VideoStoryboardUiState.Loading
      try {
        val board = aiService.generateVideoStoryboard(cleanConcept, cappedSeconds)
        val keyframe =
          if (includeKeyframe && board.shots.isNotEmpty()) {
            runCatching {
              aiService.generateImage(
                modelId = _selectedImageModelId.value,
                prompt =
                  "${board.visualStyle}. Opening frame: ${board.shots.first().visualDescription}",
                aspectRatio = StudioAspectRatio.LANDSCAPE_16_9,
              )
            }.getOrNull()
          } else {
            null
          }
        _storyboardState.value = VideoStoryboardUiState.Success(board, keyframe)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Video storyboard error", e)
        _storyboardState.value =
          VideoStoryboardUiState.Error(
            message = e.localizedMessage ?: "Storyboard generation failed.",
            retryConcept = cleanConcept,
            includeKeyframe = includeKeyframe,
          )
      }
    }
  }

  fun retryStoryboard() {
    val err = _storyboardState.value as? VideoStoryboardUiState.Error ?: return
    val concept = err.retryConcept ?: return
    generateStoryboard(concept, err.includeKeyframe)
  }

  companion object {
    private const val TAG = "StudioVM"

    fun provideFactory(aiService: AnriAiService): ViewModelProvider.Factory = viewModelFactory {
      initializer { StudioViewModel(aiService) }
    }
  }
}
