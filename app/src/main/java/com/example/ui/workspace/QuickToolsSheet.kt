package com.example.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AiToolkitAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickToolsSheet(
  onRunToolPrompt: (AiToolkitAction, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedTool by remember { mutableStateOf(AiToolkitAction.FAST_QA) }
  var toolInput by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.AutoAwesome,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "ANRI AI Quick Toolkit",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AiToolkitAction.entries.forEach { action ->
          val isSelected = action == selectedTool
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isSelected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            border =
              if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
              else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
              Modifier.fillMaxWidth(0.48f)
                .clickable { selectedTool = action }
                .testTag("toolkit_item_${action.id}"),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = toolIconFor(action),
                  contentDescription = null,
                  tint =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = action.title,
                  style = MaterialTheme.typography.titleSmall,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
              }
              Text(
                text = action.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = toolInput,
        onValueChange = { toolInput = it },
        label = { Text(selectedTool.title) },
        placeholder = { Text(selectedTool.placeholder) },
        minLines = 3,
        maxLines = 5,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("toolkit_input_field"),
      )

      Button(
        onClick = {
          val clean = toolInput.trim()
          if (clean.isNotEmpty()) {
            onRunToolPrompt(selectedTool, selectedTool.promptPrefix + clean)
            onDismiss()
          }
        },
        enabled = toolInput.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("toolkit_run_button"),
      ) {
        Icon(Icons.Filled.Bolt, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Run ${selectedTool.title} Now")
      }

      Spacer(modifier = Modifier.height(14.dp))
    }
  }
}

private fun toolIconFor(action: AiToolkitAction): ImageVector =
  when (action) {
    AiToolkitAction.FAST_QA -> Icons.Filled.Bolt
    AiToolkitAction.TRANSLATE_BN -> Icons.Filled.Translate
    AiToolkitAction.SUMMARIZE -> Icons.Filled.Description
    AiToolkitAction.CODE_FIX -> Icons.Filled.Code
    AiToolkitAction.EMAIL_PRO -> Icons.Filled.Edit
    AiToolkitAction.IDEA_SPARK -> Icons.Filled.Lightbulb
  }
