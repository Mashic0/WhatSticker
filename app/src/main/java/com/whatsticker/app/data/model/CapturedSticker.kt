package com.whatsticker.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa una captura o sticker en el sistema.
 * Si [packId] es null, se considera una "captura sin asignación".
 */
@Entity(
    tableName = "captured_stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerPack::class,
            parentColumns = ["id"],
            childColumns = ["packId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["packId"])]
)
data class CapturedSticker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val originalWidth: Int = 512,
    val originalHeight: Int = 512,
    val isAnimated: Boolean = false, // true si proviene de un GIF animado
    val emojis: String = "✨",       // WhatsApp requiere emojis asociados (1-3 emojis)
    val packId: String? = null,     // null = en bandeja de capturas sin asignar
    val createdAt: Long = System.currentTimeMillis()
)
