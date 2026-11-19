package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatThread
import com.example.data.model.UiState
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedChatsViewModel(
    private val chatRepository: ChatRepository,
    private val currentUserId: String
) : ViewModel() {

    val threadsState: StateFlow<UiState<List<ChatThread>>> = chatRepository.observeThreads(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing saved threads", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load saved chats."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _bookmarkedOnly = MutableStateFlow(false)
    val bookmarkedOnly: StateFlow<Boolean> = _bookmarkedOnly.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setBookmarkedOnly(enabled: Boolean) {
        _bookmarkedOnly.value = enabled
    }

    fun clearError() {
        _actionError.value = null
    }

    fun toggleBookmark(thread: ChatThread) {
        viewModelScope.launch {
            chatRepository.toggleBookmark(
                userId = currentUserId,
                threadId = thread.id,
                bookmarked = !thread.bookmarked
            ).onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Couldn't update bookmark."
            }
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            chatRepository.deleteThread(currentUserId, threadId)
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: "Couldn't delete chat."
                }
        }
    }

    private companion object {
        const val TAG = "SavedChatsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
