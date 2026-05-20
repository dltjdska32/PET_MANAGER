package com.petmanager.domain.model

data class Profiles(
    val work: Int,
    val names: String,
    val dong: String,
    val ment: String,
    var isFavorite: Boolean = false,
    val postID: String,
    val deadline: String,
    /** 목록 썸네일 (첫 번째 메인 이미지 URL) */
    val thumbnailUrl: String? = null,
)

