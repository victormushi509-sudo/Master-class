package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AspectRatioOption
import com.example.data.model.ImageStyleOption
import com.example.data.model.ProjectMode
import com.example.data.model.SceneItem
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioSelectableChip
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricBlueContainer
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.viewmodel.VideoDraftUiState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateVideoScreen(
    draft: VideoDraftUiState,
    remainingQuota: Int,
    quotaLimit: Int,
    onModeSelected: (ProjectMode) -> Unit,
    onTitleChanged: (String) -> Unit,
    onPromptChanged: (String) -> Unit,
    onScriptChanged: (String) -> Unit,
    onAspectRatioChanged: (AspectRatioOption) -> Unit,
    onDurationChanged: (Int) -> Unit,
    onStyleChanged: (ImageStyleOption) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onNarrationChanged: (Boolean) -> Unit,
    onSubtitlesChanged: (Boolean) -> Unit,
    onSceneCountChanged: (Int) -> Unit,
    onPhotoPicked: (android.net.Uri) -> Unit,
    onClearPhoto: () -> Unit,
    onGenerateFullVideo: (Boolean) -> Unit,
    onAssembleMp4: () -> Unit,
    onRegenerateScene: (Int) -> Unit,
    onNewDraft: () -> Unit,
    onPlayRenderedVideo: (String, String, Int, Int) -> Unit,
    onExportVideo: (String, String) -> Unit,
    onShareVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onPhotoPicked) }
    )

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricBlue,
        unfocusedBorderColor = NavyBorder,
        focusedTextColor = InkPrimary,
        unfocusedTextColor = InkPrimary,
        focusedLabelColor = ElectricBlueBright,
        unfocusedLabelColor = InkSecondary,
        cursorColor = ElectricBlueBright
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudioTopHeader(
                    title = "Create AI Video",
                    subtitle = "Script, AI visuals, voice & MP4 render",
                    remainingQuota = remainingQuota,
                    quotaLimit = quotaLimit
                )
            }

            // Mode Selection Card
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Creation Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                        if (draft.editingProjectId != null || draft.scenes.isNotEmpty()) {
                            TextButton(
                                onClick = onNewDraft,
                                modifier = Modifier.testTag("new_video_draft_button")
                            ) {
                                Text("New Draft", color = ElectricBlueBright)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProjectMode.entries.forEach { mode ->
                            StudioSelectableChip(
                                label = mode.label,
                                selected = draft.mode == mode,
                                onClick = { onModeSelected(mode) },
                                modifier = Modifier.testTag("mode_chip_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = onTitleChanged,
                        label = { Text("Project Title (optional)") },
                        placeholder = { Text("e.g., Sunset Safari Reel") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when (draft.mode) {
                        ProjectMode.PROMPT -> {
                            OutlinedTextField(
                                value = draft.prompt,
                                onValueChange = onPromptChanged,
                                label = { Text("Describe Your Video Idea") },
                                placeholder = {
                                    Text("e.g., A breathtaking journey across Mount Kilimanjaro at sunrise, narrated warmly")
                                },
                                minLines = 3,
                                maxLines = 6,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("video_prompt_input")
                            )
                        }
                        ProjectMode.SCRIPT -> {
                            OutlinedTextField(
                                value = draft.script,
                                onValueChange = onScriptChanged,
                                label = { Text("Complete Narration Script") },
                                placeholder = {
                                    Text("Paste your full script. AI will split it into scenes, visual prompts, and subtitles.")
                                },
                                minLines = 4,
                                maxLines = 8,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("video_script_input")
                            )
                        }
                        ProjectMode.ANIMATE_PHOTO -> {
                            Surface(
                                color = ElectricBlueContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = ElectricBlueBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Honest Provider Note: Cloud video diffusion (gemini-omni-1.2-flash / Interactions API) requires a dedicated server endpoint not provided in Android Firebase AI Logic. Animate Photo generates real AI keyframe variations via Nano Banana 2.1 and renders genuine smooth H.264 MP4 motion on-device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (!draft.uploadedPhotoPath.isNullOrBlank() && File(draft.uploadedPhotoPath).exists()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(NavyElevated)
                                ) {
                                    AsyncImage(
                                        model = File(draft.uploadedPhotoPath),
                                        contentDescription = "Uploaded source photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = onClearPhoto,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove photo",
                                            tint = InkPrimary
                                        )
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("upload_photo_video_button")
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload Photo to Animate (16:9 or 9:16)")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = draft.prompt,
                                onValueChange = onPromptChanged,
                                label = { Text("Motion & Story Direction (optional)") },
                                placeholder = { Text("e.g., Slow cinematic camera push-in as golden light shifts") },
                                minLines = 2,
                                maxLines = 4,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("animate_photo_prompt_input")
                            )
                        }
                    }
                }
            }

            // Video Settings Card
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "2. Video Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val availableRatios = if (draft.mode == ProjectMode.ANIMATE_PHOTO) {
                            listOf(AspectRatioOption.RATIO_16_9, AspectRatioOption.RATIO_9_16)
                        } else {
                            AspectRatioOption.entries
                        }
                        availableRatios.forEach { ratio ->
                            StudioSelectableChip(
                                label = ratio.label,
                                selected = draft.aspectRatio == ratio,
                                onClick = { onAspectRatioChanged(ratio) },
                                modifier = Modifier.testTag("aspect_ratio_${ratio.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Target Duration & Scenes", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 24, 30).forEach { secs ->
                            StudioSelectableChip(
                                label = "${secs}s",
                                selected = draft.durationSeconds == secs,
                                onClick = { onDurationChanged(secs) },
                                modifier = Modifier.testTag("duration_${secs}s")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (2..5).forEach { count ->
                            StudioSelectableChip(
                                label = "$count Scenes",
                                selected = draft.sceneCount == count,
                                onClick = { onSceneCountChanged(count) },
                                modifier = Modifier.testTag("scene_count_$count")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Visual Style", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageStyleOption.entries.forEach { style ->
                            StudioSelectableChip(
                                label = style.label,
                                selected = draft.visualStyle == style,
                                onClick = { onStyleChanged(style) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Language", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("English", "Swahili", "Spanish", "French", "Arabic", "Portuguese").forEach { lang ->
                            StudioSelectableChip(
                                label = lang,
                                selected = draft.language == lang,
                                onClick = { onLanguageChanged(lang) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Voice Narration", style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text("Synthesize spoken voice track", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                        Switch(
                            checked = draft.narrationEnabled,
                            onCheckedChange = onNarrationChanged,
                            colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue),
                            modifier = Modifier.testTag("narration_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Burned-in Subtitles", style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text("Render crisp captions into MP4", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                        Switch(
                            checked = draft.subtitlesEnabled,
                            onCheckedChange = onSubtitlesChanged,
                            colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue),
                            modifier = Modifier.testTag("subtitles_switch")
                        )
                    }
                }
            }

            // Generate Action Buttons & Feedback
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (!draft.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = StatusErrorContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = draft.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (!draft.infoMessage.isNullOrBlank()) {
                        Surface(
                            color = StatusSuccessContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = draft.infoMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    AnimatedVisibility(visible = draft.isBusy) {
                        StudioCard(modifier = Modifier.padding(bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ElectricBlueBright,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = draft.activeStepMessage ?: "Generating...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { draft.progressPercent.coerceIn(0f, 1f) },
                                color = ElectricBlue,
                                trackColor = NavyElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onGenerateFullVideo(true) },
                            enabled = !draft.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("create_video_primary_button")
                        ) {
                            Icon(Icons.Filled.MovieCreation, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create MP4 Video", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onGenerateFullVideo(false) },
                            enabled = !draft.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("generate_storyboard_only_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scenes")
                        }
                    }
                }
            }

            // Rendered Video Preview Card
            if (!draft.renderedVideoPath.isNullOrBlank() && File(draft.renderedVideoPath).exists()) {
                item {
                    StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Completed MP4 Video",
                            style = MaterialTheme.typography.titleMedium,
                            color = StatusSuccess
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = draft.title.ifBlank { "Victor AI Video" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onPlayRenderedVideo(
                                        draft.renderedVideoPath,
                                        draft.title.ifBlank { "Victor AI Video" },
                                        draft.aspectRatio.width,
                                        draft.aspectRatio.height
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("play_rendered_mp4_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview MP4")
                            }
                            OutlinedButton(
                                onClick = {
                                    onExportVideo(draft.renderedVideoPath, draft.title.ifBlank { "victor_video" })
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("download_rendered_mp4_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Save MP4")
                            }
                            OutlinedButton(
                                onClick = {
                                    onShareVideo(draft.renderedVideoPath, draft.title.ifBlank { "Victor AI Video" })
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_rendered_mp4_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share MP4")
                            }
                        }
                    }
                }
            }

            // Storyboard Scenes Section
            if (draft.scenes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storyboard Scenes (${draft.scenes.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = InkPrimary
                        )
                        Button(
                            onClick = onAssembleMp4,
                            enabled = !draft.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueContainer),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("rerender_mp4_button")
                        ) {
                            Text("Render MP4", color = ElectricBlueBright)
                        }
                    }
                }

                itemsIndexed(draft.scenes) { index, scene ->
                    SceneStoryboardCard(
                        scene = scene,
                        isRegenerating = draft.regeneratingSceneIndex == index,
                        enabled = !draft.isBusy && draft.regeneratingSceneIndex == null,
                        onRegenerate = { onRegenerateScene(index) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneStoryboardCard(
    scene: SceneItem,
    isRegenerating: Boolean,
    enabled: Boolean,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scene ${scene.sceneNumber}: ${scene.title}",
                style = MaterialTheme.typography.titleSmall,
                color = InkPrimary,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = NavyElevated,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${scene.durationSeconds}s • ${scene.motionDirection}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlueBright,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!scene.localImagePath.isNullOrBlank() && File(scene.localImagePath).exists()) {
            AsyncImage(
                model = File(scene.localImagePath),
                contentDescription = "Visual for scene ${scene.sceneNumber}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (scene.narrationText.isNotBlank()) {
            Text(
                text = "Narration: ${scene.narrationText}",
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (scene.subtitleText.isNotBlank()) {
            Text(
                text = "Subtitle: \"${scene.subtitleText}\"",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "Visual Prompt: ${scene.visualPrompt}",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onRegenerate,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("regenerate_scene_${scene.sceneNumber}_button")
        ) {
            if (isRegenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ElectricBlueBright,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regenerating Scene...")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regenerate Scene ${scene.sceneNumber}")
            }
        }
    }
}
