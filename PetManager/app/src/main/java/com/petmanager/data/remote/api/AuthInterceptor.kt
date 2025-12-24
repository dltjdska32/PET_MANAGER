package com.petmanager.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response
import android.content.Context
import android.content.SharedPreferences

class AuthInterceptor(private val context: Context) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // SharedPreferences에서 JWT 토큰 가져오기
        val prefs: SharedPreferences = context.getSharedPreferences("pet_manager_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("jwt_token", null)
        
        // 토큰이 있으면 헤더에 추가
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
        } else {
            originalRequest.newBuilder()
                .header("Content-Type", "application/json")
                .build()
        }
        
        return chain.proceed(newRequest)
    }
}

