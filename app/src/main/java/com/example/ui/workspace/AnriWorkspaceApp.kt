package com.example.ui.workspace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ai.AnriAiService
import com.example.data.local.AnriDatabase
import com.example.data.model.CitationLink
import com.example.data.repository.LocalWorkspaceRepository
import com.example.service.ScreenShareService
import com.example.ui.theme.AnriTheme
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.voice.BengaliVoiceController
import kotlinx.coroutines.launch

object AnriRoutes {
  const val CHAT_NEW = "chat"
  const val CHAT_WITH_ID = "chat/{conversationId}"
  const val PROMPTS = "prompts"
  const val STUDIO = "studio"
  const val VOICE = "voice"
  const val PROFILE = "profile"
  const val HOLO_FACE = "holo_face"

  fun chatRoute(conversationId: String?): String =
    if (conversationId.isNullOrBlank()) CHAT_NEW else "chat/$conversationId"
}

@Composable
fun AnriWorkspaceApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val aiService = remember { AnriAiService() }
  val voiceController = remember(context) { BengaliVoiceController(context.applicationContext) }

  DisposableEffect(voiceController) {
    onDispose {
      voiceController.release()
    }
  }

  val workspaceViewModel: WorkspaceViewModel =
    viewModel(
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val db = AnriDatabase.getInstance(app)
            WorkspaceViewModel(
              repository = LocalWorkspaceRepository(db),
              aiService = aiService,
            )
          }
        },
    )

  val studioViewModel: StudioViewModel =
    viewModel(
      factory = StudioViewModel.provideFactory(aiService),
    )

  val voiceViewModel: LiveVoiceViewModel =
    viewModel(
      factory = LiveVoiceViewModel.provideFactory(aiService, voiceController),
    )

  val profile by workspaceViewModel.userProfile.collectAsStateWithLifecycle()
  val conversations by workspaceViewModel.conversations.collectAsStateWithLifecycle()
  val activeConvId by workspaceViewModel.activeConversationId.collectAsStateWithLifecycle()
  val statusBanner by workspaceViewModel.statusBanner.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: AnriRoutes.CHAT_NEW

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(statusBanner) {
    val msg = statusBanner
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      workspaceViewModel.clearStatusBanner()
    }
  }

  // User-started Screen Sharing consent & MediaProjection launcher
  var showScreenShareConsent by remember { mutableStateOf(false) }
  val screenCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        ScreenShareService.start(context, result.resultCode, result.data!!)
      }
    }

  // User-approved external link dialog
  var pendingLink by remember { mutableStateOf<CitationLink?>(null) }

  val drawerState = rememberDrawerState(DrawerValue.Closed)

  AnriTheme(darkModeOverride = profile?.darkModeOverride ?: "dark") {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isCompact = maxWidth < 720.dp

      val navigateToTopTab: (String) -> Unit = { route ->
        navController.navigate(route) {
          popUpTo(navController.graph.findStartDestination().id) { saveState = true }
          launchSingleTop = true
          restoreState = true
        }
      }

      val openConversationRoute: (String?) -> Unit = { convId ->
        workspaceViewModel.selectConversation(convId)
        navController.navigate(AnriRoutes.chatRoute(convId)) {
          popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
          launchSingleTop = true
        }
      }

      val sidebarContent: @Composable (Modifier) -> Unit = { mod ->
        ConversationSidebarContent(
          conversations = conversations,
          activeConversationId = activeConvId,
          onNewConversation = {
            workspaceViewModel.startNewConversation()
            openConversationRoute(null)
            scope.launch { drawerState.close() }
          },
          onSelectConversation = { convId ->
            openConversationRoute(convId)
            scope.launch { drawerState.close() }
          },
          onTogglePin = workspaceViewModel::togglePinConversation,
          onRenameConversation = workspaceViewModel::renameConversation,
          onDeleteConversation = workspaceViewModel::deleteConversation,
          modifier = mod,
        )
      }

      val workspaceNavHost: @Composable (Modifier) -> Unit = { mod ->
        NavHost(
          navController = navController,
          startDestination = AnriRoutes.CHAT_NEW,
          modifier = mod,
        ) {
          composable(AnriRoutes.CHAT_NEW) {
            ChatWorkspaceScreen(
              viewModel = workspaceViewModel,
              voiceController = voiceController,
              isCompactScreen = isCompact,
              onOpenDrawer = { scope.launch { drawerState.open() } },
              onOpenLiveVoice = { navigateToTopTab(AnriRoutes.VOICE) },
              onOpenHoloFace = { navController.navigate(AnriRoutes.HOLO_FACE) { launchSingleTop = true } },
              onStartScreenShareFlow = { showScreenShareConsent = true },
              onStopScreenShare = { ScreenShareService.stop(context) },
              onRequestOpenLink = { pendingLink = it },
              onConversationCreated = { newId ->
                navController.navigate(AnriRoutes.chatRoute(newId)) {
                  popUpTo(AnriRoutes.CHAT_NEW) { inclusive = true }
                  launchSingleTop = true
                }
              },
            )
          }

          composable(
            route = AnriRoutes.CHAT_WITH_ID,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
          ) { entry ->
            val routeConvId = entry.arguments?.getString("conversationId")
            LaunchedEffect(routeConvId) {
              if (!routeConvId.isNullOrBlank()) {
                workspaceViewModel.selectConversation(routeConvId)
              }
            }
            ChatWorkspaceScreen(
              viewModel = workspaceViewModel,
              voiceController = voiceController,
              isCompactScreen = isCompact,
              onOpenDrawer = { scope.launch { drawerState.open() } },
              onOpenLiveVoice = { navigateToTopTab(AnriRoutes.VOICE) },
              onOpenHoloFace = { navController.navigate(AnriRoutes.HOLO_FACE) { launchSingleTop = true } },
              onStartScreenShareFlow = { showScreenShareConsent = true },
              onStopScreenShare = { ScreenShareService.stop(context) },
              onRequestOpenLink = { pendingLink = it },
              onConversationCreated = { _ -> },
            )
          }

          composable(AnriRoutes.PROMPTS) {
            SavedPromptsScreen(
              viewModel = workspaceViewModel,
              onUsePromptInChat = { body ->
                workspaceViewModel.setComposerText(body)
                navigateToTopTab(AnriRoutes.chatRoute(activeConvId))
              },
            )
          }

          composable(AnriRoutes.STUDIO) {
            MediaStudioScreen(viewModel = studioViewModel)
          }

          composable(AnriRoutes.VOICE) {
            LiveVoiceScreen(
              viewModel = voiceViewModel,
              profile = profile,
              onOpenFullHoloFace = { navController.navigate(AnriRoutes.HOLO_FACE) { launchSingleTop = true } },
            )
          }

          composable(AnriRoutes.PROFILE) {
            ProfileSettingsScreen(
              viewModel = workspaceViewModel,
              voiceController = voiceController,
            )
          }

          composable(AnriRoutes.HOLO_FACE) {
            HoloFaceScreen(
              voiceViewModel = voiceViewModel,
              profile = profile,
              onNavigateBack = { navController.popBackStack() },
            )
          }
        }
      }

      if (isCompact) {
        ModalNavigationDrawer(
          drawerState = drawerState,
          drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(310.dp)) {
              sidebarContent(Modifier.fillMaxSize())
            }
          },
        ) {
          Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
              if (currentRoute != AnriRoutes.HOLO_FACE) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                  val isChat = currentRoute.startsWith("chat")
                  NavigationBarItem(
                    selected = isChat,
                    onClick = { navigateToTopTab(AnriRoutes.chatRoute(activeConvId)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                  )
                  NavigationBarItem(
                    selected = currentRoute == AnriRoutes.PROMPTS,
                    onClick = { navigateToTopTab(AnriRoutes.PROMPTS) },
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = "Prompts") },
                    label = { Text("Prompts") },
                  )
                  NavigationBarItem(
                    selected = currentRoute == AnriRoutes.STUDIO,
                    onClick = { navigateToTopTab(AnriRoutes.STUDIO) },
                    icon = { Icon(Icons.Filled.Image, contentDescription = "Studio") },
                    label = { Text("Studio") },
                  )
                  NavigationBarItem(
                    selected = currentRoute == AnriRoutes.VOICE,
                    onClick = { navigateToTopTab(AnriRoutes.VOICE) },
                    icon = { Icon(Icons.Filled.GraphicEq, contentDescription = "Voice") },
                    label = { Text("Voice") },
                  )
                  NavigationBarItem(
                    selected = currentRoute == AnriRoutes.PROFILE,
                    onClick = { navigateToTopTab(AnriRoutes.PROFILE) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                  )
                }
              }
            },
          ) { innerPadding ->
            workspaceNavHost(Modifier.fillMaxSize().padding(innerPadding))
          }
        }
      } else {
        Scaffold(
          snackbarHost = { SnackbarHost(snackbarHostState) },
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
              val isChat = currentRoute.startsWith("chat")
              NavigationRailItem(
                selected = isChat,
                onClick = { navigateToTopTab(AnriRoutes.chatRoute(activeConvId)) },
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                label = { Text("Chat") },
              )
              NavigationRailItem(
                selected = currentRoute == AnriRoutes.PROMPTS,
                onClick = { navigateToTopTab(AnriRoutes.PROMPTS) },
                icon = { Icon(Icons.Filled.Bookmark, contentDescription = "Prompts") },
                label = { Text("Prompts") },
              )
              NavigationRailItem(
                selected = currentRoute == AnriRoutes.STUDIO,
                onClick = { navigateToTopTab(AnriRoutes.STUDIO) },
                icon = { Icon(Icons.Filled.Image, contentDescription = "Studio") },
                label = { Text("Studio") },
              )
              NavigationRailItem(
                selected = currentRoute == AnriRoutes.VOICE,
                onClick = { navigateToTopTab(AnriRoutes.VOICE) },
                icon = { Icon(Icons.Filled.GraphicEq, contentDescription = "Voice") },
                label = { Text("Voice") },
              )
              NavigationRailItem(
                selected = currentRoute == AnriRoutes.HOLO_FACE,
                onClick = { navController.navigate(AnriRoutes.HOLO_FACE) { launchSingleTop = true } },
                icon = { Icon(Icons.Filled.ViewInAr, contentDescription = "3D Core") },
                label = { Text("3D Core") },
              )
              NavigationRailItem(
                selected = currentRoute == AnriRoutes.PROFILE,
                onClick = { navigateToTopTab(AnriRoutes.PROFILE) },
                icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                label = { Text("Profile") },
              )
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (currentRoute.startsWith("chat")) {
              sidebarContent(Modifier.width(280.dp).fillMaxHeight())
              VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              workspaceNavHost(Modifier.fillMaxSize())
            }
          }
        }
      }
    }

    if (showScreenShareConsent) {
      ScreenShareConsentDialog(
        onConfirmStart = {
          showScreenShareConsent = false
          val manager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
          screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
        },
        onDismiss = { showScreenShareConsent = false },
      )
    }

    val linkToApprove = pendingLink
    if (linkToApprove != null) {
      LinkApprovalDialog(
        citation = linkToApprove,
        onConfirmOpen = { approvedUrl ->
          pendingLink = null
          runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(approvedUrl))
            context.startActivity(intent)
          }
        },
        onDismiss = { pendingLink = null },
      )
    }
  }
}
