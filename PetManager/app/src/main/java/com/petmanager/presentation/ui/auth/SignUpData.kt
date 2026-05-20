package com.petmanager.presentation.ui.auth

data class SignUpData(
    var email: String = "",
    var isEmailVerified: Boolean = false,
    var nickname: String = "",
    var username: String = "",
    var isUsernameChecked: Boolean = false,
    var password: String = "",
    var regionIds: List<Long>? = null
)

