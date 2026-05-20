package com.petmanager.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.petmanager.data.local.dao.RegionDao
import com.petmanager.data.local.dao.UserDao
import com.petmanager.data.local.entity.RegionEntity
import com.petmanager.data.local.entity.UserEntity
import com.petmanager.data.local.entity.UserRegionEntity

@Database(
    entities = [
        RegionEntity::class,
        UserEntity::class,
        UserRegionEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun regionDao(): RegionDao
    abstract fun userDao(): UserDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pet_manager_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

