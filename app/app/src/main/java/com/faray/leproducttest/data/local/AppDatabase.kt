package com.faray.leproducttest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.faray.leproducttest.data.local.dao.BatchMacDao
import com.faray.leproducttest.data.local.dao.BatchProfileDao
import com.faray.leproducttest.data.local.dao.LocalConfigDao
import com.faray.leproducttest.data.local.dao.ProductionSessionDao
import com.faray.leproducttest.data.local.dao.TestRecordDao
import com.faray.leproducttest.data.local.dao.UploadRecordDao
import com.faray.leproducttest.data.local.entity.BatchMacEntity
import com.faray.leproducttest.data.local.entity.BatchProfileEntity
import com.faray.leproducttest.data.local.entity.LocalConfigEntity
import com.faray.leproducttest.data.local.entity.ProductionSessionEntity
import com.faray.leproducttest.data.local.entity.TestRecordEntity
import com.faray.leproducttest.data.local.entity.UploadRecordEntity

@Database(
    entities = [
        BatchProfileEntity::class,
        BatchMacEntity::class,
        LocalConfigEntity::class,
        ProductionSessionEntity::class,
        TestRecordEntity::class,
        UploadRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun batchProfileDao(): BatchProfileDao
    abstract fun batchMacDao(): BatchMacDao
    abstract fun localConfigDao(): LocalConfigDao
    abstract fun productionSessionDao(): ProductionSessionDao
    abstract fun testRecordDao(): TestRecordDao
    abstract fun uploadRecordDao(): UploadRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "production_app.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
