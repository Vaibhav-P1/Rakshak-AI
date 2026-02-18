package com.safety.rakshak.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EmergencyContact::class],
    version = 1,
    exportSchema = false
)
abstract class RakshakDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        @Volatile
        private var INSTANCE: RakshakDatabase? = null

        fun getDatabase(context: Context): RakshakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RakshakDatabase::class.java,
                    "rakshak_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
