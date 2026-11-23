package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

enum class ProjectMode(val label: String) {
    PROMPT("Text Prompt"),
    SCRIPT("Complete Script"),
    ANIMATE_PHOTO("Animate Photo");

    companion object {
        fun fromRaw(raw: String?): ProjectMode =
            entries.firstOrNull { it.name == raw } ?: PROMPT
    }
}

enum class AspectRatioOption(
    val label: String,
    val width: Int,
    val height: Int
) {
    RATIO_9_16("9:16", 720, 1280),
    RATIO_16_9("16:9", 1280, 720),
    RATIO_1_1("1:1", 720, 720);

    companion object {
        fun fromRaw(raw: String?): AspectRatioOption =
            entries.firstOrNull { it.name == raw } ?: RATIO_9_16
    }
}

enum class ProjectStatus(val label: String) {
    DRAFT("Draft"),
    GENERATING_SCRIPT("Writing Script"),
    GENERATING_IMAGES("Generating Visuals"),
    RENDERING_VIDEO("Rendering MP4"),
    COMPLETED("Completed"),
    FAILED("Needs Retry");

    companion object {
        fun fromRaw(raw: String?): ProjectStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

enum class ImageStyleOption(val label: String, val promptDirective: String) {
    PHOTOREALISTIC("Photorealistic", "ultra realistic photography, 35mm lens, natural lighting, sharp detail"),
    CINEMATIC("Cinematic", "cinematic film still, dramatic lighting, anamorphic lens, rich contrast"),
    THREE_D("3D Style", "high quality 3D octane render, stylized volumetric lighting, crisp materials"),
    ANIMATION("Animation", "modern feature animation art style, vibrant lighting, expressive composition"),
    FANTASY("Fantasy", "epic fantasy concept art, ethereal atmosphere, intricate cinematic detail"),
    PORTRAIT("Portrait", "editorial studio portrait, soft rim lighting, shallow depth of field, lifelike"),
    LANDSCAPE("Landscape", "sweeping golden hour landscape photography, atmospheric depth, wide angle");

    companion object {
        fun fromLabel(label: String?): ImageStyleOption =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) || it.name == label }
                ?: CINEMATIC
    }
}

enum class JobType {
    SCRIPT, IMAGE, VIDEO;

    companion object {
        fun fromRaw(raw: String?): JobType =
            entries.firstOrNull { it.name == raw } ?: VIDEO
    }
}

enum class JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED;

    companion object {
        fun fromRaw(raw: String?): JobStatus =
            entries.firstOrNull { it.name == raw } ?: QUEUED
    }
}

