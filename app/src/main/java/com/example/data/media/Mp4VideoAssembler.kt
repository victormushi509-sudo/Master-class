package com.example.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.data.model.AspectRatioOption
import com.example.data.model.SceneItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

private const val TAG = "Mp4VideoAssembler"

data class AssembledVideoResult(
    val videoFile: File,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val hasAudioTrack: Boolean
)

class Mp4VideoAssembler(
    private val mediaStore: LocalMediaStore,
    private val narrationSynthesizer: NarrationSynthesizer
) {
    suspend fun assembleMp4(
        projectId: String,
        scenes: List<SceneItem>,
        aspectRatio: AspectRatioOption,
        narrationEnabled: Boolean,
        subtitlesEnabled: Boolean,
        language: String,
        onProgress: suspend (Float, String) -> Unit
    ): AssembledVideoResult = withContext(Dispatchers.Default) {
        require(scenes.isNotEmpty()) { "At least one scene is required to assemble an MP4 video." }

        val width = aspectRatio.width
        val height = aspectRatio.height
        val fps = 24
        val frameDurationUs = 1_000_000L / fps

        // Load and pre-prepare scene bitmaps
        val sceneBitmaps = scenes.map { scene ->
            mediaStore.loadBitmapFromPath(scene.localImagePath, maxDimension = max(width, height))
                ?: createFallbackSceneBitmap(width, height, scene.title)
        }

        val tempVideoFile = mediaStore.createTempFile("vid_${projectId}_${System.currentTimeMillis()}.mp4")
        val finalOutputFile = mediaStore.createVideoOutputFile(projectId)

        try {
            onProgress(0.10f, "Encoding H.264 video frames...")
            encodeVideoTrack(
                outputFile = tempVideoFile,
                scenes = scenes,
                bitmaps = sceneBitmaps,
                width = width,
                height = height,
                fps = fps,
                frameDurationUs = frameDurationUs,
                subtitlesEnabled = subtitlesEnabled,
                onEncodeProgress = { ratio ->
                    onProgress(0.10f + ratio * 0.55f, "Rendering scenes ${(ratio * 100).toInt()}%...")
                }
            )

            var hasAudioTrack = false
            if (narrationEnabled) {
                onProgress(0.70f, "Synthesizing voice narration...")
                val sceneWavs = scenes.mapIndexed { idx, scene ->
                    val wavFile = mediaStore.createTempFile("tts_${projectId}_${idx}.wav")
                    val ok = narrationSynthesizer.synthesizeSceneWav(
                        text = scene.narrationText,
                        languageLabel = language,
                        outputFile = wavFile
                    )
                    if (ok) wavFile else null
                }

                if (sceneWavs.any { it != null }) {
                    onProgress(0.84f, "Muxing AAC audio with MP4 video...")
                    val tempAudioFile = mediaStore.createTempFile("aud_${projectId}_${System.currentTimeMillis()}.m4a")
                    val audioOk = buildAndEncodeNarrationAudio(
                        scenes = scenes,
                        sceneWavs = sceneWavs,
                        outputAudioFile = tempAudioFile
                    )
                    if (audioOk && tempAudioFile.exists() && tempAudioFile.length() > 0L) {
                        muxVideoAndAudio(tempVideoFile, tempAudioFile, finalOutputFile)
                        hasAudioTrack = true
                    }
                    tempAudioFile.delete()
                }
                sceneWavs.forEach { it?.delete() }
            }

            if (!hasAudioTrack) {
                if (finalOutputFile.exists()) finalOutputFile.delete()
                if (!tempVideoFile.renameTo(finalOutputFile)) {
                    tempVideoFile.copyTo(finalOutputFile, overwrite = true)
                    tempVideoFile.delete()
                }
            } else {
                tempVideoFile.delete()
            }

            onProgress(1.0f, "MP4 export ready!")
            AssembledVideoResult(
                videoFile = finalOutputFile,
                width = width,
                height = height,
                fileSizeBytes = finalOutputFile.length(),
                hasAudioTrack = hasAudioTrack
            )
        } finally {
            sceneBitmaps.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
            if (tempVideoFile.exists()) tempVideoFile.delete()
        }
    }

    private suspend fun encodeVideoTrack(
        outputFile: File,
        scenes: List<SceneItem>,
        bitmaps: List<Bitmap>,
        width: Int,
        height: Int,
        fps: Int,
        frameDurationUs: Long,
        subtitlesEnabled: Boolean,
        onEncodeProgress: suspend (Float) -> Unit
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var videoTrackIndex = -1
        var outputFrameIndex = 0L

        val totalFrames = scenes.sumOf { it.durationSeconds * fps }.coerceAtLeast(fps)
        var renderedFrames = 0

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val crossfadePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                height * 0.65f,
                0f,
                height.toFloat(),
                Color.TRANSPARENT,
                Color.argb(215, 7, 12, 23),
                Shader.TileMode.CLAMP
            )
        }
        val subtitleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 11, 18, 33)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (min(width, height) * 0.044f).coerceIn(26f, 44f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2F80FF")
        }

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            videoTrackIndex = muxer!!.addTrack(encoder.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                    } else if (outputIndex >= 0) {
                        val encodedBuffer = encoder.getOutputBuffer(outputIndex)
                        if (encodedBuffer != null && bufferInfo.size > 0) {
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig && muxerStarted) {
                                encodedBuffer.position(bufferInfo.offset)
                                encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val fixedInfo = MediaCodec.BufferInfo().apply {
                                    set(
                                        bufferInfo.offset,
                                        bufferInfo.size,
                                        outputFrameIndex * frameDurationUs,
                                        bufferInfo.flags
                                    )
                                }
                                muxer!!.writeSampleData(videoTrackIndex, encodedBuffer, fixedInfo)
                                outputFrameIndex++
                            }
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }

            scenes.forEachIndexed { sceneIndex, scene ->
                val sceneFrames = (scene.durationSeconds * fps).coerceAtLeast(fps)
                val currentBitmap = bitmaps[sceneIndex]
                val nextBitmap = bitmaps.getOrNull(sceneIndex + 1)
                val transitionFrames = min(10, sceneFrames / 4)

                for (f in 0 until sceneFrames) {
                    val progress = f.toFloat() / (sceneFrames - 1).coerceAtLeast(1)
                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawColor(Color.parseColor("#080E1A"))
                        drawMotionFrame(
                            canvas = canvas,
                            bitmap = currentBitmap,
                            width = width,
                            height = height,
                            progress = progress,
                            motion = scene.motionDirection,
                            paint = bitmapPaint
                        )

                        // Smooth crossfade to next scene near scene end
                        if (nextBitmap != null && f >= sceneFrames - transitionFrames) {
                            val fadeAlpha = ((f - (sceneFrames - transitionFrames) + 1).toFloat() / transitionFrames * 255f)
                                .toInt().coerceIn(0, 255)
                            crossfadePaint.alpha = fadeAlpha
                            drawMotionFrame(
                                canvas = canvas,
                                bitmap = nextBitmap,
                                width = width,
                                height = height,
                                progress = 0f,
                                motion = scenes[sceneIndex + 1].motionDirection,
                                paint = crossfadePaint
                            )
                        }

                        // Subtle cinematic scrim + timeline progress bar
                        canvas.drawRect(0f, height * 0.62f, width.toFloat(), height.toFloat(), scrimPaint)
                        val globalProgress = (renderedFrames.toFloat() / totalFrames).coerceIn(0f, 1f)
                        canvas.drawRect(0f, height - 6f, width * globalProgress, height.toFloat(), accentPaint)

                        if (subtitlesEnabled && scene.subtitleText.isNotBlank()) {
                            drawSubtitleOverlay(
                                canvas = canvas,
                                text = scene.subtitleText,
                                width = width,
                                height = height,
                                textPaint = subtitlePaint,
                                bgPaint = subtitleBgPaint
                            )
                        }
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    renderedFrames++
                    drainEncoder(false)
                    if (renderedFrames % 12 == 0) {
                        onEncodeProgress(renderedFrames.toFloat() / totalFrames)
                    }
                }
            }

            drainEncoder(true)
            inputSurface.release()
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun drawMotionFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        progress: Float,
        motion: String,
        paint: Paint
    ) {
        val baseScale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val extraScale = when (motion) {
            "zoom_out" -> 1.14f - 0.10f * progress
            "pan_left", "pan_right", "tilt_up" -> 1.12f
            else -> 1.04f + 0.10f * progress // zoom_in default
        }
        val scale = baseScale * extraScale
        val scaledW = bitmap.width * scale
        val scaledH = bitmap.height * scale
        val slackX = (scaledW - width) / 2f
        val slackY = (scaledH - height) / 2f

        val offsetX = when (motion) {
            "pan_left" -> -slackX + (2f * slackX * (1f - progress)) - slackX
            "pan_right" -> -slackX - slackX + (2f * slackX * progress)
            else -> -slackX
        }
        val offsetY = when (motion) {
            "tilt_up" -> -slackY + slackY * (1f - 2f * progress)
            else -> -slackY
        }

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawSubtitleOverlay(
        canvas: Canvas,
        text: String,
        width: Int,
        height: Int,
        textPaint: TextPaint,
        bgPaint: Paint
    ) {
        val maxTextWidth = (width * 0.84f).toInt().coerceAtLeast(200)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(4f, 1.05f)
            .build()

        val boxWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: maxTextWidth.toFloat()
        val padX = 28f
        val padY = 16f
        val bottomMargin = height * 0.085f
        val top = height - bottomMargin - layout.height - padY * 2f
        val left = (width - boxWidth) / 2f - padX
        val right = (width + boxWidth) / 2f + padX
        val bottom = top + layout.height + padY * 2f

        canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, bgPaint)
        canvas.save()
        canvas.translate((width - maxTextWidth) / 2f, top + padY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildAndEncodeNarrationAudio(
        scenes: List<SceneItem>,
        sceneWavs: List<File?>,
        outputAudioFile: File
    ): Boolean {
        val sampleRate = 22050
        val channels = 1
        val pcmSegments = scenes.mapIndexed { idx, scene ->
            val targetSamples = scene.durationSeconds * sampleRate
            val wavFile = sceneWavs.getOrNull(idx)
            val rawSamples = if (wavFile != null && wavFile.exists()) {
                readWavSamplesResampled(wavFile, sampleRate)
            } else {
                ShortArray(0)
            }
            ShortArray(targetSamples).also { out ->
                val copyLen = min(rawSamples.size, targetSamples)
                System.arraycopy(rawSamples, 0, out, 0, copyLen)
            }
        }
        val totalSamples = pcmSegments.sumOf { it.size }
        if (totalSamples <= 0) return false

        val combinedPcm = ShortArray(totalSamples)
        var offset = 0
        pcmSegments.forEach { seg ->
            System.arraycopy(seg, 0, combinedPcm, offset, seg.size)
            offset += seg.size
        }

        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channels
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var audioTrackIdx = -1

        return try {
            codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(outputAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var sampleCursor = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (sampleCursor >= combinedPcm.size) {
                            codec.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                (sampleCursor.toLong() * 1_000_000L) / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val maxSamples = inBuf.remaining() / 2
                            val count = min(maxSamples, combinedPcm.size - sampleCursor)
                            val byteBuf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until count) {
                                byteBuf.putShort(combinedPcm[sampleCursor + i])
                            }
                            byteBuf.flip()
                            inBuf.put(byteBuf)
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate
                            sampleCursor += count
                            codec.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            audioTrackIdx = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && muxerStarted &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIdx, outBuf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Audio encoding failed, continuing with silent video track", e)
            false
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun readWavSamplesResampled(wavFile: File, targetSampleRate: Int): ShortArray {
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return ShortArray(0)

        val headerBuf = ByteBuffer.wrap(bytes, 0, min(bytes.size, 64)).order(ByteOrder.LITTLE_ENDIAN)
        val channels = headerBuf.getShort(22).toInt().coerceAtLeast(1)
        val srcSampleRate = headerBuf.getInt(24).coerceIn(8000, 96000)

        // Locate "data" chunk cleanly
        var dataOffset = 12
        var dataSize = bytes.size - 44
        while (dataOffset + 8 < bytes.size) {
            val chunkId = String(bytes, dataOffset, 4, Charsets.US_ASCII)
            val chunkLen = ByteBuffer.wrap(bytes, dataOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                dataOffset += 8
                dataSize = min(chunkLen, bytes.size - dataOffset)
                break
            }
            dataOffset += 8 + chunkLen
        }
        if (dataSize <= 0 || dataOffset >= bytes.size) return ShortArray(0)

        val totalFrames = dataSize / (2 * channels)
        if (totalFrames <= 0) return ShortArray(0)
        val monoSrc = ShortArray(totalFrames)
        val pcmBuf = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalFrames) {
            var sum = 0
            for (c in 0 until channels) {
                sum += pcmBuf.short.toInt()
            }
            monoSrc[i] = (sum / channels).toShort()
        }

        if (srcSampleRate == targetSampleRate) return monoSrc
        val ratio = srcSampleRate.toDouble() / targetSampleRate.toDouble()
        val outLen = (monoSrc.size / ratio).toInt().coerceAtLeast(1)
        return ShortArray(outLen) { idx ->
            val srcIdx = (idx * ratio).toInt().coerceIn(0, monoSrc.lastIndex)
            monoSrc[srcIdx]
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoFormat = videoExtractor.getTrackFormat(0)
            val audioFormat = audioExtractor.getTrackFormat(0)
            videoExtractor.selectTrack(0)
            audioExtractor.selectTrack(0)

            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                info.offset = 0
                info.size = videoExtractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = if ((videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxVideoTrack, buffer, info)
                videoExtractor.advance()
            }

            while (true) {
                info.offset = 0
                info.size = audioExtractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = audioExtractor.sampleTime
                info.flags = 0
                muxer.writeSampleData(muxAudioTrack, buffer, info)
                audioExtractor.advance()
            }
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun createFallbackSceneBitmap(width: Int, height: Int, label: String): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#0B1221"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2F80FF")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label.take(36), width / 2f, height / 2f, paint)
        return bmp
    }
}
