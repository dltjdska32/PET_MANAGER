package com.petmanager.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// 로그인 요청 DTO
data class LoginRequest(
    val email: String,
    val password: String
)

// 회원가입 요청 DTO
data class SignUpRequest(
    val email: String,
    val password: String,
    val name: String
)

// 인증 응답 DTO
data class AuthResponse(
    val token: String,
    val refreshToken: String?,
    val user: UserDto?
)

data class UserDto(
    val id: Long,
    val email: String,
    val name: String
)

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<AuthResponse>
    
    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body refreshToken: String): Response<AuthResponse>
}

