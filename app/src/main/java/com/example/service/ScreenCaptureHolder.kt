package com.example.service

import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

object ScreenCaptureHolder {
  private const val TAG = "ScreenCaptureHolder"

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var width: Int = 720
  private var height: Int = 1280

  private val _isSharing = MutableStateFlow(false)
  val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

  private val _lastCapturedFrame = MutableStateFlow<Bitmap?>(null)
  val lastCapturedFrame: StateFlow<Bitmap?> = _lastCapturedFrame.asStateFlow()

  fun startSession(
    projection: MediaProjection,
    screenWidth: Int,
    screenHeight: Int,
    densityDpi: Int,
  ) {
    stopSession()
    width = screenWidth.coerceIn(360, 1080)
    height = screenHeight.coerceIn(640, 1920)

    mediaProjection = projection
    projection.registerCallback(
      object : MediaProjection.Callback() {
        override fun onStop() {
          stopSession()
        }
      },
      null,
    )

    val reader =
      ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
    imageReader = reader
    virtualDisplay =
      projection.createVirtualDisplay(
        "ANRI_ScreenShare",
        width,
        height,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        null,
      )
    _isSharing.value = true
    Log.d(TAG, "User-started screen share session active (${width}x$height)")
  }

  fun captureFrame(): Bitmap? {
    val reader = imageReader ?: return _lastCapturedFrame.value
    val image = reader.acquireLatestImage() ?: return _lastCapturedFrame.value
    return try {
      val planes = image.planes
      val buffer: ByteBuffer = planes[0].buffer
      val pixelStride = planes[0].pixelStride
      val rowStride = planes[0].rowStride
      val rowPadding = rowStride - pixelStride * width
      val fullBitmap =
        Bitmap.createBitmap(
          width + rowPadding / pixelStride,
          height,
          Bitmap.Config.ARGB_8888,
        )
      fullBitmap.copyPixelsFromBuffer(buffer)
      val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, width, height)
      if (cropped != fullBitmap) fullBitmap.recycle()
      _lastCapturedFrame.value = cropped
      cropped
    } catch (e: Exception) {
      Log.e(TAG, "Failed capturing screen frame", e)
      _lastCapturedFrame.value
    } finally {
      image.close()
    }
  }

  fun stopSession() {
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
    runCatching { mediaProjection?.stop() }
    mediaProjection = null
    _isSharing.value = false
  }
}
