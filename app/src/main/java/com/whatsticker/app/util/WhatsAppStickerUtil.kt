package com.whatsticker.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.whatsticker.app.data.model.PackWithStickers
import com.whatsticker.app.data.provider.StickerContentProvider

object WhatsAppStickerUtil {

    const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /**
     * Verifica si WhatsApp estándar está instalado en el dispositivo.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context, WHATSAPP_PACKAGE)
    }

    /**
     * Verifica si WhatsApp Business está instalado.
     */
    fun isWhatsAppBusinessInstalled(context: Context): Boolean {
        return isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inicia el proceso para agregar el paquete de stickers a WhatsApp.
     * Retorna un par (éxito: Boolean, mensaje: String).
     */
    fun addPackToWhatsApp(context: Context, packWithStickers: PackWithStickers): Pair<Boolean, String> {
        val stickers = packWithStickers.stickers
        if (stickers.size < 3) {
            return Pair(false, "WhatsApp requiere al menos 3 stickers por paquete (actualmente tiene ${stickers.size}).")
        }
        if (stickers.size > 30) {
            return Pair(false, "WhatsApp permite un máximo de 30 stickers por paquete (actualmente tiene ${stickers.size}).")
        }

        val targetPackage = when {
            isWhatsAppInstalled(context) -> WHATSAPP_PACKAGE
            isWhatsAppBusinessInstalled(context) -> WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }

        if (targetPackage == null) {
            return Pair(false, "WhatsApp o WhatsApp Business no están instalados en este dispositivo.")
        }

        val intent = Intent().apply {
            action = ACTION_ENABLE_STICKER_PACK
            putExtra(EXTRA_STICKER_PACK_ID, packWithStickers.pack.id)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, StickerContentProvider.AUTHORITY)
            putExtra(EXTRA_STICKER_PACK_NAME, packWithStickers.pack.name)
            setPackage(targetPackage)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(intent)
            Pair(true, "Enviando paquete a WhatsApp...")
        } catch (e: ActivityNotFoundException) {
            Pair(false, "No se pudo abrir WhatsApp para agregar el paquete: ${e.localizedMessage}")
        } catch (e: Exception) {
            Pair(false, "Error al transferir: ${e.localizedMessage}")
        }
    }
}