data class SceneItem(
    val sceneNumber: Int = 1,
    val title: String = "",
    val narrationText: String = "",
    val subtitleText: String = "",
    val visualPrompt: String = "",
    val motionDirection: String = "zoom_in",
    val durationSeconds: Int = 5,
    val localImagePath: String? = null
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("sceneNumber", sceneNumber)
        put("title", title)
        put("narrationText", narrationText)
        put("subtitleText", subtitleText)
        put("visualPrompt", visualPrompt)
        put("motionDirection", motionDirection)
        put("durationSeconds", durationSeconds)
        if (!localImagePath.isNullOrBlank()) {
            put("localImagePath", localImagePath)
        }
    }

    companion object {
        fun listToJson(scenes: List<SceneItem>): String {
            val array = JSONArray()
            scenes.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun listFromJson(json: String?): List<SceneItem> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { index ->
                    val obj = array.getJSONObject(index)
                    SceneItem(
                        sceneNumber = obj.optInt("sceneNumber", index + 1),
                        title = obj.optString("title", "Scene ${index + 1}"),
                        narrationText = obj.optString("narrationText", ""),
                        subtitleText = obj.optString("subtitleText", ""),
                        visualPrompt = obj.optString("visualPrompt", ""),
                        motionDirection = obj.optString("motionDirection", "zoom_in"),
                        durationSeconds = obj.optInt("durationSeconds", 5).coerceIn(2, 30),
                        localImagePath = obj.optString("localImagePath", "").ifBlank { null }
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val preferredStyle: String = ImageStyleOption.CINEMATIC.label,
    val preferredLanguage: String = "English",
    val dailyQuotaUsed: Long = 0L,
    val dailyQuotaLimit: Long = 12L,
    val quotaDateKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun remainingQuota(): Int = (dailyQuotaLimit - dailyQuotaUsed).toInt().coerceAtLeast(0)
}

@Keep
@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val mode: String = ProjectMode.PROMPT.name,
    val prompt: String = "",
    val script: String = "",
    val aspectRatio: String = AspectRatioOption.RATIO_9_16.name,
    val durationSeconds: Long = 15L,
    val visualStyle: String = ImageStyleOption.CINEMATIC.label,
    val language: String = "English",
    val narrationEnabled: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val sceneCount: Long = 3L,
    val status: String = ProjectStatus.DRAFT.name,
    val errorMessage: String? = null,
    val scenesJson: String = "[]",
    val localVideoPath: String? = null,
    val localThumbnailPath: String? = null,
    val videoWidth: Long = 720L,
    val videoHeight: Long = 1280L,
    val fileSizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun modeEnum(): ProjectMode = ProjectMode.fromRaw(mode)
    fun aspectRatioEnum(): AspectRatioOption = AspectRatioOption.fromRaw(aspectRatio)
    fun statusEnum(): ProjectStatus = ProjectStatus.fromRaw(status)
    fun parsedScenes(): List<SceneItem> = SceneItem.listFromJson(scenesJson)

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "title" to title.take(120).ifBlank { "Untitled Video" },
            "mode" to mode,
            "prompt" to prompt.take(2000),
            "script" to script.take(8000),
            "aspectRatio" to aspectRatio,
            "durationSeconds" to durationSeconds.coerceIn(3L, 180L),
            "visualStyle" to visualStyle.take(50).ifBlank { "Cinematic" },
            "language" to language.take(40).ifBlank { "English" },
            "narrationEnabled" to narrationEnabled,
            "subtitlesEnabled" to subtitlesEnabled,
            "sceneCount" to sceneCount.coerceIn(1L, 8L),
            "status" to status,
            "errorMessage" to errorMessage?.take(500),
            "scenesJson" to scenesJson.take(30000),
            "localVideoPath" to localVideoPath?.take(500),
            "localThumbnailPath" to localThumbnailPath?.take(500),
            "videoWidth" to videoWidth.coerceIn(0L, 4096L),
            "videoHeight" to videoHeight.coerceIn(0L, 4096L),
            "fileSizeBytes" to fileSizeBytes.coerceIn(0L, 500_000_000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "title" to title.take(120).ifBlank { "Untitled Video" },
            "mode" to mode,
            "prompt" to prompt.take(2000),
            "script" to script.take(8000),
            "aspectRatio" to aspectRatio,
            "durationSeconds" to durationSeconds.coerceIn(3L, 180L),
            "visualStyle" to visualStyle.take(50).ifBlank { "Cinematic" },
            "language" to language.take(40).ifBlank { "English" },
            "narrationEnabled" to narrationEnabled,
            "subtitlesEnabled" to subtitlesEnabled,
            "sceneCount" to sceneCount.coerceIn(1L, 8L),
            "status" to status,
            "errorMessage" to errorMessage?.take(500),
            "scenesJson" to scenesJson.take(30000),
            "localVideoPath" to localVideoPath?.take(500),
            "localThumbnailPath" to localThumbnailPath?.take(500),
            "videoWidth" to videoWidth.coerceIn(0L, 4096L),
            "videoHeight" to videoHeight.coerceIn(0L, 4096L),
            "fileSizeBytes" to fileSizeBytes.coerceIn(0L, 500_000_000L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class GeneratedImageItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prompt: String = "",
    val style: String = ImageStyleOption.PHOTOREALISTIC.label,
    val aspectRatio: String = AspectRatioOption.RATIO_1_1.name,
    val editedFromSource: Boolean = false,
    val localImagePath: String = "",
    val projectId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "prompt" to prompt.take(1500).ifBlank { "AI Artwork" },
            "style" to style.take(50).ifBlank { "Photorealistic" },
            "aspectRatio" to aspectRatio,
            "editedFromSource" to editedFromSource,
            "localImagePath" to localImagePath.take(500),
            "projectId" to projectId?.take(128),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class GenerationJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val jobType: String = JobType.VIDEO.name,
    val status: String = JobStatus.QUEUED.name,
    val progressPercent: Long = 0L,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val rating: Long = 5L,
    val opinion: String = "",
    val userEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
