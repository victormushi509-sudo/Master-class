package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppFeedback
import com.example.data.model.ImageStyleOption
import com.example.data.model.UserProfile
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioSelectableChip
import com.example.ui.components.StudioTopHeader
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricBlueContainer
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.UiState
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    firebaseUser: FirebaseUser,
    profileState: UiState<UserProfile?>,
    feedbackState: UiState<List<AppFeedback>>,
    storageUsedBytes: Long,
    onSavePreferences: (String, ImageStyleOption, String) -> Unit,
    onSubmitFeedback: (Int, String) -> Unit,
    onClearCache: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = (profileState as? UiState.Success)?.data
    val quotaUsed = profile?.dailyQuotaUsed?.toInt() ?: 0
    val quotaLimit = profile?.dailyQuotaLimit?.toInt() ?: 12
    val remainingQuota = (quotaLimit - quotaUsed).coerceAtLeast(0)

    var displayName by remember { mutableStateOf(firebaseUser.displayName ?: "Creator") }
    var preferredStyle by remember { mutableStateOf(ImageStyleOption.CINEMATIC) }
    var preferredLanguage by remember { mutableStateOf("English") }

    LaunchedEffect(profile) {
        if (profile != null) {
            if (profile.displayName.isNotBlank()) displayName = profile.displayName
            preferredStyle = ImageStyleOption.fromLabel(profile.preferredStyle)
            if (profile.preferredLanguage.isNotBlank()) preferredLanguage = profile.preferredLanguage
        }
    }

    var selectedRating by remember { mutableIntStateOf(5) }
    var opinionText by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricBlue,
        unfocusedBorderColor = NavyBorder,
        focusedTextColor = InkPrimary,
        unfocusedTextColor = InkPrimary,
        focusedLabelColor = ElectricBlueBright,
        unfocusedLabelColor = InkSecondary,
        cursorColor = ElectricBlueBright
    )

    val email1 = stringResource(R.string.contact_email_primary)
    val email2 = stringResource(R.string.contact_email_secondary)
    val phone = stringResource(R.string.contact_phone)

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
                    title = "My Account",
                    subtitle = "Profile, free quota, help & app feedback",
                    remainingQuota = remainingQuota,
                    quotaLimit = quotaLimit
                )
            }

            // Profile Card
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(ElectricBlueContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.firstOrNull()?.uppercase() ?: "V",
                                style = MaterialTheme.typography.headlineSmall,
                                color = ElectricBlueBright
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Text(
                                text = firebaseUser.email ?: "Signed in with Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = StatusError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", color = StatusError)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Free-First Daily AI Quota ($quotaUsed / $quotaLimit used today)",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (quotaUsed.toFloat() / quotaLimit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = ElectricBlue,
                        trackColor = NavyElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Media Storage: ${formatFileSize(storageUsedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Temp")
                        }
                    }
                }
            }

            // Preferences Card
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Studio Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(80) },
                        label = { Text("Creator Name") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default Visual Style", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageStyleOption.entries.forEach { style ->
                            StudioSelectableChip(
                                label = style.label,
                                selected = preferredStyle == style,
                                onClick = { preferredStyle = style }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default Language", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("English", "Swahili", "Spanish", "French", "Arabic", "Portuguese").forEach { lang ->
                            StudioSelectableChip(
                                label = lang,
                                selected = preferredLanguage == lang,
                                onClick = { preferredLanguage = lang }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onSavePreferences(displayName, preferredStyle, preferredLanguage) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_preferences_button")
                    ) {
                        Text("Save Preferences", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Contact & Help Section
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = null,
                            tint = ElectricBlueBright
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Contact & Help",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Need assistance with AI video creation, billing quotas, or account support? Reach out directly:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email1")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Victor Video Support Request")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("contact_email_primary_button")
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = ElectricBlueBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(email1, color = InkPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email2")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Victor Video Support Request")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("contact_email_secondary_button")
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = ElectricBlueBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(email2, color = InkPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("contact_phone_button")
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null, tint = ElectricBlueBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call / WhatsApp: $phone", color = InkPrimary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = NavyElevated,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, NavyBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Quick Help",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricBlueBright
                            )
                            Text(
                                text = "• Create Video: Enter a prompt, paste a script, or upload a photo. AI writes scenes, creates images via Nano Banana 2.1, and renders a real MP4.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                            Text(
                                text = "• Security & Auth: Uses Google Sign-In via Android Credential Manager and per-user Firestore rules so your projects stay strictly private.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                            Text(
                                text = "• Free Quota: 12 AI generations per day per account; resets daily at midnight.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }

            // Rating the App & Opinion Section
            item {
                StudioCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Rate Victor Video & Share Opinion",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tell us what you think so we can keep improving the platform.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = { selectedRating = star },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("rate_star_$star")
                            ) {
                                Icon(
                                    imageVector = if (star <= selectedRating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "Rate $star stars",
                                    tint = if (star <= selectedRating) StatusWarning else InkSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$selectedRating / 5",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = opinionText,
                        onValueChange = { opinionText = it.take(1500) },
                        label = { Text("Your Opinion / Suggestions") },
                        placeholder = { Text("Share what you love or what we should improve...") },
                        minLines = 3,
                        maxLines = 5,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_opinion_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (opinionText.isNotBlank()) {
                                onSubmitFeedback(selectedRating, opinionText)
                                opinionText = ""
                            }
                        },
                        enabled = opinionText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("submit_feedback_button")
                    ) {
                        Text("Submit Rating & Opinion", fontWeight = FontWeight.Bold)
                    }

                    val submitted = (feedbackState as? UiState.Success)?.data.orEmpty()
                    if (submitted.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Your Recent Reviews",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricBlueBright
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        submitted.take(3).forEach { fb ->
                            Surface(
                                color = NavyElevated,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${"★".repeat(fb.rating.toInt().coerceIn(1, 5))} (${fb.rating}/5)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusWarning
                                    )
                                    Text(
                                        text = fb.opinion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
