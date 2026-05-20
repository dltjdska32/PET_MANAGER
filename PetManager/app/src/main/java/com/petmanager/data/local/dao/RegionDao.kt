package com.petmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.petmanager.data.local.entity.RegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegionDao {
    
    @Query("SELECT * FROM regions WHERE id = :id")
    suspend fun getRegionById(id: Long): RegionEntity?

    @Query("SELECT * FROM regions WHERE id IN (:ids)")
    suspend fun getRegionsByIds(ids: List<Long>): List<RegionEntity>
    
    @Query("SELECT * FROM regions WHERE level = 1 ORDER BY id")
    suspend fun getAllSido(): List<RegionEntity>
    
    @Query("SELECT * FROM regions WHERE level = 1 ORDER BY id")
    fun getAllSidoFlow(): Flow<List<RegionEntity>>
    
    @Query("SELECT * FROM regions WHERE level = 2 AND parentId = :parentId ORDER BY id")
    suspend fun getSigunguBySido(parentId: Long): List<RegionEntity>
    
    @Query("SELECT * FROM regions WHERE level = 2 AND parentId = :parentId ORDER BY id")
    fun getSigunguBySidoFlow(parentId: Long): Flow<List<RegionEntity>>
    
    @Query("SELECT * FROM regions ORDER BY id")
    suspend fun getAllRegions(): List<RegionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: RegionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegions(regions: List<RegionEntity>)
    
    @Query("DELETE FROM regions")
    suspend fun deleteAllRegions()
    
    @Query("SELECT COUNT(*) FROM regions")
    suspend fun getRegionCount(): Int
}

