package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.LumoreAiService
import com.example.data.model.ChatMessage
import com.example.data.model.ChatModelTier
import com.example.data.model.ChatThread
import com.example.data.model.UiState
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val aiService: LumoreAiService,
    private val currentUserId: String,
    displayName: String,
    email: String?
) : ViewModel() {

    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    val activeThread: StateFlow<ChatThread?> = _activeThreadId
        .flatMapLatest { threadId ->
            if (threadId.isNullOrBlank()) {
                flowOf<ChatThread?>(null)
            } else {
                chatRepository.observeThread(currentUserId, threadId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing active thread", firestoreError ?: error)
                        emit(null)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _activeThreadId
        .flatMapLatest { threadId ->
            if (threadId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                chatRepository.observeMessages(currentUserId, threadId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing chat messages", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load conversation."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    private val _selectedTier = MutableStateFlow(ChatModelTier.BALANCED)
    val selectedTier: StateFlow<ChatModelTier> = _selectedTier.asStateFlow()

    private val _attachedBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedBitmap: StateFlow<Bitmap?> = _attachedBitmap.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _pendingUserMessage = MutableStateFlow<String?>(null)
    val pendingUserMessage: StateFlow<String?> = _pendingUserMessage.asStateFlow()

    private val _streamingAssistantText = MutableStateFlow<String?>(null)
    val streamingAssistantText: StateFlow<String?> = _streamingAssistantText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.ensureUserProfile(currentUserId, displayName, email)
        }
    }

    fun setModelTier(tier: ChatModelTier) {
        _selectedTier.value = tier
    }

    fun setAttachedBitmap(bitmap: Bitmap?) {
        _attachedBitmap.value = bitmap
    }

    fun startNewChat() {
        _activeThreadId.value = null
        _pendingUserMessage.value = null
        _streamingAssistantText.value = null
        _attachedBitmap.value = null
        _errorMessage.value = null
    }

    fun openThread(threadId: String) {
        _activeThreadId.value = threadId
        _pendingUserMessage.value = null
        _streamingAssistantText.value = null
        _attachedBitmap.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun toggleBookmark() {
        val thread = activeThread.value ?: return
        viewModelScope.launch {
            chatRepository.toggleBookmark(
                userId = currentUserId,
                threadId = thread.id,
                bookmarked = !thread.bookmarked
            ).onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Couldn't update bookmark."
            }
        }
    }

    fun renameCurrentThread(newTitle: String) {
        val thread = activeThread.value ?: return
        viewModelScope.launch {
            chatRepository.renameThread(
                userId = currentUserId,
                threadId = thread.id,
                newTitle = newTitle
            ).onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Couldn't rename conversation."
            }
        }
    }

    fun sendMessage(rawText: String) {
        val trimmed = rawText.trim()
        val imageSnapshot = _attachedBitmap.value
        if ((trimmed.isEmpty() && imageSnapshot == null) || _isStreaming.value) return

        val threadIdSnapshot = _activeThreadId.value
        val existingThread = activeThread.value
        val historySnapshot = (messagesState.value as? UiState.Success)?.data.orEmpty()
        val displayUserText = trimmed.ifEmpty { "Analyze this image" }

        viewModelScope.launch {
            _isStreaming.value = true
            _pendingUserMessage.value = displayUserText
            _streamingAssistantText.value = ""
            _attachedBitmap.value = null
            _errorMessage.value = null

            var finalReply = ""
            try {
                aiService.streamChatReply(
                    tier = _selectedTier.value,
                    userMessage = displayUserText,
                    attachedImage = imageSnapshot,
                    history = historySnapshot
                ).collect { partial ->
                    finalReply = partial
                    _streamingAssistantText.value = partial
                }

                val resolvedTitle = existingThread?.title?.takeIf {
                    it.isNotBlank() && it != "New Chat" && !threadIdSnapshot.isNullOrBlank()
                } ?: aiService.suggestThreadTitle(displayUserText)

                val saveResult = chatRepository.saveTurn(
                    userId = currentUserId,
                    existingThreadId = threadIdSnapshot,
                    existingMessageCount = existingThread?.messageCount ?: historySnapshot.size.toLong(),
                    threadTitle = resolvedTitle,
                    userText = displayUserText,
                    userHasImage = imageSnapshot != null,
                    assistantText = finalReply
                )
                saveResult.onSuccess { savedId ->
                    _activeThreadId.value = savedId
                }.onFailure { err ->
                    _errorMessage.value = err.localizedMessage ?: "Failed to save message."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Streaming chat timed out", e)
                _errorMessage.value = "Lumore AI timed out after 60 seconds. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Streaming chat failed", e)
                _errorMessage.value = e.localizedMessage ?: "Lumore AI couldn't respond right now."
            } finally {
                _pendingUserMessage.value = null
                _streamingAssistantText.value = null
                _isStreaming.value = false
            }
        }
    }

    private companion object {
        const val TAG = "ChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
