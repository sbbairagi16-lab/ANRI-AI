package com.example.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AiModelSpec
import com.example.data.model.CitationLink
import com.example.data.model.Conversation
import com.example.data.model.ModelTask
import com.example.data.model.WorkspaceModelCatalog

@Composable
fun LinkApprovalDialog(
  citation: CitationLink,
  onConfirmOpen: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = { Text("Open external link?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            "ANRI AI never opens links automatically. Review the destination below before opening it in your browser:",
          style = MaterialTheme.typography.bodyMedium,
        )
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            if (citation.title.isNotBlank() && citation.title != citation.url) {
              Text(
                text = citation.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
              text = citation.url,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmOpen(citation.url) },
        modifier = Modifier.testTag("confirm_open_link_button"),
      ) {
        Text("Open in Browser")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
fun ScreenShareConsentDialog(
  onConfirmStart: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.ScreenShare,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = { Text("Start screen sharing?") },
    text = {
      Text(
        text =
          "Android will ask your permission to share your screen. ANRI AI only captures a frame when you tap 'Capture Screen' during an active session, and you can stop sharing anytime.",
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmStart,
        modifier = Modifier.testTag("confirm_screen_share_button"),
      ) {
        Text("Continue")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelSelectorSheet(
  selectedModelId: String,
  taskFilter: ModelTask,
  onSelectModel: (AiModelSpec) -> Unit,
  onDismiss: () -> Unit,
) {
  val models =
    remember(taskFilter) {
      WorkspaceModelCatalog.models.filter { it.task == taskFilter }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Workspace Model Catalog · ${taskFilter.label}",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = WorkspaceModelCatalog.AVAILABILITY_DISCLOSURE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      models.forEach { spec ->
        val isSelected = spec.id == selectedModelId
        Surface(
          shape = RoundedCornerShape(16.dp),
          color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          border =
            if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier =
            Modifier.fillMaxWidth()
              .clickable {
                onSelectModel(spec)
                onDismiss()
              }
              .testTag("model_option_${spec.id}"),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = spec.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = spec.id + if (spec.requiresBlaze) " · Blaze Plan" else " · Standard Tier",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (isSelected) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Selected model",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = spec.summary,
              style = MaterialTheme.typography.bodySmall,
              color =
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              spec.capabilities.forEach { cap ->
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                  Text(
                    text = cap,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(18.dp))
    }
  }
}

@Composable
fun ConversationSidebarContent(
  conversations: List<Conversation>,
  activeConversationId: String?,
  onNewConversation: () -> Unit,
  onSelectConversation: (String) -> Unit,
  onTogglePin: (Conversation) -> Unit,
  onRenameConversation: (Conversation, String) -> Unit,
  onDeleteConversation: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember { mutableStateOf("") }
  var renamingTarget by remember { mutableStateOf<Conversation?>(null) }
  var renameText by remember { mutableStateOf("") }

  val filtered =
    remember(conversations, searchQuery) {
      if (searchQuery.isBlank()) conversations
      else {
        conversations.filter {
          it.title.contains(searchQuery, ignoreCase = true) ||
            it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
        }
      }
    }

  Column(
    modifier =
      modifier
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.surface)
        .padding(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Conversations",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Button(
        onClick = onNewConversation,
        modifier = Modifier.testTag("new_conversation_button"),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("New Chat")
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Filter threads...") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("conversation_search_input"),
      shape = RoundedCornerShape(12.dp),
    )

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(8.dp))

    if (filtered.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (conversations.isEmpty()) "Start your first ANRI AI thread." else "No matching threads.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(filtered, key = { it.id }) { conv ->
          val isActive = conv.id == activeConversationId
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isActive) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            border =
              if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
              else null,
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onSelectConversation(conv.id) }
                .testTag("conversation_item_${conv.id}"),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = conv.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                  color =
                    if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = { onTogglePin(conv) },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                    imageVector = if (conv.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (conv.pinned) "Unpin conversation" else "Pin conversation",
                    tint =
                      if (conv.pinned) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                }
                IconButton(
                  onClick = {
                    renamingTarget = conv
                    renameText = conv.title
                  },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename conversation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                }
                IconButton(
                  onClick = { onDeleteConversation(conv.id) },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete conversation",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
              if (conv.lastMessagePreview.isNotBlank()) {
                Text(
                  text = conv.lastMessagePreview,
                  style = MaterialTheme.typography.bodySmall,
                  color =
                    if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "anri://chat/${conv.id.take(8)} · ${conv.messageCount} msgs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  val renameConv = renamingTarget
  if (renameConv != null) {
    AlertDialog(
      onDismissRequest = { renamingTarget = null },
      title = { Text("Rename conversation") },
      text = {
        OutlinedTextField(
          value = renameText,
          onValueChange = { renameText = it },
          singleLine = true,
          label = { Text("Title") },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRenameConversation(renameConv, renameText)
            renamingTarget = null
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { renamingTarget = null }) {
          Text("Cancel")
        }
      },
    )
  }
}
