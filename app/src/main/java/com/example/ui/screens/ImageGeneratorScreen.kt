package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AspectRatioOption
import com.example.data.model.GeneratedImageItem
import com.example.data.model.ImageStyleOption
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioSelectableChip
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.viewmodel.ImageGenUiState
import com.example.ui.viewmodel.UiState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageGeneratorScreen(
    uiState: ImageGenUiState,
    savedImagesState: UiState<List<GeneratedImageItem>>,
    remainingQuota: Int,
    quotaLimit: Int,
    onPromptChanged: (String) -> Unit,
    onStyleSelected: (ImageStyleOption) -> Unit,
    onAspectRatioSelected: (AspectRatioOption) -> Unit,
    onSourcePhotoPicked: (Uri) -> Unit,
    onClearSourcePhoto: () -> Unit,
    onGenerateClick: () -> Unit,
    onExportImage: (String, String) -> Unit,
    onSendToVideoStudio: (GeneratedImageItem) -> Unit,
    onDeleteImage: (GeneratedImageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onSourcePhotoPicked) }
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
                    title = "AI Image Studio",
                    subtitle = "Create & edit with Gemini Nano Banana 2.1",
                    remainingQuota = remainingQuota,
                    quotaLimit = quotaLimit
                )
            }

            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = if (uiState.sourcePhotoPath != null) "Edit Uploaded Photo" else "Describe Your Image",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = uiState.prompt,
                        onValueChange = onPromptChanged,
                        label = {
                            Text(
                                if (uiState.sourcePhotoPath != null) "Edit Instruction"
                                else "Image Prompt"
                            )
                        },
                        placeholder = {
                            Text(
                                if (uiState.sourcePhotoPath != null) {
                                    "e.g., Transform into golden hour cyberpunk lighting"
                                } else {
                                    "e.g., Futuristic electric-blue electric train gliding across snowy Alps"
                                }
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("image_prompt_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!uiState.sourcePhotoPath.isNullOrBlank() && File(uiState.sourcePhotoPath).exists()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(NavyElevated)
                        ) {
                            AsyncImage(
                                model = File(uiState.sourcePhotoPath),
                                contentDescription = "Source image for editing",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = onClearSourcePhoto,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove source photo", tint = InkPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("attach_edit_image_button")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Optional: Upload Photo to Edit")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text("Style", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageStyleOption.entries.forEach { style ->
                            StudioSelectableChip(
                                label = style.label,
                                selected = uiState.style == style,
                                onClick = { onStyleSelected(style) },
                                modifier = Modifier.testTag("image_style_${style.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatioOption.entries.forEach { ratio ->
                            StudioSelectableChip(
                                label = ratio.label,
                                selected = uiState.aspectRatio == ratio,
                                onClick = { onAspectRatioSelected(ratio) }
                            )
                        }
                    }

                    if (!uiState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = StatusErrorContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (!uiState.infoMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = StatusSuccessContainer,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.infoMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onGenerateClick,
                        enabled = !uiState.isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("generate_ai_image_button")
                    ) {
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = InkPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Synthesizing Image (30-60s)...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.sourcePhotoPath != null) "Apply AI Edit" else "Generate AI Image",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (!uiState.latestImagePath.isNullOrBlank() && File(uiState.latestImagePath).exists()) {
                item {
                    StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Latest Creation",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricBlueBright
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AsyncImage(
                            model = File(uiState.latestImagePath),
                            contentDescription = "Latest generated AI image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onExportImage(uiState.latestImagePath, "victor_ai_image") },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("download_latest_image_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download to Gallery")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Saved Creations",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            when (savedImagesState) {
                is UiState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricBlue)
                    }
                }
                is UiState.Error -> item {
                    StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(savedImagesState.message, color = StatusError)
                    }
                }
                is UiState.Success -> {
                    val images = savedImagesState.data
                    if (images.isEmpty()) {
                        item {
                            StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_gallery),
                                    contentDescription = "No saved images yet",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No AI images yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "Pick a style above and generate photorealistic, cinematic, 3D, fantasy, or portrait artwork.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSecondary
                                )
                            }
                        }
                    } else {
                        items(images, key = { it.id }) { item ->
                            SavedImageCard(
                                item = item,
                                onDownload = { onExportImage(item.localImagePath, item.prompt.take(24)) },
                                onUseInVideo = { onSendToVideoStudio(item) },
                                onDelete = { onDeleteImage(item) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedImageCard(
    item: GeneratedImageItem,
    onDownload: () -> Unit,
    onUseInVideo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(modifier = modifier) {
        if (File(item.localImagePath).exists()) {
            AsyncImage(
                model = File(item.localImagePath),
                contentDescription = item.prompt,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = item.prompt,
            style = MaterialTheme.typography.titleSmall,
            color = InkPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${item.style} • ${AspectRatioOption.fromRaw(item.aspectRatio).label}" +
                if (item.editedFromSource) " • Edited" else "",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onUseInVideo,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use in Video")
            }
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Save image")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete image", tint = StatusError)
            }
        }
    }
}
