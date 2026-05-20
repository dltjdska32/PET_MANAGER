package com.petmanager.data.local.dao

import androidx.room.*
import com.petmanager.data.local.entity.UserEntity
import com.petmanager.data.local.entity.UserRegionEntity

@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    suspend fun getAllUsersSync(): List<UserEntity>

    @Query("SELECT regionId FROM user_regions WHERE userId = :userId ORDER BY isPrimary DESC, regionId ASC")
    suspend fun getUserRegionIdsSync(userId: String): List<Long>

    @Query("SELECT regionId FROM user_regions WHERE userId = :userId AND isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryRegionIdSync(userId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserRegions(userRegions: List<UserRegionEntity>)

    @Query("DELETE FROM user_regions WHERE userId = :userId")
    suspend fun deleteUserRegions(userId: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("UPDATE user_regions SET isPrimary = 0 WHERE userId = :userId")
    suspend fun clearPrimaryRegion(userId: String)

    @Query("UPDATE user_regions SET isPrimary = 1 WHERE userId = :userId AND regionId = :regionId")
    suspend fun markPrimaryRegion(userId: String, regionId: Long)

    /**
     * 유저 정보와 지역 정보를 원자적으로 업데이트.
     * 기존 대표 지역이 새 목록에 포함되면 유지한다.
     */
    @Transaction
    suspend fun updateUserInfo(user: UserEntity, regionIds: List<Long>) {
        insertUser(user)
        val preservedPrimary = getPrimaryRegionIdSync(user.id)?.takeIf { it in regionIds }
        deleteUserRegions(user.id)
        val userRegions = regionIds.map { regionId ->
            UserRegionEntity(
                userId = user.id,
                regionId = regionId,
                isPrimary = regionId == preservedPrimary,
            )
        }
        insertUserRegions(userRegions)
    }

    @Transaction
    suspend fun setPrimaryRegion(userId: String, regionId: Long) {
        clearPrimaryRegion(userId)
        markPrimaryRegion(userId, regionId)
    }
}
