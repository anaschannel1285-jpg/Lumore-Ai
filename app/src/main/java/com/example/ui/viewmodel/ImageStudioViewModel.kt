package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.LumoreAiService
import com.example.data.model.GeneratedCreation
import com.example.data.model.ImageGenUiState
import com.example.data.model.ImageStylePreset
import com.example.data.model.StudioAspectRatio
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImageStudioViewModel(
    private val aiService: LumoreAiService,
    private val galleryRepository: GalleryRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _aspectRatio = MutableStateFlow(StudioAspectRatio.SQUARE)
    val aspectRatio: StateFlow<StudioAspectRatio> = _aspectRatio.asStateFlow()

    private val _stylePreset = MutableStateFlow(ImageStylePreset.NONE)
    val stylePreset: StateFlow<ImageStylePreset> = _stylePreset.asStateFlow()

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    private val _gallery = MutableStateFlow<List<GeneratedCreation>>(emptyList())
    val gallery: StateFlow<List<GeneratedCreation>> = _gallery.asStateFlow()

    private val _isEnhancing = MutableStateFlow(false)
    val isEnhancing: StateFlow<Boolean> = _isEnhancing.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        refreshGallery()
    }

    fun refreshGallery() {
        viewModelScope.launch {
            _gallery.value = galleryRepository.loadCreations(currentUserId)
        }
    }

    fun setPrompt(value: String) {
        _prompt.value = value
    }

    fun setAspectRatio(ratio: StudioAspectRatio) {
        _aspectRatio.value = ratio
    }

    fun setStylePreset(preset: ImageStylePreset) {
        _stylePreset.value = preset
    }

    fun setReferenceBitmap(bitmap: Bitmap?) {
        _referenceBitmap.value = bitmap
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun enhancePrompt() {
        val current = _prompt.value.trim()
        if (current.isEmpty() || _isEnhancing.value) return
        viewModelScope.launch {
            _isEnhancing.value = true
            aiService.enhanceImagePrompt(current, _stylePreset.value)
                .onSuccess { improved ->
                    _prompt.value = improved
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Couldn't enhance prompt."
                }
            _isEnhancing.value = false
        }
    }

    fun generate() {
        val currentPrompt = _prompt.value.trim()
        if (currentPrompt.isEmpty() || _uiState.value is ImageGenUiState.Loading) return

        val ratio = _aspectRatio.value
        val style = _stylePreset.value
        val refBitmap = _referenceBitmap.value

        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading
            _statusBanner.value = null

            val result = aiService.generateImage(
                prompt = currentPrompt,
                aspectRatio = ratio,
                stylePreset = style,
                referenceBitmap = refBitmap
            )

            result.fold(
                onSuccess = { bitmap ->
                    val saved = galleryRepository.saveCreation(
                        userId = currentUserId,
                        bitmap = bitmap,
                        prompt = currentPrompt,
                        styleLabel = style.label,
                        aspectLabel = ratio.label,
                        isRemix = refBitmap != null
                    )
                    _gallery.value = galleryRepository.loadCreations(currentUserId)
                    _uiState.value = ImageGenUiState.Success(bitmap, saved)
                },
                onFailure = { err ->
                    _uiState.value = ImageGenUiState.Error(
                        err.localizedMessage ?: "Image generation failed."
                    )
                }
            )
        }
    }

    fun openFromGallery(creation: GeneratedCreation) {
        viewModelScope.launch {
            val bitmap = galleryRepository.loadBitmap(creation.filePath)
            if (bitmap != null) {
                _prompt.value = creation.prompt
                _uiState.value = ImageGenUiState.Success(bitmap, creation)
            } else {
                _statusBanner.value = "Could not load image from storage."
            }
        }
    }

    fun remixCurrent(bitmap: Bitmap) {
        _referenceBitmap.value = bitmap
        _statusBanner.value = "Image attached! Describe how you want to edit or remix it."
    }

    fun saveToPhotos(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            galleryRepository.saveToDevicePhotos(bitmap, prompt)
                .onSuccess { msg -> _statusBanner.value = msg }
                .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Couldn't save photo." }
        }
    }

    fun shareBitmap(bitmap: Bitmap, onUriReady: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            galleryRepository.createShareUri(bitmap)
                .onSuccess(onUriReady)
                .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Couldn't prepare share." }
        }
    }

    fun deleteCreation(creationId: String) {
        viewModelScope.launch {
            galleryRepository.deleteCreation(currentUserId, creationId)
            val updated = galleryRepository.loadCreations(currentUserId)
            _gallery.value = updated
            val currentState = _uiState.value
            if (currentState is ImageGenUiState.Success && currentState.creation.id == creationId) {
                _uiState.value = ImageGenUiState.Idle
            }
        }
    }
}
