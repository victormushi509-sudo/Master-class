package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.media.LocalMediaStore
import com.example.data.media.NarrationSynthesizer
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.signOut
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreateVideoScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.ImageGeneratorScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricBlueContainer
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.NavyBase
import com.example.ui.theme.NavySurface
import com.example.ui.theme.VictorVideoTheme
import com.example.ui.viewmodel.StudioTab
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

private data class NavDestination(
    val tab: StudioTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val StudioDestinations = listOf(
    NavDestination(
        tab = StudioTab.CREATE,
        labelRes = R.string.nav_create,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_create"
    ),
    NavDestination(
        tab = StudioTab.IMAGES,
        labelRes = R.string.nav_images,
        selectedIcon = Icons.Filled.Image,
        unselectedIcon = Icons.Outlined.Image,
        testTag = "nav_images"
    ),
    NavDestination(
        tab = StudioTab.PROJECTS,
        labelRes = R.string.nav_projects,
        selectedIcon = Icons.Filled.FolderSpecial,
        unselectedIcon = Icons.Outlined.FolderSpecial,
        testTag = "nav_projects"
    ),
    NavDestination(
        tab = StudioTab.GALLERY,
        labelRes = R.string.nav_gallery,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_gallery"
    ),
    NavDestination(
        tab = StudioTab.ACCOUNT,
        labelRes = R.string.nav_account,
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
        testTag = "nav_account"
    )
)

private data class ActiveVideoPreview(
    val path: String,
    val title: String,
    val width: Int,
    val height: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            VictorVideoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NavyBase) {
                    VictorStudioRoot()
                }
            }
        }
    }
}

