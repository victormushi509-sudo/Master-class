package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ProjectStatus
import com.example.data.model.VideoProject
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricBlueContainer
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.NavySurface
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.viewmodel.UiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProjectsScreen(
    projectsState: UiState<List<VideoProject>>,
    remainingQuota: Int,
    quotaLimit: Int,
    onOpenProject: (VideoProject) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (VideoProject) -> Unit,
    onPlayVideo: (VideoProject) -> Unit,
    onExportVideo: (String, String) -> Unit,
    onGoToCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renamingProject by remember { mutableStateOf<VideoProject?>(null) }
    var renameText by remember { mutableStateOf("") }

    renamingProject?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingProject = null },
            containerColor = NavySurface,
            title = { Text("Rename Project", color = InkPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(120) },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameProject(target.id, renameText)
                        }
                        renamingProject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingProject = null }) {
                    Text("Cancel", color = InkSecondary)
                }
            }
        )
    }

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
                    title = "My Projects",
                    subtitle = "Private cloud-synced video drafts & renders",
                    remainingQuota = remainingQuota,
                    quotaLimit = quotaLimit
                )
            }

            when (projectsState) {
                is UiState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricBlue)
                    }
                }
                is UiState.Error -> item {
                    StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(projectsState.message, color = StatusError)
                    }
                }
                is UiState.Success -> {
                    val projects = projectsState.data
                    if (projects.isEmpty()) {
                        item {
                            StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_gallery),
                                    contentDescription = "No projects yet",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Your personal project library is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Every video draft, script, and completed MP4 you create is saved privately to your account.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSecondary
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onGoToCreate,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("empty_projects_create_button")
                                ) {
                                    Text("Create Your First Video")
                                }
                            }
                        }
                    } else {
                        items(projects, key = { it.id }) { project ->
                            ProjectItemCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onRename = {
                                    renameText = project.title
                                    renamingProject = project
                                },
                                onDelete = { onDeleteProject(project) },
                                onPlay = { onPlayVideo(project) },
                                onDownload = {
                                    project.localVideoPath?.let { path ->
                                        onExportVideo(path, project.title)
                                    }
                                },
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
private fun ProjectItemCard(
    project: VideoProject,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = project.statusEnum()
    val statusColors = when (status) {
        ProjectStatus.COMPLETED -> StatusSuccessContainer to StatusSuccess
        ProjectStatus.FAILED -> StatusErrorContainer to StatusError
        ProjectStatus.DRAFT -> NavyElevated to ElectricBlueBright
        else -> StatusWarningContainer to StatusWarning
    }
    val dateLabel = remember(project.createdAt) {
        project.createdAt?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(it)
        } ?: "Just now"
    }
    val hasLocalVideo = !project.localVideoPath.isNullOrBlank() && File(project.localVideoPath).exists()

    StudioCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!project.localThumbnailPath.isNullOrBlank() && File(project.localThumbnailPath).exists()) {
                AsyncImage(
                    model = File(project.localThumbnailPath),
                    contentDescription = project.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(84.dp)
                        .height(84.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColors.first,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, statusColors.second.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColors.second,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${project.modeEnum().label} • ${project.aspectRatioEnum().label} • ${project.sceneCount} scenes • ${project.visualStyle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    maxLines = 2
                )
            }
        }

        if (!project.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = project.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = StatusError,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueContainer),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("open_project_${project.id}")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = ElectricBlueBright)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (status == ProjectStatus.COMPLETED) "Edit" else "Continue",
                    color = ElectricBlueBright
                )
            }

            if (hasLocalVideo) {
                OutlinedButton(
                    onClick = onPlay,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play video")
                }
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download MP4")
                }
            }

            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename project")
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete project", tint = StatusError)
            }
        }
    }
}
