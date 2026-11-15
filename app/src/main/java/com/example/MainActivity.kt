package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.configureAppCheck
import com.example.ui.workspace.AnriWorkspaceApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase AI operations
    configureAppCheck(this, intent)

    enableEdgeToEdge()

    // STEP 2: Launch directly into the ANRI AI workspace without requiring Google Sign-In
    setContent {
      AnriWorkspaceApp()
    }
  }
}
