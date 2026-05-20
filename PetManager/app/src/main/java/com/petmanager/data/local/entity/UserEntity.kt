package com.petmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String? = null,
    val email: String? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null
)