@Composable
fun VictorStudioRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStudioShell(
                firebaseUser = user,
                onSignOutRequested = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(
    firebaseUser: FirebaseUser,
    onSignOutRequested: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val databaseId = remember(appContext) {
        appContext.getString(R.string.firestore_database_id)
    }
    val mediaStore = remember(appContext) { LocalMediaStore(appContext) }
    val narrationSynthesizer = remember(appContext) { NarrationSynthesizer(appContext) }

    val viewModel: StudioViewModel = viewModel(
        key = firebaseUser.uid,
        factory = StudioViewModel.provideFactory(
            currentUser = firebaseUser,
            databaseId = databaseId,
            mediaStore = mediaStore,
            narrationSynthesizer = narrationSynthesizer
        )
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val draftState by viewModel.draftState.collectAsStateWithLifecycle()
    val imageGenState by viewModel.imageGenState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val savedImagesState by viewModel.generatedImagesState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val storageUsedBytes by viewModel.storageUsedBytes.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.globalBannerMessage.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val quotaLimit = profile?.dailyQuotaLimit?.toInt() ?: 12
    val remainingQuota = profile?.remainingQuota() ?: quotaLimit

    var activePreview by remember { mutableStateOf<ActiveVideoPreview?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    fun launchShareVideo(path: String, title: String) {
        val shareIntent = mediaStore.buildShareVideoIntent(path, title) ?: return
        context.startActivity(Intent.createChooser(shareIntent, "Share MP4 Video"))
    }

    activePreview?.let { preview ->
        VideoPlayerDialog(
            title = preview.title,
            videoPath = preview.path,
            aspectRatioWidth = preview.width,
            aspectRatioHeight = preview.height,
            onDownloadClick = { viewModel.exportVideoToDeviceGallery(preview.path, preview.title) },
            onShareClick = { launchShareVideo(preview.path, preview.title) },
            onDismiss = { activePreview = null }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = NavyBase,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = NavySurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        StudioDestinations.forEach { dest ->
                            val isSelected = selectedTab == dest.tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricBlueBright,
                                    selectedTextColor = ElectricBlueBright,
                                    indicatorColor = ElectricBlueContainer,
                                    unselectedIconColor = InkSecondary,
                                    unselectedTextColor = InkSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = NavySurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        StudioDestinations.forEach { dest ->
                            val isSelected = selectedTab == dest.tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricBlueBright,
                                    selectedTextColor = ElectricBlueBright,
                                    indicatorColor = ElectricBlueContainer,
                                    unselectedIconColor = InkSecondary,
                                    unselectedTextColor = InkSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(NavyBase)
                ) {
                    when (selectedTab) {
                        StudioTab.CREATE -> CreateVideoScreen(
                            draft = draftState,
                            remainingQuota = remainingQuota,
                            quotaLimit = quotaLimit,
                            onModeSelected = viewModel::updateDraftMode,
                            onTitleChanged = viewModel::updateDraftTitle,
                            onPromptChanged = viewModel::updateDraftPrompt,
                            onScriptChanged = viewModel::updateDraftScript,
                            onAspectRatioChanged = viewModel::updateDraftAspectRatio,
                            onDurationChanged = viewModel::updateDraftDuration,
                            onStyleChanged = viewModel::updateDraftStyle,
                            onLanguageChanged = viewModel::updateDraftLanguage,
                            onNarrationChanged = viewModel::updateDraftNarration,
                            onSubtitlesChanged = viewModel::updateDraftSubtitles,
                            onSceneCountChanged = viewModel::updateDraftSceneCount,
                            onPhotoPicked = viewModel::setDraftUploadedPhoto,
                            onClearPhoto = viewModel::clearDraftUploadedPhoto,
                            onGenerateFullVideo = viewModel::generateFullVideoWorkflow,
                            onAssembleMp4 = viewModel::assembleCurrentScenesToMp4,
                            onRegenerateScene = viewModel::regenerateScene,
                            onNewDraft = viewModel::resetNewVideoDraft,
                            onPlayRenderedVideo = { path, title, w, h ->
                                activePreview = ActiveVideoPreview(path, title, w, h)
                            },
                            onExportVideo = viewModel::exportVideoToDeviceGallery,
                            onShareVideo = ::launchShareVideo
                        )

                        StudioTab.IMAGES -> ImageGeneratorScreen(
                            uiState = imageGenState,
                            savedImagesState = savedImagesState,
                            remainingQuota = remainingQuota,
                            quotaLimit = quotaLimit,
                            onPromptChanged = viewModel::updateImagePrompt,
                            onStyleSelected = viewModel::updateImageStyle,
                            onAspectRatioSelected = viewModel::updateImageAspectRatio,
                            onSourcePhotoPicked = viewModel::setImageEditorSource,
                            onClearSourcePhoto = viewModel::clearImageEditorSource,
                            onGenerateClick = viewModel::generateOrEditStandaloneImage,
                            onExportImage = viewModel::exportImageToDeviceGallery,
                            onSendToVideoStudio = viewModel::sendImageToVideoProject,
                            onDeleteImage = viewModel::deleteGeneratedImage
                        )

                        StudioTab.PROJECTS -> ProjectsScreen(
                            projectsState = projectsState,
                            remainingQuota = remainingQuota,
                            quotaLimit = quotaLimit,
                            onOpenProject = viewModel::openProjectInCreateStudio,
                            onRenameProject = viewModel::renameProject,
                            onDeleteProject = viewModel::deleteProject,
                            onPlayVideo = { proj ->
                                proj.localVideoPath?.let { path ->
                                    activePreview = ActiveVideoPreview(
                                        path = path,
                                        title = proj.title,
                                        width = proj.videoWidth.toInt(),
                                        height = proj.videoHeight.toInt()
                                    )
                                }
                            },
                            onExportVideo = viewModel::exportVideoToDeviceGallery,
                            onGoToCreate = { viewModel.selectTab(StudioTab.CREATE) }
                        )

                        StudioTab.GALLERY -> GalleryScreen(
                            projectsState = projectsState,
                            remainingQuota = remainingQuota,
                            quotaLimit = quotaLimit,
                            onPlayVideo = { proj ->
                                proj.localVideoPath?.let { path ->
                                    activePreview = ActiveVideoPreview(
                                        path = path,
                                        title = proj.title,
                                        width = proj.videoWidth.toInt(),
                                        height = proj.videoHeight.toInt()
                                    )
                                }
                            },
                            onDownloadVideo = viewModel::exportVideoToDeviceGallery,
                            onShareVideo = ::launchShareVideo,
                            onDeleteVideo = viewModel::deleteProject,
                            onGoToCreate = { viewModel.selectTab(StudioTab.CREATE) }
                        )

                        StudioTab.ACCOUNT -> AccountScreen(
                            firebaseUser = firebaseUser,
                            profileState = profileState,
                            feedbackState = feedbackState,
                            storageUsedBytes = storageUsedBytes,
                            onSavePreferences = viewModel::updateAccountPreferences,
                            onSubmitFeedback = viewModel::submitAppFeedback,
                            onClearCache = viewModel::clearTemporaryCache,
                            onSignOut = onSignOutRequested
                        )
                    }
                }
            }
        }
    }
}
