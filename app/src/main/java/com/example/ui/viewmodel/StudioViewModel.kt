package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AiStudioProvider
import com.example.data.ai.FirebaseAiStudioProvider
import com.example.data.media.LocalMediaStore
import com.example.data.media.Mp4VideoAssembler
import com.example.data.media.NarrationSynthesizer
import com.example.data.model.AppFeedback
import com.example.data.model.AspectRatioOption
import com.example.data.model.GeneratedImageItem
import com.example.data.model.GenerationJob
import com.example.data.model.ImageStyleOption
import com.example.data.model.JobStatus
import com.example.data.model.JobType
import com.example.data.model.ProjectMode
import com.example.data.model.ProjectStatus
import com.example.data.model.SceneItem
import com.example.data.model.UserProfile
import com.example.data.model.VideoProject
import com.example.data.repository.StudioRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "StudioViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class StudioTab {
    CREATE, IMAGES, PROJECTS, GALLERY, ACCOUNT
}

data class VideoDraftUiState(
    val editingProjectId: String? = null,
    val title: String = "",
    val mode: ProjectMode = ProjectMode.PROMPT,
    val prompt: String = "",
    val script: String = "",
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_9_16,
    val durationSeconds: Int = 15,
    val visualStyle: ImageStyleOption = ImageStyleOption.CINEMATIC,
    val language: String = "English",
    val narrationEnabled: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val sceneCount: Int = 3,
    val uploadedPhotoPath: String? = null,
    val scenes: List<SceneItem> = emptyList(),
    val isBusy: Boolean = false,
    val regeneratingSceneIndex: Int? = null,
    val progressPercent: Float = 0f,
    val activeStepMessage: String? = null,
    val renderedVideoPath: String? = null,
    val hasAudioTrack: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class ImageGenUiState(
    val prompt: String = "",
    val style: ImageStyleOption = ImageStyleOption.PHOTOREALISTIC,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_1_1,
    val sourcePhotoPath: String? = null,
    val isGenerating: Boolean = false,
    val latestImagePath: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class StudioViewModel(
    private val currentUserId: String,
    private val currentUser: FirebaseUser,
    private val repository: StudioRepository,
    private val aiProvider: AiStudioProvider,
    private val mediaStore: LocalMediaStore,
    private val videoAssembler: Mp4VideoAssembler
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(StudioTab.CREATE)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _draftState = MutableStateFlow(VideoDraftUiState())
    val draftState: StateFlow<VideoDraftUiState> = _draftState.asStateFlow()

    private val _imageGenState = MutableStateFlow(ImageGenUiState())
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _storageUsedBytes = MutableStateFlow(mediaStore.totalStorageUsedBytes())
    val storageUsedBytes: StateFlow<Long> = _storageUsedBytes.asStateFlow()

    private val _globalBannerMessage = MutableStateFlow<String?>(null)
    val globalBannerMessage: StateFlow<String?> = _globalBannerMessage.asStateFlow()

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "Failed to load account profile")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val projectsState: StateFlow<UiState<List<VideoProject>>> = repository.observeProjects(currentUserId)
        .map<List<VideoProject>, UiState<List<VideoProject>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "Failed to load projects")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val generatedImagesState: StateFlow<UiState<List<GeneratedImageItem>>> = repository.observeGeneratedImages(currentUserId)
        .map<List<GeneratedImageItem>, UiState<List<GeneratedImageItem>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "Failed to load generated images")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val jobsState: StateFlow<UiState<List<GenerationJob>>> = repository.observeGenerationJobs(currentUserId)
        .map<List<GenerationJob>, UiState<List<GenerationJob>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "Failed to observe jobs")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val feedbackState: StateFlow<UiState<List<AppFeedback>>> = repository.observeFeedback(currentUserId)
        .map<List<AppFeedback>, UiState<List<AppFeedback>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFirestoreError(error, "Failed to load feedback")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUser).onSuccess { profile ->
                _draftState.update { state ->
                    if (state.editingProjectId == null && state.prompt.isBlank()) {
                        state.copy(
                            visualStyle = ImageStyleOption.fromLabel(profile.preferredStyle),
                            language = profile.preferredLanguage.ifBlank { "English" }
                        )
                    } else state
                }
            }
        }
    }

    private fun <T> unwrapFirestoreError(error: Throwable, fallback: String): UiState<T> {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, fallback, root)
        return UiState.Error(root.localizedMessage ?: fallback)
    }

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun clearBannerMessage() {
        _globalBannerMessage.value = null
    }

    fun refreshStorageUsage() {
        _storageUsedBytes.value = mediaStore.totalStorageUsedBytes()
    }

    // --- CREATE VIDEO DRAFT UPDATES ---

    fun updateDraftMode(mode: ProjectMode) {
        _draftState.update { it.copy(mode = mode, errorMessage = null, infoMessage = null) }
    }

    fun updateDraftTitle(title: String) {
        _draftState.update { it.copy(title = title.take(120)) }
    }

    fun updateDraftPrompt(prompt: String) {
        _draftState.update { it.copy(prompt = prompt.take(2000), errorMessage = null) }
    }

    fun updateDraftScript(script: String) {
        _draftState.update { it.copy(script = script.take(8000), errorMessage = null) }
    }

    fun updateDraftAspectRatio(ratio: AspectRatioOption) {
        _draftState.update { it.copy(aspectRatio = ratio) }
    }

    fun updateDraftDuration(seconds: Int) {
        _draftState.update { it.copy(durationSeconds = seconds.coerceIn(6, 90)) }
    }

    fun updateDraftStyle(style: ImageStyleOption) {
        _draftState.update { it.copy(visualStyle = style) }
    }

    fun updateDraftLanguage(language: String) {
        _draftState.update { it.copy(language = language) }
    }

    fun updateDraftNarration(enabled: Boolean) {
        _draftState.update { it.copy(narrationEnabled = enabled) }
    }

    fun updateDraftSubtitles(enabled: Boolean) {
        _draftState.update { it.copy(subtitlesEnabled = enabled) }
    }

    fun updateDraftSceneCount(count: Int) {
        _draftState.update { it.copy(sceneCount = count.coerceIn(1, 6)) }
    }

    fun resetNewVideoDraft() {
        _draftState.value = VideoDraftUiState()
    }

    fun setDraftUploadedPhoto(uri: Uri) {
        viewModelScope.launch {
            val bmp = mediaStore.loadBitmapFromUri(uri, maxDimension = 1280)
            if (bmp == null) {
                _draftState.update { it.copy(errorMessage = "Could not read selected photo.") }
                return@launch
            }
            val savedPath = mediaStore.saveBitmapAsJpeg(bmp, prefix = "upload")
            bmp.recycle()
            _draftState.update {
                it.copy(
                    uploadedPhotoPath = savedPath,
                    mode = ProjectMode.ANIMATE_PHOTO,
                    errorMessage = null,
                    infoMessage = "Photo loaded. Add motion notes or generate video."
                )
            }
            refreshStorageUsage()
        }
    }

    fun clearDraftUploadedPhoto() {
        _draftState.update { it.copy(uploadedPhotoPath = null) }
    }

    fun generateFullVideoWorkflow(autoAssembleMp4: Boolean = true) {
        val current = _draftState.value
        if (current.isBusy) return

        // Validate input per mode
        when (current.mode) {
            ProjectMode.PROMPT -> if (current.prompt.isBlank()) {
                _draftState.update { it.copy(errorMessage = "Describe your video idea first.") }
                return
            }
            ProjectMode.SCRIPT -> if (current.script.isBlank()) {
                _draftState.update { it.copy(errorMessage = "Paste or write your complete script first.") }
                return
            }
            ProjectMode.ANIMATE_PHOTO -> if (current.uploadedPhotoPath.isNullOrBlank()) {
                _draftState.update { it.copy(errorMessage = "Upload a photo to animate first.") }
                return
            }
        }

        viewModelScope.launch {
            _draftState.update {
                it.copy(
                    isBusy = true,
                    progressPercent = 0.05f,
                    activeStepMessage = "Checking daily free quota...",
                    errorMessage = null,
                    infoMessage = null
                )
            }

            val quotaCheck = repository.consumeDailyQuota(currentUserId)
            if (quotaCheck.isFailure) {
                _draftState.update {
                    it.copy(
                        isBusy = false,
                        progressPercent = 0f,
                        activeStepMessage = null,
                        errorMessage = quotaCheck.exceptionOrNull()?.message ?: "Daily quota reached."
                    )
                }
                return@launch
            }

            var projectId = current.editingProjectId
            val effectiveTitle = current.title.ifBlank {
                current.prompt.ifBlank { current.script }.trim().take(48).ifBlank { "AI Video Studio" }
            }
            val initialProject = VideoProject(
                id = projectId.orEmpty(),
                userId = currentUserId,
                title = effectiveTitle,
                mode = current.mode.name,
                prompt = current.prompt,
                script = current.script,
                aspectRatio = current.aspectRatio.name,
                durationSeconds = current.durationSeconds.toLong(),
                visualStyle = current.visualStyle.label,
                language = current.language,
                narrationEnabled = current.narrationEnabled,
                subtitlesEnabled = current.subtitlesEnabled,
                sceneCount = current.sceneCount.toLong(),
                status = ProjectStatus.GENERATING_SCRIPT.name,
                scenesJson = SceneItem.listToJson(current.scenes),
                videoWidth = current.aspectRatio.width.toLong(),
                videoHeight = current.aspectRatio.height.toLong()
            )

            if (projectId.isNullOrBlank()) {
                val created = repository.createProject(initialProject)
                if (created.isFailure) {
                    _draftState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = created.exceptionOrNull()?.message ?: "Failed to create project."
                        )
                    }
                    return@launch
                }
                projectId = created.getOrThrow()
                _draftState.update { it.copy(editingProjectId = projectId, title = effectiveTitle) }
            } else {
                repository.updateProject(initialProject)
            }

            val activeProjectId = projectId!!
            val jobId = repository.createGenerationJob(
                projectId = activeProjectId,
                jobType = JobType.VIDEO,
                statusMessage = "Generating AI script and scenes..."
            ).getOrNull().orEmpty()

            try {
                // Step 1: Storyboard & Script via Gemini 3.8 Flash
                _draftState.update {
                    it.copy(
                        progressPercent = 0.14f,
                        activeStepMessage = "Gemini AI is writing script & dividing scenes..."
                    )
                }
                val storyboard = aiProvider.generateStoryboard(
                    prompt = current.prompt.ifBlank { "Animate this visual scene smoothly with cinematic atmosphere." },
                    rawScript = current.script.takeIf { it.isNotBlank() },
                    mode = current.mode,
                    style = current.visualStyle.label,
                    language = current.language,
                    sceneCount = current.sceneCount,
                    totalDurationSeconds = current.durationSeconds
                )

                val resolvedTitle = current.title.ifBlank { storyboard.title }.take(120)
                _draftState.update {
                    it.copy(
                        title = resolvedTitle,
                        script = storyboard.fullScript,
                        scenes = storyboard.scenes,
                        progressPercent = 0.28f,
                        activeStepMessage = "Generating scene visuals with Gemini Nano Banana..."
                    )
                }

                repository.updateGenerationJob(
                    jobId = jobId,
                    status = JobStatus.RUNNING,
                    progressPercent = 30,
                    statusMessage = "Generating AI scene images..."
                )

                // Step 2: Generate or process scene images
                val updatedScenes = mutableListOf<SceneItem>()
                val uploadedSourceBmp = if (current.mode == ProjectMode.ANIMATE_PHOTO && !current.uploadedPhotoPath.isNullOrBlank()) {
                    mediaStore.loadBitmapFromPath(current.uploadedPhotoPath, 1024)
                } else null

                try {
                    storyboard.scenes.forEachIndexed { idx, scene ->
                        val stepBase = 0.30f + (idx.toFloat() / storyboard.scenes.size.coerceAtLeast(1)) * 0.36f
                        _draftState.update {
                            it.copy(
                                progressPercent = stepBase,
                                activeStepMessage = "Creating visual for Scene ${idx + 1} of ${storyboard.scenes.size}..."
                            )
                        }

                        val imagePath = if (uploadedSourceBmp != null && idx == 0) {
                            current.uploadedPhotoPath!!
                        } else if (uploadedSourceBmp != null) {
                            val editedBmp = aiProvider.editImage(
                                sourceBitmap = uploadedSourceBmp,
                                instruction = "Create a subtle progressive motion/camera-angle frame from this photo: ${scene.visualPrompt}",
                                style = current.visualStyle.label,
                                aspectRatio = current.aspectRatio
                            )
                            val saved = mediaStore.saveBitmapAsJpeg(editedBmp, prefix = "scene_${idx + 1}")
                            editedBmp.recycle()
                            saved
                        } else {
                            val bmp = aiProvider.generateImage(
                                prompt = scene.visualPrompt,
                                style = current.visualStyle.label,
                                aspectRatio = current.aspectRatio
                            )
                            val saved = mediaStore.saveBitmapAsJpeg(bmp, prefix = "scene_${idx + 1}")
                            bmp.recycle()
                            saved
                        }

                        val withImg = scene.copy(localImagePath = imagePath)
                        updatedScenes.add(withImg)
                        _draftState.update { state ->
                            val merged = updatedScenes + storyboard.scenes.drop(updatedScenes.size)
                            state.copy(scenes = merged)
                        }
                    }
                } finally {
                    uploadedSourceBmp?.recycle()
                }

                val midProject = VideoProject(
                    id = activeProjectId,
                    userId = currentUserId,
                    title = resolvedTitle,
                    mode = current.mode.name,
                    prompt = current.prompt,
                    script = storyboard.fullScript,
                    aspectRatio = current.aspectRatio.name,
                    durationSeconds = updatedScenes.sumOf { it.durationSeconds }.toLong(),
                    visualStyle = current.visualStyle.label,
                    language = current.language,
                    narrationEnabled = current.narrationEnabled,
                    subtitlesEnabled = current.subtitlesEnabled,
                    sceneCount = updatedScenes.size.toLong(),
                    status = if (autoAssembleMp4) ProjectStatus.RENDERING_VIDEO.name else ProjectStatus.DRAFT.name,
                    scenesJson = SceneItem.listToJson(updatedScenes),
                    localThumbnailPath = updatedScenes.firstOrNull()?.localImagePath,
                    videoWidth = current.aspectRatio.width.toLong(),
                    videoHeight = current.aspectRatio.height.toLong()
                )
                repository.updateProject(midProject)

                if (autoAssembleMp4) {
                    assembleScenesInternal(
                        projectId = activeProjectId,
                        title = resolvedTitle,
                        mode = current.mode,
                        prompt = current.prompt,
                        script = storyboard.fullScript,
                        aspectRatio = current.aspectRatio,
                        visualStyle = current.visualStyle.label,
                        language = current.language,
                        narrationEnabled = current.narrationEnabled,
                        subtitlesEnabled = current.subtitlesEnabled,
                        scenes = updatedScenes,
                        jobId = jobId
                    )
                } else {
                    repository.updateGenerationJob(
                        jobId = jobId,
                        status = JobStatus.COMPLETED,
                        progressPercent = 100,
                        statusMessage = "Storyboard & visuals ready to review."
                    )
                    _draftState.update {
                        it.copy(
                            isBusy = false,
                            progressPercent = 1f,
                            activeStepMessage = null,
                            infoMessage = "Storyboard ready! Review or regenerate any scene, then tap Render MP4."
                        )
                    }
                    refreshStorageUsage()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "AI generation failed."
                Log.e(TAG, "Full video workflow failed", e)
                repository.updateGenerationJob(
                    jobId = jobId,
                    status = JobStatus.FAILED,
                    progressPercent = 0,
                    statusMessage = "Generation failed",
                    errorMessage = msg
                )
                repository.updateProject(
                    initialProject.copy(
                        id = activeProjectId,
                        title = _draftState.value.title.ifBlank { effectiveTitle },
                        status = ProjectStatus.FAILED.name,
                        errorMessage = msg,
                        scenesJson = SceneItem.listToJson(_draftState.value.scenes)
                    )
                )
                _draftState.update {
                    it.copy(
                        isBusy = false,
                        progressPercent = 0f,
                        activeStepMessage = null,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    fun assembleCurrentScenesToMp4() {
        val current = _draftState.value
        if (current.isBusy || current.scenes.isEmpty()) return

        viewModelScope.launch {
            _draftState.update {
                it.copy(
                    isBusy = true,
                    progressPercent = 0.05f,
                    activeStepMessage = "Preparing MP4 assembly...",
                    errorMessage = null,
                    infoMessage = null
                )
            }
            val projectId = current.editingProjectId ?: repository.createProject(
                VideoProject(
                    userId = currentUserId,
                    title = current.title.ifBlank { "Studio Video" },
                    mode = current.mode.name,
                    prompt = current.prompt,
                    script = current.script,
                    aspectRatio = current.aspectRatio.name,
                    durationSeconds = current.scenes.sumOf { it.durationSeconds }.toLong(),
                    visualStyle = current.visualStyle.label,
                    language = current.language,
                    narrationEnabled = current.narrationEnabled,
                    subtitlesEnabled = current.subtitlesEnabled,
                    sceneCount = current.scenes.size.toLong(),
                    status = ProjectStatus.RENDERING_VIDEO.name,
                    scenesJson = SceneItem.listToJson(current.scenes)
                )
            ).getOrElse { err ->
                _draftState.update { it.copy(isBusy = false, errorMessage = err.message) }
                return@launch
            }
            _draftState.update { it.copy(editingProjectId = projectId) }

            val jobId = repository.createGenerationJob(
                projectId = projectId,
                jobType = JobType.VIDEO,
                statusMessage = "Encoding MP4 video..."
            ).getOrNull().orEmpty()

            try {
                assembleScenesInternal(
                    projectId = projectId,
                    title = current.title.ifBlank { "Studio Video" },
                    mode = current.mode,
                    prompt = current.prompt,
                    script = current.script,
                    aspectRatio = current.aspectRatio,
                    visualStyle = current.visualStyle.label,
                    language = current.language,
                    narrationEnabled = current.narrationEnabled,
                    subtitlesEnabled = current.subtitlesEnabled,
                    scenes = current.scenes,
                    jobId = jobId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Failed to render MP4 video."
                repository.updateGenerationJob(
                    jobId = jobId,
                    status = JobStatus.FAILED,
                    progressPercent = 0,
                    statusMessage = "MP4 assembly failed",
                    errorMessage = msg
                )
                _draftState.update {
                    it.copy(
                        isBusy = false,
                        progressPercent = 0f,
                        activeStepMessage = null,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    private suspend fun assembleScenesInternal(
        projectId: String,
        title: String,
        mode: ProjectMode,
        prompt: String,
        script: String,
        aspectRatio: AspectRatioOption,
        visualStyle: String,
        language: String,
        narrationEnabled: Boolean,
        subtitlesEnabled: Boolean,
        scenes: List<SceneItem>,
        jobId: String
    ) {
        repository.updateGenerationJob(
            jobId = jobId,
            status = JobStatus.RUNNING,
            progressPercent = 70,
            statusMessage = "Encoding H.264 MP4 video..."
        )
        val result = videoAssembler.assembleMp4(
            projectId = projectId,
            scenes = scenes,
            aspectRatio = aspectRatio,
            narrationEnabled = narrationEnabled,
            subtitlesEnabled = subtitlesEnabled,
            language = language,
            onProgress = { ratio, msg ->
                _draftState.update {
                    it.copy(
                        progressPercent = 0.65f + ratio * 0.35f,
                        activeStepMessage = msg
                    )
                }
            }
        )

        val completedProject = VideoProject(
            id = projectId,
            userId = currentUserId,
            title = title,
            mode = mode.name,
            prompt = prompt,
            script = script,
            aspectRatio = aspectRatio.name,
            durationSeconds = scenes.sumOf { it.durationSeconds }.toLong(),
            visualStyle = visualStyle,
            language = language,
            narrationEnabled = narrationEnabled,
            subtitlesEnabled = subtitlesEnabled,
            sceneCount = scenes.size.toLong(),
            status = ProjectStatus.COMPLETED.name,
            errorMessage = null,
            scenesJson = SceneItem.listToJson(scenes),
            localVideoPath = result.videoFile.absolutePath,
            localThumbnailPath = scenes.firstOrNull()?.localImagePath,
            videoWidth = result.width.toLong(),
            videoHeight = result.height.toLong(),
            fileSizeBytes = result.fileSizeBytes
        )
        repository.updateProject(completedProject)
        repository.updateGenerationJob(
            jobId = jobId,
            status = JobStatus.COMPLETED,
            progressPercent = 100,
            statusMessage = "MP4 video created"
        )
        refreshStorageUsage()
        val audioNote = if (narrationEnabled && !result.hasAudioTrack) {
            " (Device TTS voice unavailable, exported clean silent MP4 with subtitles)"
        } else ""
        _draftState.update {
            it.copy(
                isBusy = false,
                progressPercent = 1f,
                activeStepMessage = null,
                renderedVideoPath = result.videoFile.absolutePath,
                hasAudioTrack = result.hasAudioTrack,
                infoMessage = "MP4 video ready!$audioNote"
            )
        }
    }

    fun regenerateScene(sceneIndex: Int) {
        val current = _draftState.value
        val targetScene = current.scenes.getOrNull(sceneIndex) ?: return
        if (current.isBusy || current.regeneratingSceneIndex != null) return

        viewModelScope.launch {
            _draftState.update { it.copy(regeneratingSceneIndex = sceneIndex, errorMessage = null) }
            try {
                val quotaCheck = repository.consumeDailyQuota(currentUserId)
                if (quotaCheck.isFailure) {
                    _draftState.update {
                        it.copy(
                            regeneratingSceneIndex = null,
                            errorMessage = quotaCheck.exceptionOrNull()?.message
                        )
                    }
                    return@launch
                }

                val updatedSceneMeta = aiProvider.regenerateSingleScene(
                    storyContext = current.script.ifBlank { current.prompt },
                    existingScene = targetScene,
                    style = current.visualStyle.label,
                    language = current.language
                )
                val newBmp = aiProvider.generateImage(
                    prompt = updatedSceneMeta.visualPrompt,
                    style = current.visualStyle.label,
                    aspectRatio = current.aspectRatio
                )
                val newPath = mediaStore.saveBitmapAsJpeg(newBmp, prefix = "scene_${sceneIndex + 1}")
                newBmp.recycle()

                mediaStore.deleteFileIfExists(targetScene.localImagePath)
                val finalScene = updatedSceneMeta.copy(localImagePath = newPath)
                val updatedList = current.scenes.toMutableList().apply {
                    set(sceneIndex, finalScene)
                }

                _draftState.update {
                    it.copy(
                        scenes = updatedList,
                        regeneratingSceneIndex = null,
                        infoMessage = "Scene ${sceneIndex + 1} regenerated! Tap Re-render MP4 to update the video."
                    )
                }

                current.editingProjectId?.let { projId ->
                    repository.updateProject(
                        VideoProject(
                            id = projId,
                            userId = currentUserId,
                            title = current.title.ifBlank { "AI Video" },
                            mode = current.mode.name,
                            prompt = current.prompt,
                            script = updatedList.joinToString(" ") { it.narrationText },
                            aspectRatio = current.aspectRatio.name,
                            durationSeconds = updatedList.sumOf { it.durationSeconds }.toLong(),
                            visualStyle = current.visualStyle.label,
                            language = current.language,
                            narrationEnabled = current.narrationEnabled,
                            subtitlesEnabled = current.subtitlesEnabled,
                            sceneCount = updatedList.size.toLong(),
                            status = ProjectStatus.DRAFT.name,
                            scenesJson = SceneItem.listToJson(updatedList),
                            localThumbnailPath = updatedList.firstOrNull()?.localImagePath,
                            videoWidth = current.aspectRatio.width.toLong(),
                            videoHeight = current.aspectRatio.height.toLong()
                        )
                    )
                }
                refreshStorageUsage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _draftState.update {
                    it.copy(
                        regeneratingSceneIndex = null,
                        errorMessage = e.localizedMessage ?: "Failed to regenerate scene."
                    )
                }
            }
        }
    }

    // --- FEATURE 2: AI IMAGE GENERATOR ---

    fun updateImagePrompt(prompt: String) {
        _imageGenState.update { it.copy(prompt = prompt.take(1500), errorMessage = null) }
    }

    fun updateImageStyle(style: ImageStyleOption) {
        _imageGenState.update { it.copy(style = style) }
    }

    fun updateImageAspectRatio(ratio: AspectRatioOption) {
        _imageGenState.update { it.copy(aspectRatio = ratio) }
    }

    fun setImageEditorSource(uri: Uri) {
        viewModelScope.launch {
            val bmp = mediaStore.loadBitmapFromUri(uri, maxDimension = 1024)
            if (bmp == null) {
                _imageGenState.update { it.copy(errorMessage = "Could not load selected image.") }
                return@launch
            }
            val path = mediaStore.saveBitmapAsJpeg(bmp, prefix = "edit_src")
            bmp.recycle()
            _imageGenState.update {
                it.copy(
                    sourcePhotoPath = path,
                    errorMessage = null,
                    infoMessage = "Source photo attached. Enter edit instructions."
                )
            }
        }
    }

    fun clearImageEditorSource() {
        _imageGenState.update { it.copy(sourcePhotoPath = null) }
    }

    fun generateOrEditStandaloneImage() {
        val current = _imageGenState.value
        if (current.isGenerating) return
        if (current.prompt.isBlank()) {
            _imageGenState.update { it.copy(errorMessage = "Enter an image prompt or edit instruction.") }
            return
        }

        viewModelScope.launch {
            _imageGenState.update {
                it.copy(isGenerating = true, errorMessage = null, infoMessage = null)
            }

            val quotaCheck = repository.consumeDailyQuota(currentUserId)
            if (quotaCheck.isFailure) {
                _imageGenState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = quotaCheck.exceptionOrNull()?.message
                    )
                }
                return@launch
            }

            try {
                val srcPath = current.sourcePhotoPath
                val isEditing = !srcPath.isNullOrBlank()
                val bitmap = if (isEditing) {
                    val srcBmp = mediaStore.loadBitmapFromPath(srcPath, 1024)
                        ?: throw IllegalStateException("Source image file is missing.")
                    try {
                        aiProvider.editImage(
                            sourceBitmap = srcBmp,
                            instruction = current.prompt,
                            style = current.style.label,
                            aspectRatio = current.aspectRatio
                        )
                    } finally {
                        srcBmp.recycle()
                    }
                } else {
                    aiProvider.generateImage(
                        prompt = current.prompt,
                        style = current.style.label,
                        aspectRatio = current.aspectRatio
                    )
                }

                val savedPath = mediaStore.saveBitmapAsJpeg(bitmap, prefix = "ai_img")
                bitmap.recycle()

                repository.saveGeneratedImage(
                    GeneratedImageItem(
                        userId = currentUserId,
                        prompt = current.prompt,
                        style = current.style.label,
                        aspectRatio = current.aspectRatio.name,
                        editedFromSource = isEditing,
                        localImagePath = savedPath
                    )
                )
                refreshStorageUsage()
                _imageGenState.update {
                    it.copy(
                        isGenerating = false,
                        latestImagePath = savedPath,
                        infoMessage = if (isEditing) "Edited image saved!" else "AI image generated and saved!"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageGenState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Image generation failed."
                    )
                }
            }
        }
    }

    fun sendImageToVideoProject(imageItem: GeneratedImageItem) {
        _draftState.update {
            VideoDraftUiState(
                title = imageItem.prompt.take(40).ifBlank { "Animated Photo" },
                mode = ProjectMode.ANIMATE_PHOTO,
                prompt = imageItem.prompt,
                aspectRatio = AspectRatioOption.fromRaw(imageItem.aspectRatio),
                visualStyle = ImageStyleOption.fromLabel(imageItem.style),
                uploadedPhotoPath = imageItem.localImagePath,
                infoMessage = "Image loaded into Video Studio!"
            )
        }
        _selectedTab.value = StudioTab.CREATE
    }

    fun exportImageToDeviceGallery(localPath: String, name: String) {
        viewModelScope.launch {
            mediaStore.exportImageToGallery(localPath, name)
                .onSuccess { msg -> _globalBannerMessage.value = msg }
                .onFailure { err -> _globalBannerMessage.value = err.message ?: "Export failed." }
        }
    }

    fun deleteGeneratedImage(item: GeneratedImageItem) {
        viewModelScope.launch {
            repository.deleteGeneratedImage(item.id)
                .onSuccess {
                    mediaStore.deleteFileIfExists(item.localImagePath)
                    refreshStorageUsage()
                    _globalBannerMessage.value = "Image deleted."
                }
                .onFailure { err ->
                    _globalBannerMessage.value = err.message ?: "Failed to delete image."
                }
        }
    }

    // --- FEATURE 3 & 5: PROJECTS & GALLERY ---

    fun openProjectInCreateStudio(project: VideoProject) {
        val scenes = project.parsedScenes()
        _draftState.value = VideoDraftUiState(
            editingProjectId = project.id,
            title = project.title,
            mode = project.modeEnum(),
            prompt = project.prompt,
            script = project.script,
            aspectRatio = project.aspectRatioEnum(),
            durationSeconds = project.durationSeconds.toInt().coerceIn(6, 90),
            visualStyle = ImageStyleOption.fromLabel(project.visualStyle),
            language = project.language.ifBlank { "English" },
            narrationEnabled = project.narrationEnabled,
            subtitlesEnabled = project.subtitlesEnabled,
            sceneCount = project.sceneCount.toInt().coerceIn(1, 6),
            scenes = scenes,
            renderedVideoPath = project.localVideoPath,
            infoMessage = "Loaded project '${project.title}'."
        )
        _selectedTab.value = StudioTab.CREATE
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameProject(projectId, newTitle)
                .onSuccess { _globalBannerMessage.value = "Project renamed." }
                .onFailure { err -> _globalBannerMessage.value = err.message ?: "Rename failed." }
        }
    }

    fun deleteProject(project: VideoProject) {
        viewModelScope.launch {
            repository.deleteProject(project.id)
                .onSuccess {
                    mediaStore.deleteFileIfExists(project.localVideoPath)
                    project.parsedScenes().forEach { mediaStore.deleteFileIfExists(it.localImagePath) }
                    if (_draftState.value.editingProjectId == project.id) {
                        _draftState.value = VideoDraftUiState()
                    }
                    refreshStorageUsage()
                    _globalBannerMessage.value = "Project deleted."
                }
                .onFailure { err -> _globalBannerMessage.value = err.message ?: "Delete failed." }
        }
    }

    fun exportVideoToDeviceGallery(localPath: String, title: String) {
        viewModelScope.launch {
            mediaStore.exportVideoToGallery(localPath, title)
                .onSuccess { msg -> _globalBannerMessage.value = msg }
                .onFailure { err -> _globalBannerMessage.value = err.message ?: "MP4 export failed." }
        }
    }

    // --- FEATURE 4: ACCOUNT, PREFERENCES & FEEDBACK ---

    fun updateAccountPreferences(displayName: String, style: ImageStyleOption, language: String) {
        viewModelScope.launch {
            repository.updateProfilePreferences(currentUserId, displayName, style.label, language)
                .onSuccess { _globalBannerMessage.value = "Profile preferences saved." }
                .onFailure { err -> _globalBannerMessage.value = err.message ?: "Update failed." }
        }
    }

    fun submitAppFeedback(rating: Int, opinion: String) {
        viewModelScope.launch {
            repository.submitFeedback(
                rating = rating,
                opinion = opinion,
                userEmail = currentUser.email.orEmpty()
            ).onSuccess {
                _globalBannerMessage.value = "Thank you for rating Victor Video!"
            }.onFailure { err ->
                _globalBannerMessage.value = err.message ?: "Failed to submit feedback."
            }
        }
    }

    fun clearTemporaryCache() {
        mediaStore.clearTempFiles()
        refreshStorageUsage()
        _globalBannerMessage.value = "Temporary render cache cleared."
    }

    companion object {
        fun provideFactory(
            currentUser: FirebaseUser,
            databaseId: String,
            mediaStore: LocalMediaStore,
            narrationSynthesizer: NarrationSynthesizer
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = StudioRepository(db)
                val aiProvider = FirebaseAiStudioProvider()
                val assembler = Mp4VideoAssembler(mediaStore, narrationSynthesizer)
                StudioViewModel(
                    currentUserId = currentUser.uid,
                    currentUser = currentUser,
                    repository = repo,
                    aiProvider = aiProvider,
                    mediaStore = mediaStore,
                    videoAssembler = assembler
                )
            }
        }
    }
}
