package com.petmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey
    val id: Long,
    val level: Int, // 1: 시도, 2: 시군구
    val parentId: Long?, // 부모 시도 ID (시도는 null, 시군구는 해당 시도 ID)
    val regionName: String // 지역명
)

