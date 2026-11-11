package com.whatsticker.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ImageUtils {

    const val STICKER_SIZE = 512
    const val TRAY_SIZE = 96
    const val MAX_STATIC_SIZE_BYTES = 100 * 1024 // 100 KB para WhatsApp
    const val MAX_TRAY_SIZE_BYTES = 50 * 1024    // 50 KB para WhatsApp

    /**
     * Directorio base donde se almacenan los stickers de la aplicación.
     */
    fun getStickersDir(context: Context): File {
        val dir = File(context.filesDir, "stickers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Procesa un bitmap para cumplir con las directrices de WhatsApp:
     * - Lienzo transparente de 512x512
     * - Imagen escalada manteniendo la relación de aspecto (con padding de 16px)
     * - Formato WebP
     * - Peso < 100 KB
     */
    fun processStickerBitmap(source: Bitmap): ByteArray {
        val paddedSize = STICKER_SIZE - 32 // 16px margen en cada lado
        val scale = min(
            paddedSize.toFloat() / source.width.toFloat(),
            paddedSize.toFloat() / source.height.toFloat()
        )

        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)

        val scaledSource = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

        val outputBitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val left = ((STICKER_SIZE - targetWidth) / 2).toFloat()
        val top = ((STICKER_SIZE - targetHeight) / 2).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaledSource, left, top, paint)

        // Comprimir en WebP garantizando < 100 KB
        var quality = 95
        var outputBytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            outputBitmap.compress(format, quality, stream)
            outputBytes = stream.toByteArray()
            quality -= 10
        } while (outputBytes.size > MAX_STATIC_SIZE_BYTES && quality >= 30)

        return outputBytes
    }

    /**
     * Scales and centers a captured frame into a transparent 512x512 canvas and
     * compresses it as a static WebP frame at the given quality.
     */
    fun processStickerFrame(source: Bitmap, quality: Int = 60): ByteArray {
        val paddedSize = STICKER_SIZE - 32
        val scale = min(
            paddedSize.toFloat() / source.width.toFloat(),
            paddedSize.toFloat() / source.height.toFloat()
        )

        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)

        val scaledSource = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val outputBitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val left = ((STICKER_SIZE - targetWidth) / 2).toFloat()
        val top = ((STICKER_SIZE - targetHeight) / 2).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaledSource, left, top, paint)

        val stream = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        outputBitmap.compress(format, quality.coerceIn(0, 100), stream)
        return stream.toByteArray()
    }

    /**
     * Guarda un sticker procesado en el almacenamiento interno y retorna su ruta.
     */
    fun saveStickerToFile(context: Context, stickerBytes: ByteArray, filenamePrefix: String = "sticker"): File {
        val dir = getStickersDir(context)
        val file = File(dir, "${filenamePrefix}_${System.currentTimeMillis()}.webp")
        FileOutputStream(file).use { out ->
            out.write(stickerBytes)
        }
        return file
    }

    /**
     * Genera un icono de bandeja (tray icon) de 96x96 píxeles a partir de un Bitmap o archivo de sticker.
     */
    fun createTrayIcon(context: Context, sourceBitmap: Bitmap, packId: String): File {
        val scale = min(
            (TRAY_SIZE - 8).toFloat() / sourceBitmap.width.toFloat(),
            (TRAY_SIZE - 8).toFloat() / sourceBitmap.height.toFloat()
        )

        val targetWidth = (sourceBitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (sourceBitmap.height * scale).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
        val trayBitmap = Bitmap.createBitmap(TRAY_SIZE, TRAY_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(trayBitmap)

        val left = ((TRAY_SIZE - targetWidth) / 2).toFloat()
        val top = ((TRAY_SIZE - targetHeight) / 2).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaled, left, top, paint)

        val stream = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        trayBitmap.compress(format, 90, stream)

        val dir = getStickersDir(context)
        val trayFile = File(dir, "tray_${packId}.webp")
        FileOutputStream(trayFile).use { out ->
            out.write(stream.toByteArray())
        }
        return trayFile
    }

    /**
     * Recorta una región específica de un bitmap de pantalla completa.
     */
    fun cropBitmap(original: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        val safeLeft = left.coerceIn(0, original.width - 1)
        val safeTop = top.coerceIn(0, original.height - 1)
        val safeWidth = width.coerceIn(1, original.width - safeLeft)
        val safeHeight = height.coerceIn(1, original.height - safeTop)

        return Bitmap.createBitmap(original, safeLeft, safeTop, safeWidth, safeHeight)
    }

    /**
     * Carga un Bitmap desde un archivo.
     */
    fun loadBitmapFromFile(filePath: String): Bitmap? {
        return BitmapFactory.decodeFile(filePath)
    }
}
