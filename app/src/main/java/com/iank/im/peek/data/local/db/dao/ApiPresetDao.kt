package com.iank.im.peek.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iank.im.peek.data.local.db.entity.ApiPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiPresetDao {
    @Query("SELECT * FROM api_presets ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ApiPresetEntity>>

    @Query("SELECT * FROM api_presets ORDER BY createdAt ASC")
    suspend fun getAll(): List<ApiPresetEntity>

    @Query("SELECT * FROM api_presets WHERE id = :id")
    suspend fun getById(id: String): ApiPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: ApiPresetEntity)

    @Query("DELETE FROM api_presets WHERE id = :id")
    suspend fun deleteById(id: String)
}
