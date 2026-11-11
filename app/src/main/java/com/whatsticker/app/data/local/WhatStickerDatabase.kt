package com.whatsticker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.whatsticker.app.data.model.CapturedSticker
import com.whatsticker.app.data.model.StickerPack

@Database(
    entities = [CapturedSticker::class, StickerPack::class],
    version = 1,
    exportSchema = false
)
abstract class WhatStickerDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao

    companion object {
        @Volatile
        private var INSTANCE: WhatStickerDatabase? = null

        fun getInstance(context: Context): WhatStickerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WhatStickerDatabase::class.java,
                    "whatsticker_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
