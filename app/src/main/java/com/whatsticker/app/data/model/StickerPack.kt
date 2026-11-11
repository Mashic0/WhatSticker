package com.whatsticker.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa un paquete de stickers listo o en edición para WhatsApp.
 * Cumple con los requerimientos de la API oficial de WhatsApp Stickers:
 * - Entre 3 y 30 stickers por paquete.
 * - Icono de bandeja (tray icon) de 96x96 px.
 */
@Entity(tableName = "sticker_packs")
data class StickerPack(
    @PrimaryKey
    val id: String, // Identificador único (ej: pack_17287382910)
    val name: String,
    val publisher: String,
    val trayImagePath: String, // Ruta al archivo de icono de 96x96
    val isAnimated: Boolean = false,
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
