package com.whatsticker.app.data.local

import androidx.room.*
import com.whatsticker.app.data.model.CapturedSticker
import com.whatsticker.app.data.model.PackWithStickers
import com.whatsticker.app.data.model.StickerPack
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {

    // --- Capturas sin asignar ---
    @Query("SELECT * FROM captured_stickers WHERE packId IS NULL ORDER BY createdAt DESC")
    fun getUnassignedStickers(): Flow<List<CapturedSticker>>

    @Query("SELECT * FROM captured_stickers WHERE packId IS NULL ORDER BY createdAt DESC")
    suspend fun getUnassignedStickersSync(): List<CapturedSticker>

    @Query("SELECT COUNT(*) FROM captured_stickers WHERE packId IS NULL")
    fun getUnassignedCount(): Flow<Int>

    // --- Stickers asignados a un paquete ---
    @Query("SELECT * FROM captured_stickers WHERE packId = :packId ORDER BY createdAt ASC")
    fun getStickersForPack(packId: String): Flow<List<CapturedSticker>>

    @Query("SELECT * FROM captured_stickers WHERE packId = :packId ORDER BY createdAt ASC")
    suspend fun getStickersForPackSync(packId: String): List<CapturedSticker>

    // --- Paquetes ---
    @Transaction
    @Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
    fun getAllPacksWithStickers(): Flow<List<PackWithStickers>>

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE id = :packId LIMIT 1")
    fun getPackWithStickers(packId: String): Flow<PackWithStickers?>

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE id = :packId LIMIT 1")
    suspend fun getPackWithStickersSync(packId: String): PackWithStickers?

    @Query("SELECT * FROM sticker_packs WHERE id = :packId LIMIT 1")
    suspend fun getPackById(packId: String): StickerPack?

    @Query("SELECT * FROM sticker_packs ORDER BY createdAt DESC")
    suspend fun getAllPacksSync(): List<StickerPack>

    // --- Inserciones y Actualizaciones ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSticker(sticker: CapturedSticker): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPack)

    @Update
    suspend fun updatePack(pack: StickerPack)

    @Update
    suspend fun updateSticker(sticker: CapturedSticker)

    @Query("UPDATE captured_stickers SET packId = :packId WHERE id = :stickerId")
    suspend fun assignStickerToPack(stickerId: Long, packId: String?)

    @Query("UPDATE captured_stickers SET packId = NULL WHERE id = :stickerId")
    suspend fun unassignSticker(stickerId: Long)

    // --- Eliminaciones ---
    @Delete
    suspend fun deleteSticker(sticker: CapturedSticker)

    @Query("DELETE FROM captured_stickers WHERE id = :stickerId")
    suspend fun deleteStickerById(stickerId: Long)

    @Delete
    suspend fun deletePack(pack: StickerPack)

    @Query("DELETE FROM sticker_packs WHERE id = :packId")
    suspend fun deletePackById(packId: String)
}
