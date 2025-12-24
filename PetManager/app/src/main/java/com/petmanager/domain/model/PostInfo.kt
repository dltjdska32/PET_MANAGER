package com.petmanager.domain.model

data class PostInfo(
    val userID: String? = null,
    val title: String? = null,
    val price: Int? = null,
    val management_type: String? = null,
    val animal_type: String? = null,
    val contents: String? = null,
    val location: String? = null,
    val deadline: String? = null // 마감일 추가
)

