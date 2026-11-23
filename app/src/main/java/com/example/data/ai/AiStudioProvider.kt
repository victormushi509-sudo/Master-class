package com.example.data.ai

import android.graphics.Bitmap
import com.example.data.model.AspectRatioOption
import com.example.data.model.ProjectMode
import com.example.data.model.SceneItem

data class StoryboardResult(
    val title: String,
    val fullScript: String,
    val scenes: List<SceneItem>
)

/**
 * Modular AI provider interface so text, storyboard, and image providers
 * can be swapped independently of UI or storage layers.
 */
interface AiStudioProvider {
    suspend fun generateStoryboard(
        prompt: String,
        rawScript: String?,
        mode: ProjectMode,
        style: String,
        language: String,
        sceneCount: Int,
        totalDurationSeconds: Int
    ): StoryboardResult

    suspend fun regenerateSingleScene(
        storyContext: String,
        existingScene: SceneItem,
        style: String,
        language: String
    ): SceneItem

    suspend fun generateImage(
        prompt: String,
        style: String,
        aspectRatio: AspectRatioOption
    ): Bitmap

    suspend fun editImage(
        sourceBitmap: Bitmap,
        instruction: String,
        style: String,
        aspectRatio: AspectRatioOption
    ): Bitmap
}
