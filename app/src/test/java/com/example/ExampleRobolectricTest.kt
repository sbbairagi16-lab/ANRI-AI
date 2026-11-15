package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ANRI AI", appName)
  }

  @Test
  fun `threejs holo face asset is bundled with required bridge and pbr scene`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = context.assets.open("anri_holo_face.html").bufferedReader().use { it.readText() }
    assertTrue(html.contains("three.module.min.js"))
    assertTrue(html.contains("OrbitControls"))
    assertTrue(html.contains("webglcontextlost"))
    assertTrue(html.contains("disposeAnriScene"))
    assertTrue(html.contains("ACESFilmicToneMapping"))
    assertTrue(html.contains("window.setAnriState"))
    assertTrue(html.contains("sharingan"))
  }

  @Test
  fun `assist and power live cast intents resolve to AnriAssistOverlayActivity`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager
    val assistIntent = Intent(Intent.ACTION_ASSIST).setPackage(context.packageName)
    val powerCastIntent =
      Intent("com.example.action.POWER_LIVE_CAST").setPackage(context.packageName)

    assertTrue(pm.queryIntentActivities(assistIntent, 0).isNotEmpty())
    assertTrue(pm.queryIntentActivities(powerCastIntent, 0).isNotEmpty())
  }
}
