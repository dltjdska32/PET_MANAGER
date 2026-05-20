package com.petmanager.data.remote.api

data class ApiResponse<T>(
    val statusCode: String?,
    val code: String?,
    val message: String?,
    val value: T?
)

