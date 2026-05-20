package com.petmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "user_regions",
    primaryKeys = ["userId", "regionId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class UserRegionEntity(
    val userId: String,
    val regionId: Long,
    val isPrimary: Boolean = false,
)
