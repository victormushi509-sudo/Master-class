package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class LocalMediaStore(private val context: Context) {

    private val mediaRoot: File
        get() = File(context.filesDir, "media").apply { mkdirs() }

    private val imagesDir: File
        get() = File(mediaRoot, "images").apply { mkdirs() }

    private val videosDir: File
        get() = File(mediaRoot, "videos").apply { mkdirs() }

    private val tempDir: File
        get() = File(mediaRoot, "temp").apply { mkdirs() }

    suspend fun saveBitmapAsJpeg(
        bitmap: Bitmap,
        prefix: String = "img",
        quality: Int = 88
    ): String = withContext(Dispatchers.IO) {
        val file = File(imagesDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        file.absolutePath
    }

    suspend fun loadBitmapFromUri(uri: Uri, maxDimension: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            var sampleSize = 1
            while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }

    fun loadBitmapFromPath(path: String?, maxDimension: Int = 1280): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    fun createVideoOutputFile(projectId: String): File =
        File(videosDir, "victor_${projectId}_${System.currentTimeMillis()}.mp4")

    fun createTempFile(name: String): File = File(tempDir, name)

    fun clearTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun totalStorageUsedBytes(): Long {
        val imageBytes = imagesDir.listFiles()?.sumOf { it.length() } ?: 0L
        val videoBytes = videosDir.listFiles()?.sumOf { it.length() } ?: 0L
        return imageBytes + videoBytes
    }

    suspend fun exportImageToGallery(localPath: String, displayName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(localPath)
                require(srcFile.exists()) { "Image file no longer exists on device." }
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "victor_image" }
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName}_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VictorVideo")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create gallery image entry.")
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(srcFile).use { input -> input.copyTo(out) }
                } ?: error("Failed to write image to gallery.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                "Saved to Pictures/VictorVideo"
            }
        }

    suspend fun exportVideoToGallery(localPath: String, displayName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(localPath)
                require(srcFile.exists()) { "Video file no longer exists on device." }
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "victor_video" }
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "${safeName}_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VictorVideo")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create gallery video entry.")
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(srcFile).use { input -> input.copyTo(out) }
                } ?: error("Failed to write MP4 video to gallery.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                "Saved MP4 to Movies/VictorVideo"
            }
        }

    fun buildShareVideoIntent(localPath: String, title: String): Intent? {
        val file = File(localPath)
        if (!file.exists()) return null
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
