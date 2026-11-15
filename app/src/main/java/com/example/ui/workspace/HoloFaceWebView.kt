package com.example.ui.workspace

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.viewmodel.LiveVoicePhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

enum class HoloPersonaMode(
  val id: String,
  val label: String,
) {
  JARVIS("jarvis", "J.A.R.V.I.S."),
  CHAKRA("chakra", "Chakra Gold"),
  SHARINGAN("sharingan", "Sharingan"),
  SAGE("sage", "Sage Mode"),
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HoloFaceCanvas(
  personaMode: HoloPersonaMode,
  phase: LiveVoicePhase,
  wireframeEnabled: Boolean,
  resetCameraTick: Int,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val bgArgb = MaterialTheme.colorScheme.background.toArgb()

  var sceneReady by remember { mutableStateOf(false) }
  var rendererCrashed by remember { mutableStateOf(false) }
  var reloadNonce by remember { mutableIntStateOf(0) }
  var renderEngineLabel by remember { mutableStateOf("Initializing 3D Core...") }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  val htmlContent = remember(context) {
    runCatching {
      context.assets.open("anri_holo_face.html").bufferedReader().use { it.readText() }
    }.getOrElse {
      "<html><body style='background:#030710;color:#2ee6d6;font-family:sans-serif;padding:24px;'>ANRI 3D Core asset missing.</body></html>"
    }
  }

  // Watchdog: if Chromium WebView stalls or renderer fails silently, switch to Native Holo Core
  LaunchedEffect(reloadNonce, rendererCrashed) {
    if (!rendererCrashed && !sceneReady) {
      delay(9000)
      if (!sceneReady && !rendererCrashed) {
        Log.w("HoloFaceWebView", "WebGL initialization timed out; switching to Native Holo Core")
        rendererCrashed = true
      }
    }
  }

  // Push live state updates into Three.js without recreating the WebGL canvas
  LaunchedEffect(sceneReady, rendererCrashed, personaMode, phase, wireframeEnabled) {
    if (sceneReady && !rendererCrashed) {
      val audioEnergy =
        when (phase) {
          LiveVoicePhase.SPEAKING -> 0.85
          LiveVoicePhase.THINKING -> 0.45
          LiveVoicePhase.LISTENING -> 0.25
          LiveVoicePhase.IDLE -> 0.0
        }
      val js =
        "window.setAnriState && window.setAnriState({mode:'${personaMode.id}',phase:'${phase.name}',wireframe:$wireframeEnabled,audioEnergy:$audioEnergy});"
      webViewRef?.evaluateJavascript(js, null)
    }
  }

  LaunchedEffect(resetCameraTick) {
    if (resetCameraTick > 0 && sceneReady && !rendererCrashed) {
      webViewRef?.evaluateJavascript("window.resetAnriCamera && window.resetAnriCamera();", null)
    }
  }

  DisposableEffect(lifecycleOwner, webViewRef) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
        Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Box(modifier = modifier.testTag("holo_face_canvas_container")) {
    if (rendererCrashed) {
      NativeHoloCoreCanvas(
        personaMode = personaMode,
        phase = phase,
        wireframeEnabled = wireframeEnabled,
        onRetryWebGl = {
          sceneReady = false
          rendererCrashed = false
          renderEngineLabel = "Reconnecting 3D Core..."
          reloadNonce += 1
        },
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      key(reloadNonce) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            WebView(ctx).apply {
              setBackgroundColor(if (bgArgb != 0) bgArgb else AndroidColor.BLACK)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
              }
              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
              }
              webChromeClient = WebChromeClient()
              webViewClient =
                object : WebViewClient() {
                  override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?,
                  ): Boolean {
                    val didCrash =
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detail?.didCrash() == true
                      } else {
                        true
                      }
                    Log.w(
                      "HoloFaceWebView",
                      "Chromium renderer gone (didCrash=$didCrash); recovering gracefully to Native Holo Core",
                    )
                    if (view != null) {
                      (view.parent as? ViewGroup)?.removeView(view)
                      runCatching {
                        view.stopLoading()
                        view.removeJavascriptInterface("AnriBridge")
                        view.destroy()
                      }
                    }
                    if (webViewRef === view) {
                      webViewRef = null
                    }
                    rendererCrashed = true
                    sceneReady = false
                    return true
                  }
                }
              addJavascriptInterface(
                object {
                  @JavascriptInterface
                  fun onSceneReady(engine: String?) {
                    post {
                      sceneReady = true
                      renderEngineLabel =
                        if (engine == "fallback2d") "2D Holo Fallback" else "Three.js WebGL"
                    }
                  }
                },
                "AnriBridge",
              )
              loadDataWithBaseURL("https://anri.ai/", htmlContent, "text/html", "UTF-8", null)
              webViewRef = this
            }
          },
          onRelease = { view ->
            runCatching {
              view.evaluateJavascript("window.disposeAnriScene && window.disposeAnriScene();", null)
              view.onPause()
              view.stopLoading()
              view.loadUrl("about:blank")
              view.removeJavascriptInterface("AnriBridge")
              (view.parent as? ViewGroup)?.removeView(view)
              view.destroy()
            }
            if (webViewRef === view) webViewRef = null
          },
        )
      }

      if (!sceneReady) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
          modifier = Modifier.align(Alignment.Center).padding(16.dp),
        ) {
          Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text(
                text = renderEngineLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NativeHoloCoreCanvas(
  personaMode: HoloPersonaMode,
  phase: LiveVoicePhase,
  wireframeEnabled: Boolean,
  onRetryWebGl: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryAccent: Color
  val secondaryAccent: Color
  when (personaMode) {
    HoloPersonaMode.JARVIS -> {
      primaryAccent = MaterialTheme.colorScheme.primary
      secondaryAccent = MaterialTheme.colorScheme.secondary
    }
    HoloPersonaMode.CHAKRA -> {
      primaryAccent = MaterialTheme.colorScheme.secondary
      secondaryAccent = MaterialTheme.colorScheme.tertiary
    }
    HoloPersonaMode.SHARINGAN -> {
      primaryAccent = MaterialTheme.colorScheme.error
      secondaryAccent = MaterialTheme.colorScheme.primary
    }
    HoloPersonaMode.SAGE -> {
      primaryAccent = MaterialTheme.colorScheme.tertiary
      secondaryAccent = MaterialTheme.colorScheme.secondary
    }
  }
  val surfaceBg = MaterialTheme.colorScheme.background

  val transition = rememberInfiniteTransition(label = "nativeHolo")
  val orbitPhase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 7200, easing = LinearEasing)),
      label = "orbitAngle",
    )
  val pulse by
    transition.animateFloat(
      initialValue = 0.85f,
      targetValue = 1.15f,
      animationSpec =
        infiniteRepeatable(
          tween(durationMillis = 900),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "holoPulse",
    )

  Box(modifier = modifier) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(
        brush =
          Brush.radialGradient(
            colors = listOf(primaryAccent.copy(alpha = 0.22f), surfaceBg),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.maxDimension * 0.65f,
          )
      )

      val cx = size.width * 0.5f
      val cy = size.height * 0.46f
      val scale = (size.minDimension / 320f).coerceIn(0.65f, 1.8f)

      // Concentric J.A.R.V.I.S. gyro HUD rings
      val speedMult =
        when (phase) {
          LiveVoicePhase.THINKING -> 2.2f
          LiveVoicePhase.SPEAKING -> 1.6f
          LiveVoicePhase.LISTENING -> 1.2f
          LiveVoicePhase.IDLE -> 1.0f
        }
      for (ringIdx in 0..2) {
        val radius = (108f + ringIdx * 24f) * scale
        val angle = orbitPhase * speedMult * (if (ringIdx % 2 == 0) 1f else -1.3f)
        rotate(degrees = angle, pivot = Offset(cx, cy)) {
          drawArc(
            color = if (ringIdx == 1) secondaryAccent else primaryAccent.copy(alpha = 0.72f),
            startAngle = 10f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = (3f - ringIdx * 0.5f) * scale, cap = StrokeCap.Round),
          )
          drawArc(
            color = primaryAccent.copy(alpha = 0.45f),
            startAngle = 190f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round),
          )
        }
      }

      // Anime Naruto spiky hair silhouette
      val hairPath =
        Path().apply {
          moveTo(cx - 58f * scale, cy - 22f * scale)
          lineTo(cx - 88f * scale, cy - 48f * scale)
          lineTo(cx - 54f * scale, cy - 58f * scale)
          lineTo(cx - 64f * scale, cy - 94f * scale)
          lineTo(cx - 26f * scale, cy - 78f * scale)
          lineTo(cx, cy - 112f * scale)
          lineTo(cx + 26f * scale, cy - 78f * scale)
          lineTo(cx + 64f * scale, cy - 94f * scale)
          lineTo(cx + 54f * scale, cy - 58f * scale)
          lineTo(cx + 88f * scale, cy - 48f * scale)
          lineTo(cx + 58f * scale, cy - 22f * scale)
        }
      drawPath(
        path = hairPath,
        color = primaryAccent.copy(alpha = if (wireframeEnabled) 0.75f else 0.95f),
        style = Stroke(width = 2.8f * scale, cap = StrokeCap.Round),
      )

      // Anime tapered chin & jawline contour
      val facePath =
        Path().apply {
          moveTo(cx - 52f * scale, cy - 28f * scale)
          lineTo(cx - 46f * scale, cy + 22f * scale)
          lineTo(cx, cy + 68f * scale)
          lineTo(cx + 46f * scale, cy + 22f * scale)
          lineTo(cx + 52f * scale, cy - 28f * scale)
        }
      drawPath(
        path = facePath,
        color = primaryAccent,
        style = Stroke(width = 2.6f * scale, cap = StrokeCap.Round),
      )

      // Hitai-ate headband crest
      drawRect(
        color = primaryAccent.copy(alpha = 0.25f),
        topLeft = Offset(cx - 46f * scale, cy - 46f * scale),
        size = Size(92f * scale, 18f * scale),
      )
      drawCircle(
        color = secondaryAccent,
        radius = 6f * scale,
        center = Offset(cx, cy - 37f * scale),
        style = Stroke(width = 2.2f * scale),
      )

      // Dojutsu anime eyes
      for (side in listOf(-1f, 1f)) {
        val ex = cx + side * 23f * scale
        val ey = cy - 4f * scale
        drawCircle(
          color = primaryAccent.copy(alpha = 0.92f),
          radius = 10f * scale * (if (phase == LiveVoicePhase.SPEAKING) pulse else 1f),
          center = Offset(ex, ey),
          style = Stroke(width = 2.4f * scale),
        )
        drawCircle(
          color = secondaryAccent,
          radius = 4f * scale,
          center = Offset(ex, ey),
        )

        // 3 Naruto cheek whiskers per side
        for (w in -1..1) {
          val wy = cy + (18f + w * 8.5f) * scale
          drawLine(
            color = secondaryAccent.copy(alpha = 0.9f),
            start = Offset(cx + side * 20f * scale, wy),
            end = Offset(cx + side * 46f * scale, wy - 2f * scale),
            strokeWidth = 2.2f * scale,
            cap = StrokeCap.Round,
          )
        }
      }

      // Reactive Holo-Vocalizer EQ bars
      val activeBarGain =
        when (phase) {
          LiveVoicePhase.SPEAKING -> 18f * pulse
          LiveVoicePhase.THINKING -> 10f
          LiveVoicePhase.LISTENING -> 8f
          LiveVoicePhase.IDLE -> 3f
        }
      for (b in -3..3) {
        val barAngle = (orbitPhase * 0.08f + b * 0.9f).toDouble()
        val barHalfH = (3f + abs(sin(barAngle)).toFloat() * activeBarGain) * scale
        val bx = cx + b * 7.5f * scale
        val by = cy + 40f * scale
        drawLine(
          color = if (phase == LiveVoicePhase.SPEAKING) secondaryAccent else primaryAccent,
          start = Offset(bx, by - barHalfH),
          end = Offset(bx, by + barHalfH),
          strokeWidth = 3f * scale,
          cap = StrokeCap.Round,
        )
      }

      // Orbiting data nodes
      for (p in 0..5) {
        val a = (orbitPhase * 0.8f + p * 60f) * (PI / 180.0)
        val r = 132f * scale
        drawCircle(
          color = if (p % 2 == 0) primaryAccent else secondaryAccent,
          radius = 3.5f * scale,
          center = Offset(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat()),
        )
      }
    }

    // Subtle Recovery Pill in corner if WebGL renderer died
    Column(
      modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
      horizontalAlignment = Alignment.End,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
          Text(
            text = "Native Holo Core Active",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedButton(
            onClick = onRetryWebGl,
            modifier = Modifier.testTag("holo_retry_webgl_button"),
          ) {
            Icon(
              Icons.Filled.Refresh,
              contentDescription = "Retry Three.js WebGL renderer",
              modifier = Modifier.size(16.dp),
            )
            Text(" Retry 3D WebGL", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
  }
}
