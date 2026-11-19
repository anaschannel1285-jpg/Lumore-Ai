package com.example.data.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class MessageRole {
    USER,
    ASSISTANT;

    companion object {
        fun fromRaw(raw: String?): MessageRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ASSISTANT
    }
}

enum class ChatModelTier(val modelId: String, val label: String, val badge: String) {
    BALANCED("gemini-3.8-flash", "Lumore Smart", "3.8 Flash"),
    FAST("gemini-3.5-flash-lite", "Lumore Fast", "Flash-Lite")
}

enum class StudioAspectRatio(val label: String, val ratioValue: Float) {
    SQUARE("1:1", 1f),
    LANDSCAPE_16_9("16:9", 16f / 9f),
    PORTRAIT_9_16("9:16", 9f / 16f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
    PORTRAIT_3_4("3:4", 3f / 4f)
}

enum class ImageStylePreset(val label: String, val promptModifier: String) {
    NONE("Natural", ""),
    PHOTOREALISTIC("Photoreal", "photorealistic, ultra-detailed, natural studio lighting, sharp focus"),
    STUDIO_3D("3D Render", "clean 3D octane studio render, soft volumetric lighting, smooth materials"),
    ANIME("Anime", "high-craft anime illustration, expressive linework, luminous cel shading"),
    OIL_PAINT("Oil Paint", "rich expressive oil painting on textured canvas, fine brushwork"),
    MINIMAL_VECTOR("Vector", "crisp minimal editorial flat vector art, balanced geometry, clean edges"),
    CYBERPUNK("Neon Noir", "atmospheric neon noir aesthetic, rain reflections, moody cinematic glow")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val lastMessagePreview: String = "",
    val bookmarked: Boolean = false,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val threadId: String = "",
    val role: String = MessageRole.USER.name,
    val text: String = "",
    val hasImage: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun roleEnum(): MessageRole = MessageRole.fromRaw(role)
}

data class GeneratedCreation(
    val id: String,
    val prompt: String,
    val styleLabel: String,
    val aspectLabel: String,
    val filePath: String,
    val createdAtMillis: Long,
    val isRemix: Boolean = false
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val creation: GeneratedCreation) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}
