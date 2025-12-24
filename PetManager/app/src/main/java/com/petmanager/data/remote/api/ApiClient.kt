package com.petmanager.data.remote.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // 백엔드 서버 URL (로컬 개발 시: http://10.0.2.2:8080, 실제 서버: 실제 도메인)
    private const val BASE_URL = "http://10.0.2.2:8080/" // 로컬 개발용 (에뮬레이터)
    // private const val BASE_URL = "https://your-api-server.com/" // 실제 서버 URL
    
    private var retrofit: Retrofit? = null
    
    fun getRetrofitInstance(context: Context): Retrofit {
        if (retrofit == null) {
            // 로깅 Interceptor (개발 시에만 사용)
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY // 요청/응답 로그 출력
            }
            
            // OkHttpClient 설정
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context)) // JWT 토큰 자동 추가
                .addInterceptor(loggingInterceptor) // 로깅
                .connectTimeout(30, TimeUnit.SECONDS) // 연결 타임아웃
                .readTimeout(30, TimeUnit.SECONDS) // 읽기 타임아웃
                .writeTimeout(30, TimeUnit.SECONDS) // 쓰기 타임아웃
                .build()
            
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
    
    // API 서비스 인스턴스 생성
    fun <T> createService(context: Context, serviceClass: Class<T>): T {
        return getRetrofitInstance(context).create(serviceClass)
    }
}

