package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.ai.LumoreAiService
import com.example.data.model.ThemeMode
import com.example.data.repository.ChatRepository
import com.example.data.repository.GalleryRepository
import com.example.ui.components.ProfileAccountSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.SavedChatsScreen
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.SavedChatsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class LumoreTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    CHAT(R.string.nav_chat, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_chat"),
    STUDIO(R.string.nav_studio, Icons.Rounded.Palette, Icons.Outlined.Palette, "nav_studio"),
    SAVED(R.string.nav_saved, Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks, "nav_saved")
}

@Composable
fun AppNavigation(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleLightDark: (Boolean) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val isDarkEffective = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            isDarkTheme = isDarkEffective,
            onToggleTheme = { onToggleLightDark(isDarkEffective) },
            onAuthSuccess = { }
        )
    } else {
        key(user.uid) {
            LumoreAuthenticatedShell(
                userId = user.uid,
                displayName = user.displayName.orEmpty().ifBlank { user.email?.substringBefore("@") ?: "Lumore User" },
                email = user.email,
                themeMode = themeMode,
                isDarkEffective = isDarkEffective,
                onSelectThemeMode = onSelectThemeMode,
                onToggleLightDark = { onToggleLightDark(isDarkEffective) }
            )
        }
    }
}

@Composable
private fun LumoreAuthenticatedShell(
    userId: String,
    displayName: String,
    email: String?,
    themeMode: ThemeMode,
    isDarkEffective: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleLightDark: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$userId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ChatViewModel(
                    chatRepository = ChatRepository(db),
                    aiService = LumoreAiService(),
                    currentUserId = userId,
                    displayName = displayName,
                    email = email
                )
            }
        }
    )

    val studioViewModel: ImageStudioViewModel = viewModel(
        key = "studio_$userId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                ImageStudioViewModel(
                    aiService = LumoreAiService(),
                    galleryRepository = GalleryRepository(app),
                    currentUserId = userId
                )
            }
        }
    )

    val savedChatsViewModel: SavedChatsViewModel = viewModel(
        key = "saved_$userId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SavedChatsViewModel(
                    chatRepository = ChatRepository(db),
                    currentUserId = userId
                )
            }
        }
    )

    var currentTab by rememberSaveable { mutableStateOf(LumoreTab.CHAT) }
    var showProfileSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = currentTab != LumoreTab.CHAT) {
        currentTab = LumoreTab.CHAT
    }

    val activeThread by chatViewModel.activeThread.collectAsStateWithLifecycle()
    val messagesState by chatViewModel.messagesState.collectAsStateWithLifecycle()
    val selectedTier by chatViewModel.selectedTier.collectAsStateWithLifecycle()
    val attachedBitmap by chatViewModel.attachedBitmap.collectAsStateWithLifecycle()
    val isStreaming by chatViewModel.isStreaming.collectAsStateWithLifecycle()
    val pendingUserMessage by chatViewModel.pendingUserMessage.collectAsStateWithLifecycle()
    val streamingAssistantText by chatViewModel.streamingAssistantText.collectAsStateWithLifecycle()
    val chatError by chatViewModel.errorMessage.collectAsStateWithLifecycle()

    val studioPrompt by studioViewModel.prompt.collectAsStateWithLifecycle()
    val studioAspect by studioViewModel.aspectRatio.collectAsStateWithLifecycle()
    val studioStyle by studioViewModel.stylePreset.collectAsStateWithLifecycle()
    val studioRefBitmap by studioViewModel.referenceBitmap.collectAsStateWithLifecycle()
    val studioUiState by studioViewModel.uiState.collectAsStateWithLifecycle()
    val studioGallery by studioViewModel.gallery.collectAsStateWithLifecycle()
    val isEnhancing by studioViewModel.isEnhancing.collectAsStateWithLifecycle()
    val studioBanner by studioViewModel.statusBanner.collectAsStateWithLifecycle()

    val threadsState by savedChatsViewModel.threadsState.collectAsStateWithLifecycle()
    val searchQuery by savedChatsViewModel.searchQuery.collectAsStateWithLifecycle()
    val bookmarkedOnly by savedChatsViewModel.bookmarkedOnly.collectAsStateWithLifecycle()
    val savedError by savedChatsViewModel.actionError.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        LumoreTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        LumoreTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                when (currentTab) {
                    LumoreTab.CHAT -> {
                        ChatScreen(
                            userDisplayName = displayName,
                            activeThread = activeThread,
                            messagesState = messagesState,
                            selectedTier = selectedTier,
                            attachedBitmap = attachedBitmap,
                            isStreaming = isStreaming,
                            pendingUserMessage = pendingUserMessage,
                            streamingAssistantText = streamingAssistantText,
                            errorMessage = chatError,
                            isDarkTheme = isDarkEffective,
                            onToggleTheme = onToggleLightDark,
                            onOpenProfile = { showProfileSheet = true },
                            onSelectTier = chatViewModel::setModelTier,
                            onAttachBitmap = chatViewModel::setAttachedBitmap,
                            onToggleBookmark = chatViewModel::toggleBookmark,
                            onRenameThread = chatViewModel::renameCurrentThread,
                            onStartNewChat = chatViewModel::startNewChat,
                            onSendMessage = chatViewModel::sendMessage,
                            onCreateImageFromText = { textSnippet ->
                                studioViewModel.setPrompt(textSnippet)
                                currentTab = LumoreTab.STUDIO
                            },
                            onClearError = chatViewModel::clearError,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LumoreTab.STUDIO -> {
                        ImageStudioScreen(
                            userDisplayName = displayName,
                            prompt = studioPrompt,
                            aspectRatio = studioAspect,
                            stylePreset = studioStyle,
                            referenceBitmap = studioRefBitmap,
                            uiState = studioUiState,
                            gallery = studioGallery,
                            isEnhancing = isEnhancing,
                            statusBanner = studioBanner,
                            isDarkTheme = isDarkEffective,
                            onToggleTheme = onToggleLightDark,
                            onOpenProfile = { showProfileSheet = true },
                            onPromptChange = studioViewModel::setPrompt,
                            onAspectRatioChange = studioViewModel::setAspectRatio,
                            onStylePresetChange = studioViewModel::setStylePreset,
                            onReferenceBitmapChange = studioViewModel::setReferenceBitmap,
                            onEnhancePrompt = studioViewModel::enhancePrompt,
                            onGenerate = studioViewModel::generate,
                            onRemix = studioViewModel::remixCurrent,
                            onSaveToPhotos = studioViewModel::saveToPhotos,
                            onShareBitmap = studioViewModel::shareBitmap,
                            onOpenGalleryItem = studioViewModel::openFromGallery,
                            onDeleteCreation = studioViewModel::deleteCreation,
                            onClearBanner = studioViewModel::clearStatusBanner,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LumoreTab.SAVED -> {
                        SavedChatsScreen(
                            userDisplayName = displayName,
                            threadsState = threadsState,
                            searchQuery = searchQuery,
                            bookmarkedOnly = bookmarkedOnly,
                            actionError = savedError,
                            isDarkTheme = isDarkEffective,
                            onToggleTheme = onToggleLightDark,
                            onOpenProfile = { showProfileSheet = true },
                            onSearchChange = savedChatsViewModel::setSearchQuery,
                            onSetBookmarkedOnly = savedChatsViewModel::setBookmarkedOnly,
                            onOpenThread = { threadId ->
                                chatViewModel.openThread(threadId)
                                currentTab = LumoreTab.CHAT
                            },
                            onToggleBookmark = savedChatsViewModel::toggleBookmark,
                            onDeleteThread = savedChatsViewModel::deleteThread,
                            onClearError = savedChatsViewModel::clearError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showProfileSheet) {
        ProfileAccountSheet(
            displayName = displayName,
            email = email,
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            onSignOut = {
                showProfileSheet = false
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope
                )
            },
            onDismiss = { showProfileSheet = false }
        )
    }
}
