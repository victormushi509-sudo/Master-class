package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AspectRatioOption
import com.example.data.model.ImageStyleOption
import com.example.data.model.ProjectMode
import com.example.data.model.SceneItem
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FirebaseAiStudio"
private const val AI_TIMEOUT_MS = 60_000L

class FirebaseAiStudioProvider : AiStudioProvider {

    private val storyboardSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short compelling video title under 60 characters"),
                "fullScript" to Schema.string(description = "Complete cohesive narration script across all scenes"),
                "scenes" to Schema.array(
                    description = "Ordered list of video scenes",
                    items = Schema.obj(
                        properties = mapOf(
                            "sceneNumber" to Schema.integer(description = "1-based scene index"),
                            "title" to Schema.string(description = "Short scene title"),
                            "narrationText" to Schema.string(description = "Spoken voiceover line for this scene"),
                            "subtitleText" to Schema.string(description = "Concise readable on-screen subtitle"),
                            "visualPrompt" to Schema.string(description = "Detailed visual generation prompt describing subject, setting, lighting, and framing"),
                            "motionDirection" to Schema.enumeration(
                                values = listOf("zoom_in", "zoom_out", "pan_left", "pan_right", "tilt_up"),
                                description = "Camera motion direction"
                            ),
                            "durationSeconds" to Schema.integer(description = "Duration of this scene in seconds")
                        )
                    )
                )
            )
        )
    }

    private val singleSceneSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Updated scene title"),
                "narrationText" to Schema.string(description = "Updated voiceover narration line"),
                "subtitleText" to Schema.string(description = "Updated concise on-screen subtitle"),
                "visualPrompt" to Schema.string(description = "Fresh detailed visual prompt for this scene"),
                "motionDirection" to Schema.enumeration(
                    values = listOf("zoom_in", "zoom_out", "pan_left", "pan_right", "tilt_up"),
                    description = "Camera motion direction"
                )
            )
        )
    }

    private val storyboardModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
    }

    private val singleSceneModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleSceneSchema
            }
        )
    }

    private val imageModel9x16 by lazy { buildImageModel(AspectRatio.PORTRAIT_9x16) }
    private val imageModel16x9 by lazy { buildImageModel(AspectRatio.LANDSCAPE_16x9) }
    private val imageModel1x1 by lazy { buildImageModel(AspectRatio.SQUARE_1x1) }

    private fun buildImageModel(ratio: AspectRatio): GenerativeModel {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(ratio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        return Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    private fun imageModelFor(ratio: AspectRatioOption): GenerativeModel = when (ratio) {
        AspectRatioOption.RATIO_9_16 -> imageModel9x16
        AspectRatioOption.RATIO_16_9 -> imageModel16x9
        AspectRatioOption.RATIO_1_1 -> imageModel1x1
    }

    override suspend fun generateStoryboard(
        prompt: String,
        rawScript: String?,
        mode: ProjectMode,
        style: String,
        language: String,
        sceneCount: Int,
        totalDurationSeconds: Int
    ): StoryboardResult {
        val perSceneDuration = (totalDurationSeconds / sceneCount.coerceAtLeast(1)).coerceIn(3, 20)
        val directive = ImageStyleOption.fromLabel(style).promptDirective

        val instruction = buildString {
            appendLine("You are an expert AI video director and scriptwriter.")
            appendLine("Language for title, narrationText, and subtitleText: $language.")
            appendLine("Visual style for visualPrompt (keep visualPrompt in English for best image synthesis): $style ($directive).")
            appendLine("Create exactly $sceneCount cohesive scenes totaling approximately $totalDurationSeconds seconds (~$perSceneDuration seconds per scene).")
            appendLine("Each scene's narrationText should be natural to speak within ~$perSceneDuration seconds (8-18 words).")
            appendLine("Each scene's subtitleText should be crisp and concise (max 14 words).")
            when (mode) {
                ProjectMode.PROMPT -> {
                    appendLine("Mode: Story from Idea Prompt.")
                    appendLine("User idea: $prompt")
                }
                ProjectMode.SCRIPT -> {
                    appendLine("Mode: Divide Complete User Script into Visual Scenes.")
                    appendLine("User script:\n${rawScript.orEmpty().ifBlank { prompt }}")
                }
                ProjectMode.ANIMATE_PHOTO -> {
                    appendLine("Mode: Photo-to-Video Motion Storyboard.")
                    appendLine("Create $sceneCount progressive camera-angle / atmospheric story beats expanding from the uploaded photo.")
                    appendLine("Concept: $prompt")
                }
            }
        }

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                storyboardModel.generateContent(instruction)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("AI storyboard generator returned an empty response.")
            val root = JSONObject(rawJson)
            val title = root.optString("title", "AI Video Story").take(120)
            val fullScript = root.optString("fullScript", "")
            val scenesArray = root.getJSONArray("scenes")
            val scenes = (0 until scenesArray.length()).map { idx ->
                val obj = scenesArray.getJSONObject(idx)
                SceneItem(
                    sceneNumber = idx + 1,
                    title = obj.optString("title", "Scene ${idx + 1}"),
                    narrationText = obj.optString("narrationText", ""),
                    subtitleText = obj.optString("subtitleText", ""),
                    visualPrompt = obj.optString("visualPrompt", prompt),
                    motionDirection = obj.optString("motionDirection", "zoom_in"),
                    durationSeconds = obj.optInt("durationSeconds", perSceneDuration).coerceIn(3, 20)
                )
            }
            if (scenes.isEmpty()) {
                throw IllegalStateException("AI returned 0 scenes. Please refine your prompt.")
            }
            StoryboardResult(
                title = title,
                fullScript = fullScript.ifBlank { scenes.joinToString(" ") { it.narrationText } },
                scenes = scenes
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Storyboard generation timed out", e)
            throw IllegalStateException("Storyboard generation timed out after 60 seconds. Please retry.", e)
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "AI quota exceeded", e)
            throw IllegalStateException("Provider AI quota exceeded. Please try again later.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Storyboard generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to generate storyboard.", e)
        }
    }

    override suspend fun regenerateSingleScene(
        storyContext: String,
        existingScene: SceneItem,
        style: String,
        language: String
    ): SceneItem {
        val directive = ImageStyleOption.fromLabel(style).promptDirective
        val instruction = """
            Rewrite and improve Scene ${existingScene.sceneNumber} for this video story.
            Story context: $storyContext
            Current scene title: ${existingScene.title}
            Current narration: ${existingScene.narrationText}
            Language for title, narrationText, subtitleText: $language
            Visual style for visualPrompt (English): $style ($directive)
            Provide a fresh, vivid angle while keeping story continuity.
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                singleSceneModel.generateContent(instruction)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Scene regeneration returned empty response.")
            val obj = JSONObject(rawJson)
            existingScene.copy(
                title = obj.optString("title", existingScene.title),
                narrationText = obj.optString("narrationText", existingScene.narrationText),
                subtitleText = obj.optString("subtitleText", existingScene.subtitleText),
                visualPrompt = obj.optString("visualPrompt", existingScene.visualPrompt),
                motionDirection = obj.optString("motionDirection", existingScene.motionDirection)
            )
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Scene regeneration timed out. Please try again.", e)
        } catch (e: QuotaExceededException) {
            throw IllegalStateException("Provider AI quota exceeded. Please try again later.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Single scene regeneration failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to regenerate scene.", e)
        }
    }

    override suspend fun generateImage(
        prompt: String,
        style: String,
        aspectRatio: AspectRatioOption
    ): Bitmap {
        val styleOption = ImageStyleOption.fromLabel(style)
        val fullPrompt = "$prompt. Style: ${styleOption.label}, ${styleOption.promptDirective}. No watermarks, no text overlays."
        return try {
            val model = imageModelFor(aspectRatio)
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(fullPrompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image
                ?: throw IllegalStateException("Image model did not return an image. Check Firebase Blaze billing or prompt safety.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds.", e)
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image generation quota exceeded", e)
            throw IllegalStateException("Image generation quota exceeded. Please try again later.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Image generation failed.", e)
        }
    }

    override suspend fun editImage(
        sourceBitmap: Bitmap,
        instruction: String,
        style: String,
        aspectRatio: AspectRatioOption
    ): Bitmap {
        val styleOption = ImageStyleOption.fromLabel(style)
        val fullInstruction = "$instruction. Apply style: ${styleOption.label}, ${styleOption.promptDirective}."
        return try {
            val model = imageModelFor(aspectRatio)
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        image(sourceBitmap)
                        text(fullInstruction)
                    }
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image
                ?: throw IllegalStateException("Image editor did not return an edited image.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image editing timed out", e)
            throw IllegalStateException("Image editing timed out after 60 seconds.", e)
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image editing quota exceeded", e)
            throw IllegalStateException("Image generation quota exceeded. Please try again later.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image editing failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Image editing failed.", e)
        }
    }
}
