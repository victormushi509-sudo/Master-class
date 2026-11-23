package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppFeedback
import com.example.data.model.GeneratedImageItem
import com.example.data.model.GenerationJob
import com.example.data.model.ImageStyleOption
import com.example.data.model.JobStatus
import com.example.data.model.JobType
import com.example.data.model.UserProfile
import com.example.data.model.VideoProject
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to continue.")
    }

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun ensureUserProfile(user: FirebaseUser): Result<UserProfile> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val today = todayDateKey()

            if (!snapshot.exists()) {
                val displayName = (user.displayName ?: user.email?.substringBefore("@") ?: "Creator")
                    .take(80)
                    .ifBlank { "Creator" }
                val email = (user.email ?: "").take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "preferredStyle" to ImageStyleOption.CINEMATIC.label,
                    "preferredLanguage" to "English",
                    "dailyQuotaUsed" to 0L,
                    "dailyQuotaLimit" to 12L,
                    "quotaDateKey" to today,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(
                    UserProfile(
                        userId = uid,
                        displayName = displayName,
                        email = email,
                        preferredStyle = ImageStyleOption.CINEMATIC.label,
                        preferredLanguage = "English",
                        dailyQuotaUsed = 0L,
                        dailyQuotaLimit = 12L,
                        quotaDateKey = today
                    )
                )
            } else {
                val existing = snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid)
                if (existing.quotaDateKey != today) {
                    docRef.update(
                        mapOf(
                            "dailyQuotaUsed" to 0L,
                            "quotaDateKey" to today,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success(existing.copy(dailyQuotaUsed = 0L, quotaDateKey = today))
                } else {
                    Result.success(existing)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile observation." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun consumeDailyQuota(userId: String): Result<UserProfile> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val today = todayDateKey()
            val profile = snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("User profile not initialized."))

            val effectiveUsed = if (profile.quotaDateKey == today) profile.dailyQuotaUsed else 0L
            if (effectiveUsed >= profile.dailyQuotaLimit) {
                return Result.failure(
                    IllegalStateException(
                        "Daily free quota reached ($effectiveUsed/${profile.dailyQuotaLimit}). Your free quota resets tomorrow."
                    )
                )
            }
            val nextUsed = effectiveUsed + 1L
            docRef.update(
                mapOf(
                    "dailyQuotaUsed" to nextUsed,
                    "quotaDateKey" to today,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(profile.copy(dailyQuotaUsed = nextUsed, quotaDateKey = today))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(
        userId: String,
        displayName: String,
        preferredStyle: String,
        preferredLanguage: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "preferredStyle" to preferredStyle.take(50).ifBlank { "Cinematic" },
                    "preferredLanguage" to preferredLanguage.take(40).ifBlank { "English" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeProjects(userId: String): Flow<List<VideoProject>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VideoProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(project: VideoProject): Result<String> {
        var path = "users/${project.userId}/projects"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("projects").document()
            path = docRef.path
            val sanitized = project.copy(userId = uid)
            docRef.set(sanitized.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProject(project: VideoProject): Result<Unit> {
        val path = "users/${project.userId}/projects/${project.id}"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Project ID is required." }
            db.collection("users").document(uid).collection("projects").document(project.id)
                .update(project.toUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameProject(projectId: String, newTitle: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/projects/$projectId"
        return try {
            val activeUid = requireUserId()
            val clean = newTitle.trim().take(120)
            require(clean.isNotEmpty()) { "Project title cannot be empty." }
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            db.collection("users").document(activeUid).collection("projects").document(projectId)
                .update(
                    mapOf(
                        "title" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/projects/$projectId"
        return try {
            val activeUid = requireUserId()
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            db.collection("users").document(activeUid).collection("projects").document(projectId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeGeneratedImages(userId: String): Flow<List<GeneratedImageItem>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/generated_images"
        emitAll(
            db.collection("users").document(uid).collection("generated_images")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(GeneratedImageItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveGeneratedImage(item: GeneratedImageItem): Result<String> {
        var path = "users/${item.userId}/generated_images"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("generated_images").document()
            path = docRef.path
            docRef.set(item.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGeneratedImage(imageId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/generated_images/$imageId"
        return try {
            val activeUid = requireUserId()
            require(imageId.isNotBlank())
            db.collection("users").document(activeUid).collection("generated_images").document(imageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeGenerationJobs(userId: String): Flow<List<GenerationJob>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/generation_jobs"
        emitAll(
            db.collection("users").document(uid).collection("generation_jobs")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(10)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(GenerationJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createGenerationJob(
        projectId: String,
        jobType: JobType,
        statusMessage: String
    ): Result<String> {
        var path = "users/unknown/generation_jobs"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("generation_jobs").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "projectId" to projectId.take(128),
                "jobType" to jobType.name,
                "status" to JobStatus.RUNNING.name,
                "progressPercent" to 5L,
                "statusMessage" to statusMessage.take(250),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGenerationJob(
        jobId: String,
        status: JobStatus,
        progressPercent: Int,
        statusMessage: String,
        errorMessage: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/generation_jobs/$jobId"
        return try {
            val activeUid = requireUserId()
            if (jobId.isBlank()) return Result.success(Unit)
            val raw = mutableMapOf<String, Any?>(
                "status" to status.name,
                "progressPercent" to progressPercent.coerceIn(0, 100).toLong(),
                "statusMessage" to statusMessage.take(250),
                "errorMessage" to errorMessage?.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = raw.filterValues { it != null } as Map<String, Any>
            db.collection("users").document(activeUid).collection("generation_jobs").document(jobId)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeFeedback(userId: String): Flow<List<AppFeedback>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/app_feedback"
        emitAll(
            db.collection("users").document(uid).collection("app_feedback")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AppFeedback::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitFeedback(
        rating: Int,
        opinion: String,
        userEmail: String
    ): Result<Unit> {
        var path = "users/unknown/app_feedback"
        return try {
            val uid = requireUserId()
            val cleanOpinion = opinion.trim().take(1500)
            require(cleanOpinion.isNotEmpty()) { "Please write your opinion before submitting." }
            val docRef = db.collection("users").document(uid).collection("app_feedback").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "rating" to rating.coerceIn(1, 5).toLong(),
                "opinion" to cleanOpinion,
                "userEmail" to userEmail.take(120),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
