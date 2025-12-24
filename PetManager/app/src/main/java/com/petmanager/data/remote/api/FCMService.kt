package com.petmanager.data.remote.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface FCMService {
    @Headers(
        "Authorization: key=268970e3fe3a79df0ed3a378f72a2d821a9700cf",
        "Content-Type: application/json"
    )
    @POST("fcm/send")
    fun sendNotification(@Body notification: FCMNotification): Call<FCMResponse>
}

data class FCMNotification(val to: String, val notification: NotificationData)
data class NotificationData(val title: String, val body: String)
data class FCMResponse(val success: Int, val failure: Int)

