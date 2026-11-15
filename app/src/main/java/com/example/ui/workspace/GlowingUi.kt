package com.example.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws an ambient radial aurora backdrop with ANRI AI's signature amber-gold and luminous teal glow.
 */
@Composable
fun Modifier.anriAuroraBackground(): Modifier {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val bg = MaterialTheme.colorScheme.background
  return this
    .background(bg)
    .drawBehind {
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(secondary.copy(alpha = 0.14f), bg.copy(alpha = 0f)),
            center = Offset(size.width * 0.85f, size.height * 0.10f),
            radius = size.maxDimension * 0.52f,
          ),
        radius = size.maxDimension * 0.52f,
        center = Offset(size.width * 0.85f, size.height * 0.10f),
      )
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.13f), bg.copy(alpha = 0f)),
            center = Offset(size.width * 0.12f, size.height * 0.78f),
            radius = size.maxDimension * 0.55f,
          ),
        radius = size.maxDimension * 0.55f,
        center = Offset(size.width * 0.12f, size.height * 0.78f),
      )
    }
}

/**
 * Applies a luminous gradient border and soft ambient shadow glow to featured cards and buttons.
 */
@Composable
fun Modifier.anriGlowBorder(
  shape: Shape = RoundedCornerShape(20.dp),
  width: Dp = 1.25.dp,
  elevation: Dp = 10.dp,
  intensity: Float = 0.55f,
): Modifier {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val tertiary = MaterialTheme.colorScheme.tertiary
  return this
    .shadow(
      elevation = elevation,
      shape = shape,
      ambientColor = primary.copy(alpha = 0.45f),
      spotColor = secondary.copy(alpha = 0.55f),
    )
    .border(
      width = width,
      brush =
        Brush.linearGradient(
          colors =
            listOf(
              secondary.copy(alpha = intensity),
              primary.copy(alpha = intensity),
              tertiary.copy(alpha = intensity * 0.75f),
            )
        ),
      shape = shape,
    )
}

@Composable
fun anriAccentGradientBrush(): Brush {
  return Brush.horizontalGradient(
    colors =
      listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
      )
  )
}
