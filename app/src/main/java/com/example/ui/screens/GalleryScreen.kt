package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.StatusError
import com.example.ui.viewmodel.UiState
import java.io.File

@Composable
fun GalleryScreen(
    projectsState: UiState<List<VideoProject>>,
    remainingQuota: Int,
    quotaLimit: Int,
    onPlayVideo: (VideoProject) -> Unit,
    onDownloadVideo: (String, String) -> Unit,
    onShareVideo: (String, String) -> Unit,
    onDeleteVideo: (VideoProject) -> Unit,
    onGoToCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    title = "Video Gallery",
                    subtitle = "Completed MP4 creations ready to play & export",
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
                    val completedVideos = projectsState.data.filter { project ->
                        project.statusEnum() == ProjectStatus.COMPLETED &&
                            !project.localVideoPath.isNullOrBlank() &&
                            File(project.localVideoPath).exists()
                    }
                    if (completedVideos.isEmpty()) {
                        item {
                            StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_gallery),
                                    contentDescription = "Empty video gallery",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No completed MP4 videos yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Render your first video in Create to preview, share, and download MP4 files here.",
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
                                        .testTag("empty_gallery_create_button")
                                ) {
                                    Text("Go to Video Studio")
                                }
                            }
                        }
                    } else {
                        items(completedVideos, key = { it.id }) { video ->
                            GalleryVideoCard(
                                video = video,
                                onPlay = { onPlayVideo(video) },
                                onDownload = {
                                    video.localVideoPath?.let { path ->
                                        onDownloadVideo(path, video.title)
                                    }
                                },
                                onShare = {
                                    video.localVideoPath?.let { path ->
                                        onShareVideo(path, video.title)
                                    }
                                },
                                onDelete = { onDeleteVideo(video) },
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
private fun GalleryVideoCard(
    video: VideoProject,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(NavyElevated)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            if (!video.localThumbnailPath.isNullOrBlank() && File(video.localThumbnailPath).exists()) {
                AsyncImage(
                    model = File(video.localThumbnailPath),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
            Surface(
                color = ElectricBlue.copy(alpha = 0.92f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${video.title}",
                        tint = InkPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Text(
                    text = "${video.durationSeconds}s • ${video.aspectRatioEnum().label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${video.videoWidth}x${video.videoHeight} MP4 • ${formatFileSize(video.fileSizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("gallery_play_${video.id}")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play")
            }
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download MP4")
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share MP4")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete video", tint = StatusError)
            }
        }
    }
}
