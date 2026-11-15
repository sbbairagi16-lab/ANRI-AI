package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities =
    [
      UserProfileEntity::class,
      ConversationEntity::class,
      ChatMessageEntity::class,
      SavedPromptEntity::class,
    ],
  version = 1,
  exportSchema = false,
)
@TypeConverters(AnriTypeConverters::class)
abstract class AnriDatabase : RoomDatabase() {
  abstract fun anriDao(): AnriDao

  companion object {
    @Volatile private var instance: AnriDatabase? = null

    fun getInstance(context: Context): AnriDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                AnriDatabase::class.java,
                "anri_ai_workspace.db",
              )
              .build()
              .also { instance = it }
        }
    }
  }
}
