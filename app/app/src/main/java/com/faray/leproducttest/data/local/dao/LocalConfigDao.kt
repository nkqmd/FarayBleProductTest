package com.faray.leproducttest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.faray.leproducttest.data.local.entity.LocalConfigEntity

@Dao
interface LocalConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalConfigEntity)

    @Query("SELECT * FROM local_config WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): LocalConfigEntity?
}
