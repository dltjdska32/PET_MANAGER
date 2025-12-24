package com.petmanager.domain.model

data class Profiles(
    val work: Int,
    val names: String,
    val dong: String,
    val ment: String,
    var isFavorite: Boolean = false,
    val postID: String,
    val deadline: String
)

