package com.example.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavedPrompt
import com.example.ui.viewmodel.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPromptsScreen(
  viewModel: WorkspaceViewModel,
  onUsePromptInChat: (String) -> Unit,
) {
  val prompts by viewModel.savedPrompts.collectAsStateWithLifecycle()
  val categories = listOf("All", "Writing", "Coding", "Bengali", "Research", "Creative")
  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }
  var editingPrompt by remember { mutableStateOf<SavedPrompt?>(null) }
  var showEditor by remember { mutableStateOf(false) }

  val filtered =
    remember(prompts, selectedCategory, searchQuery) {
      prompts.filter { p ->
        val catOk = selectedCategory == "All" || p.category.equals(selectedCategory, ignoreCase = true)
        val searchOk =
          searchQuery.isBlank() ||
            p.title.contains(searchQuery, ignoreCase = true) ||
            p.body.contains(searchQuery, ignoreCase = true)
        catOk && searchOk
      }
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Saved Prompts") },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingPrompt = null
          showEditor = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_saved_prompt_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Create saved prompt")
      }
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .anriAuroraBackground()
          .padding(innerPadding)
          .padding(horizontal = 16.dp),
    ) {
      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search saved prompts...") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("search_prompts_input"),
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        categories.forEach { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { selectedCategory = cat },
            label = { Text(cat) },
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Filled.Bookmark,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No saved prompts yet",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Tap + to store reusable writing, coding, or Bengali instructions.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(filtered, key = { it.id }) { prompt ->
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier =
                  Modifier.fillMaxWidth()
                    .anriGlowBorder(shape = RoundedCornerShape(16.dp), intensity = 0.35f)
                    .testTag("saved_prompt_card_${prompt.id}"),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = prompt.category.uppercase(),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                      text = prompt.title,
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                  Row {
                    IconButton(
                      onClick = {
                        editingPrompt = prompt
                        showEditor = true
                      }
                    ) {
                      Icon(Icons.Filled.Edit, contentDescription = "Edit prompt")
                    }
                    IconButton(onClick = { viewModel.deletePrompt(prompt.id) }) {
                      Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete prompt",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = prompt.body,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                  onClick = { onUsePromptInChat(prompt.body) },
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Use in Chat")
                }
              }
            }
          }
        }
      }
    }
  }

  if (showEditor) {
    PromptEditorDialog(
      initial = editingPrompt,
      categories = categories.drop(1),
      onSave = { title, body, category ->
        viewModel.savePrompt(
          promptId = editingPrompt?.id,
          title = title,
          body = body,
          category = category,
        )
        showEditor = false
      },
      onDismiss = { showEditor = false },
    )
  }
}

@Composable
private fun PromptEditorDialog(
  initial: SavedPrompt?,
  categories: List<String>,
  onSave: (String, String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
  var body by remember(initial) { mutableStateOf(initial?.body ?: "") }
  var category by remember(initial) { mutableStateOf(initial?.category ?: categories.first()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "New Saved Prompt" else "Edit Prompt") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("prompt_title_input"),
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          categories.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat) },
            )
          }
        }
        OutlinedTextField(
          value = body,
          onValueChange = { body = it },
          label = { Text("Prompt Template") },
          minLines = 3,
          maxLines = 6,
          modifier = Modifier.fillMaxWidth().testTag("prompt_body_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(title, body, category) },
        enabled = title.isNotBlank() && body.isNotBlank(),
        modifier = Modifier.testTag("save_prompt_confirm_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
