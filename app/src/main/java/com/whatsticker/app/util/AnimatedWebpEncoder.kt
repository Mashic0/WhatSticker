package com.whatsticker.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions
import com.aureusapps.android.webpandroid.encoder.WebPConfig
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams
import com.aureusapps.android.webpandroid.encoder.WebPPreset
import java.io.File

interface AnimatedWebpWriter {
    fun encode(
        frames: List<Bitmap>,
        width: Int,
        height: Int,
        frameDurationMs: Long,
        quality: Int
    ): ByteArray?

    fun encodeCompressed(
        frames: List<ByteArray>,
        width: Int,
        height: Int,
        frameDurationMs: Long,
        quality: Int
    ): ByteArray?
}

class AnimatedWebpEncoder(
    private val maxSizeBytes: Int = MAX_ANIMATED_STICKER_SIZE_BYTES,
    private val qualitySteps: List<Int> = DEFAULT_QUALITY_STEPS,
    private val writer: AnimatedWebpWriter
) {
    fun encode(frames: List<Bitmap>, frameDurationMs: Long): ByteArray? {
        if (frames.isEmpty() || frameDurationMs <= 0L) return null

        val width = frames.first().width
        val height = frames.first().height
        if (width <= 0 || height <= 0) return null

        val encodedByQuality = linkedMapOf<Int, ByteArray?>()
        val picked = AnimatedWebpSizePolicy.pickFirstFittingQuality(
            qualities = qualitySteps,
            maxSizeBytes = maxSizeBytes
        ) { quality ->
            val encoded = writer.encode(frames, width, height, frameDurationMs, quality)
            encodedByQuality[quality] = encoded
            encoded?.size
        }

        return picked?.let { encodedByQuality[it.quality] }
    }

    fun encodeCompressed(frames: List<ByteArray>, frameDurationMs: Long): ByteArray? {
        if (frames.isEmpty() || frameDurationMs <= 0L) return null

        val firstBitmap = BitmapFactory.decodeByteArray(frames.first(), 0, frames.first().size) ?: return null
        val width = firstBitmap.width
        val height = firstBitmap.height
        firstBitmap.recycle()
        if (width <= 0 || height <= 0) return null

        val encodedByQuality = linkedMapOf<Int, ByteArray?>()
        val picked = AnimatedWebpSizePolicy.pickFirstFittingQuality(
            qualities = qualitySteps,
            maxSizeBytes = maxSizeBytes
        ) { quality ->
            val encoded = writer.encodeCompressed(frames, width, height, frameDurationMs, quality)
            encodedByQuality[quality] = encoded
            encoded?.size
        }

        return picked?.let { encodedByQuality[it.quality] }
    }

    companion object {
        const val MAX_ANIMATED_STICKER_SIZE_BYTES = 500 * 1024
        val DEFAULT_QUALITY_STEPS = listOf(90, 80, 70, 60, 50, 40, 30)
    }
}

class AureusAppsAnimatedWebpWriter(
    private val context: Context
) : AnimatedWebpWriter {
    override fun encode(
        frames: List<Bitmap>,
        width: Int,
        height: Int,
        frameDurationMs: Long,
        quality: Int
    ): ByteArray? {
        val outputFile = File.createTempFile("animated_sticker_", ".webp", context.cacheDir)
        return try {
            val outputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val encoder = WebPAnimEncoder(
                context,
                width,
                height,
                WebPAnimEncoderOptions(
                    minimizeSize = true,
                    animParams = WebPMuxAnimParams(loopCount = 0)
                )
            )

            try {
                encoder.configure(
                    WebPConfig(
                        lossless = WebPConfig.COMPRESSION_LOSSY,
                        quality = quality.toFloat()
                    ),
                    WebPPreset.WEBP_PRESET_PICTURE
                )

                var timestampMs = 0L
                for (frame in frames) {
                    encoder.addFrame(timestampMs, frame)
                    timestampMs += frameDurationMs
                }

                encoder.assemble(timestampMs, outputUri)
            } finally {
                encoder.release()
            }

            if (outputFile.exists() && outputFile.length() > 0L) outputFile.readBytes() else null
        } catch (_: Throwable) {
            null
        } finally {
            outputFile.delete()
        }
    }

    override fun encodeCompressed(
        frames: List<ByteArray>,
        width: Int,
        height: Int,
        frameDurationMs: Long,
        quality: Int
    ): ByteArray? {
        val outputFile = File.createTempFile("animated_sticker_", ".webp", context.cacheDir)
        return try {
            val outputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val encoder = WebPAnimEncoder(
                context,
                width,
                height,
                WebPAnimEncoderOptions(
                    minimizeSize = true,
                    animParams = WebPMuxAnimParams(loopCount = 0)
                )
            )

            try {
                encoder.configure(
                    WebPConfig(
                        lossless = WebPConfig.COMPRESSION_LOSSY,
                        quality = quality.toFloat()
                    ),
                    WebPPreset.WEBP_PRESET_PICTURE
                )

                var timestampMs = 0L
                for (frame in frames) {
                    val bitmap = BitmapFactory.decodeByteArray(frame, 0, frame.size) ?: return null
                    try {
                        encoder.addFrame(timestampMs, bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    timestampMs += frameDurationMs
                }

                encoder.assemble(timestampMs, outputUri)
            } finally {
                encoder.release()
            }

            if (outputFile.exists() && outputFile.length() > 0L) outputFile.readBytes() else null
        } catch (_: Throwable) {
            null
        } finally {
            outputFile.delete()
        }
    }
}
