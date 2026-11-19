package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.ChatModelTier
import com.example.data.model.ImageStylePreset
import com.example.data.model.MessageRole
import com.example.data.model.StudioAspectRatio
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "LumoreAiService"

class LumoreAiService {

    private val chatSystemInstruction = content {
        text(
            """
            You are Lumore AI, a sharp, warm, and deeply helpful conversational AI assistant.
            You excel at brainstorming, explaining complex topics clearly, writing and debugging code,
            drafting creative writing, analyzing uploaded photos, and crafting vivid visual prompts.
            Format responses cleanly using concise paragraphs, bullet points, bold highlights, and
            fenced code blocks when showing code.
            """.trimIndent()
        )
    }

    private val balancedChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ChatModelTier.BALANCED.modelId,
            systemInstruction = chatSystemInstruction
        )
    }

    private val fastChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ChatModelTier.FAST.modelId,
            systemInstruction = chatSystemInstruction
        )
    }

    private val utilityModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private fun chatModelFor(tier: ChatModelTier): GenerativeModel = when (tier) {
        ChatModelTier.BALANCED -> balancedChatModel
        ChatModelTier.FAST -> fastChatModel
    }

    fun streamChatReply(
        tier: ChatModelTier,
        userMessage: String,
        attachedImage: Bitmap?,
        history: List<ChatMessage>
    ): Flow<String> = flow {
        val model = chatModelFor(tier)
        val pastTurns = history.takeLast(14).map { msg ->
            val role = if (msg.roleEnum() == MessageRole.USER) "user" else "model"
            content(role) { text(msg.text) }
        }
        val chat = model.startChat(history = pastTurns)
        val messageContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(userMessage.ifBlank { "Please analyze this image and describe what you see." })
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(messageContent).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Lumore AI returned an empty response.")
        }
    }

    suspend fun suggestThreadTitle(firstUserMessage: String): String {
        val fallback = firstUserMessage.trim().take(48).ifEmpty { "Lumore Session" }
        return try {
            val response = withTimeout(20_000L) {
                utilityModel.generateContent(
                    "Summarize this user prompt into a concise 3 to 5 word title without quotes or punctuation:\n$firstUserMessage"
                )
            }
            response.text?.trim()
                ?.replace("\"", "")
                ?.take(60)
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun enhanceImagePrompt(rawPrompt: String, style: ImageStylePreset): Result<String> {
        return try {
            val styleNote = if (style == ImageStylePreset.NONE) "" else " Style target: ${style.label} (${style.promptModifier})."
            val response = withTimeout(30_000L) {
                balancedChatModel.generateContent(
                    "Rewrite this short image idea into a vivid, rich, cohesive single-paragraph image generation prompt (under 65 words, no quotes, no preamble).$styleNote Idea: $rawPrompt"
                )
            }
            val enhanced = response.text?.trim()?.removeSurrounding("\"")
            if (!enhanced.isNullOrBlank()) {
                Result.success(enhanced)
            } else {
                Result.failure(IllegalStateException("Could not enhance prompt."))
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Prompt enhancement timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prompt enhancement failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatio: StudioAspectRatio,
        stylePreset: ImageStylePreset,
        referenceBitmap: Bitmap?
    ): Result<Bitmap> {
        return try {
            val fullPrompt = if (stylePreset.promptModifier.isNotBlank()) {
                "${prompt.trim()}, ${stylePreset.promptModifier}"
            } else {
                prompt.trim()
            }

            val config = if (aspectRatio == StudioAspectRatio.SQUARE && referenceBitmap == null) {
                generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                }
            } else {
                generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(aspectRatio.toSdkAspectRatio())
                        .setImageSize(ImageSize.SIZE_1K)
                        .build()
                }
            }

            val imageModel = Firebase.ai.generativeModel(
                modelName = "gemini-nano-banana-2.1",
                generationConfig = config
            )

            val response = withTimeout(60_000L) {
                if (referenceBitmap != null) {
                    imageModel.generateContent(
                        content {
                            image(referenceBitmap)
                            text(fullPrompt)
                        }
                    )
                } else {
                    imageModel.generateContent(fullPrompt)
                }
            }

            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
                ?.image

            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("No image was returned by the model."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds. Please try again."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image generation quota exceeded", e)
            Result.failure(IllegalStateException("Rate limit exceeded. Please wait a moment or check your Firebase Blaze quota."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(e)
        }
    }

    private fun StudioAspectRatio.toSdkAspectRatio(): AspectRatio = when (this) {
        StudioAspectRatio.SQUARE -> AspectRatio.SQUARE_1x1
        StudioAspectRatio.LANDSCAPE_16_9 -> AspectRatio.LANDSCAPE_16x9
        StudioAspectRatio.PORTRAIT_9_16 -> AspectRatio.PORTRAIT_9x16
        StudioAspectRatio.LANDSCAPE_4_3 -> AspectRatio.LANDSCAPE_4x3
        StudioAspectRatio.PORTRAIT_3_4 -> AspectRatio.PORTRAIT_3x4
    }
}
