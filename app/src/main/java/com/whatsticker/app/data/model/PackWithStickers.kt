package com.whatsticker.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Paquete de stickers junto con todos los stickers asociados.
 */
data class PackWithStickers(
    @Embedded
    val pack: StickerPack,
    @Relation(
        parentColumn = "id",
        entityColumn = "packId"
    )
    val stickers: List<CapturedSticker>
) {
    val stickerCount: Int
        get() = stickers.size

    val isValidForWhatsApp: Boolean
        get() = stickers.size in 3..30
}
