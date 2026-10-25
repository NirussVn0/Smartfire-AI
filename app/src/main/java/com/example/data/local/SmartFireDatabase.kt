package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.SmartFireDao
import com.example.data.local.entity.AnalysisInsightEntity
import com.example.data.local.entity.DeviceEntity
import com.example.data.local.entity.DeviceEventEntity
import com.example.data.local.entity.DeviceSnapshotEntity
import com.example.data.local.entity.ExtinguisherEntity
import com.example.data.local.entity.FloorEntity
import com.example.data.local.entity.HomeEntity
import com.example.data.local.entity.MapEdgeEntity
import com.example.data.local.entity.MapObjectEntity
import com.example.data.local.entity.RoomEntity
import com.example.data.local.entity.SyncCursorEntity
import com.example.data.local.entity.UserLocationEntity

@Database(
    entities = [
        HomeEntity::class,
        FloorEntity::class,
        RoomEntity::class,
        DeviceEntity::class,
        DeviceSnapshotEntity::class,
        DeviceEventEntity::class,
        SyncCursorEntity::class,
        ExtinguisherEntity::class,
        MapObjectEntity::class,
        MapEdgeEntity::class,
        UserLocationEntity::class,
        AnalysisInsightEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SmartFireDatabase : RoomDatabase() {
    abstract fun smartFireDao(): SmartFireDao

    companion object {
        @Volatile
        private var INSTANCE: SmartFireDatabase? = null

        fun getInstance(context: Context): SmartFireDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartFireDatabase::class.java,
                    "smartfire_iot.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
