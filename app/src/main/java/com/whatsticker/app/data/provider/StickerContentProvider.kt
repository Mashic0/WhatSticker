package com.whatsticker.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import com.whatsticker.app.data.local.WhatStickerDatabase
import com.whatsticker.app.util.ImageUtils
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider que expone los paquetes y stickers a WhatsApp siguiendo
 * las especificaciones oficiales de WhatsApp Sticker Integration.
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.whatsticker.app.stickercontentprovider"

        // Columnas de paquetes (METADATA)
        const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "publisher_email"
        const val PUBLISHER_WEBSITE = "publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "avoid_cache"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        // Columnas de stickers individuales
        const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_pack_animation_data" // o sticker_emojis

        private const val METADATA_CODE = 1
        private const val METADATA_STICKER_PACK_CODE = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA_CODE)
            addURI(AUTHORITY, "metadata/*", METADATA_STICKER_PACK_CODE)
            addURI(AUTHORITY, "stickers/*/*", STICKERS_CODE)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET_CODE)
        }
    }

    private lateinit var database: WhatStickerDatabase

    override fun onCreate(): Boolean {
        context?.let { ctx ->
            database = WhatStickerDatabase.getInstance(ctx)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val code = MATCHER.match(uri)
        return when (code) {
            METADATA_CODE -> getAllPacksCursor()
            METADATA_STICKER_PACK_CODE -> {
                val packId = uri.lastPathSegment ?: return null
                getPackStickersCursor(packId)
            }
            else -> null
        }
    }

    private fun getAllPacksCursor(): Cursor {
        val columns = arrayOf(
            BaseColumns._ID,
            STICKER_PACK_IDENTIFIER_IN_QUERY,
            STICKER_PACK_NAME_IN_QUERY,
            STICKER_PACK_PUBLISHER_IN_QUERY,
            STICKER_PACK_ICON_IN_QUERY,
            ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
            IOS_APP_DOWNLOAD_LINK_IN_QUERY,
            PUBLISHER_EMAIL,
            PUBLISHER_WEBSITE,
            PRIVACY_POLICY_WEBSITE,
            LICENSE_AGREEMENT_WEBSITE,
            IMAGE_DATA_VERSION,
            AVOID_CACHE,
            ANIMATED_STICKER_PACK
        )
        val cursor = MatrixCursor(columns)

        val packsWithStickers = runBlocking {
            database.stickerDao().getAllPacksSync()
        }

        packsWithStickers.forEachIndexed { index, pack ->
            val trayFileName = File(pack.trayImagePath).name
            cursor.addRow(
                arrayOf(
                    index,
                    pack.id,
                    pack.name,
                    pack.publisher,
                    trayFileName,
                    "https://play.google.com/store/apps/details?id=com.whatsticker.app",
                    "",
                    pack.publisherEmail,
                    pack.publisherWebsite,
                    pack.privacyPolicyWebsite,
                    pack.licenseAgreementWebsite,
                    "1",
                    false,
                    pack.isAnimated
                )
            )
        }
        return cursor
    }

    private fun getPackStickersCursor(packId: String): Cursor {
        val columns = arrayOf(
            STICKER_FILE_NAME_IN_QUERY,
            STICKER_FILE_EMOJI_IN_QUERY
        )
        val cursor = MatrixCursor(columns)

        val stickers = runBlocking {
            database.stickerDao().getStickersForPackSync(packId)
        }

        stickers.forEach { sticker ->
            val fileName = File(sticker.filePath).name
            // Los emojis van separados por comas o vacíos
            cursor.addRow(arrayOf(fileName, sticker.emojis))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (MATCHER.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            METADATA_STICKER_PACK_CODE -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
            STICKERS_CODE, STICKERS_ASSET_CODE -> "image/webp"
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val match = MATCHER.match(uri)
        if (match == STICKERS_CODE || match == STICKERS_ASSET_CODE) {
            val segments = uri.pathSegments
            // formato: stickers/{pack_id}/{file_name} o stickers_asset/{pack_id}/{file_name}
            val fileName = segments.lastOrNull() ?: throw FileNotFoundException("URI inválida: $uri")
            val context = context ?: throw FileNotFoundException("Context nulo")
            val file = File(ImageUtils.getStickersDir(context), fileName)

            if (!file.exists()) {
                throw FileNotFoundException("Archivo de sticker no encontrado: ${file.absolutePath}")
            }

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        return super.openAssetFile(uri, mode)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
