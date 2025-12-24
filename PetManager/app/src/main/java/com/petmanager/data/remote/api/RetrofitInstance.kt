package com.petmanager.data.remote.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.petmanager.data.remote.api.FCMService

object RetrofitInstance {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://fcm.googleapis.com/") // FCM API 엔드포인트
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: FCMService by lazy {
        retrofit.create(FCMService::class.java)
    }
}

