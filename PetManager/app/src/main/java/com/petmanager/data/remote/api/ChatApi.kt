package com.petmanager.data.remote.api

import com.petmanager.data.remote.dto.ChatSliceDto
import com.petmanager.data.remote.dto.FindChatLogsRespDto
import com.petmanager.data.remote.dto.FindChatRoomsRespDto
import com.petmanager.data.remote.dto.JoinChatRoomReqDto
import com.petmanager.data.remote.dto.JoinChatRoomRespDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ChatApi {

    @GET("api/chat/logs")
    suspend fun getChatLogs(
        @Query("roomId") roomId: String,
        @Query("size") size: Int,
        @Query("lastCreatedAt") lastCreatedAt: String? = null,
        @Query("lastId") lastId: String? = null,
    ): Response<ApiResponse<ChatSliceDto<FindChatLogsRespDto>>>

    @POST("api/chat/join")
    suspend fun joinChatRoom(
        @Body body: JoinChatRoomReqDto,
    ): Response<ApiResponse<JoinChatRoomRespDto>>

    @GET("api/chat/rooms")
    suspend fun getChatRooms(
        @Query("size") size: Int,
        @Query("lastUpdatedAt") lastUpdatedAt: String? = null,
        @Query("lastRoomId") lastRoomId: String? = null,
    ): Response<ApiResponse<ChatSliceDto<FindChatRoomsRespDto>>>

    @Multipart
    @POST("api/chat/files/upload")
    suspend fun uploadChatFiles(
        @Part("roomId") roomId: RequestBody,
        @Part("messageType") messageType: RequestBody,
        @Part("message") message: RequestBody?,
        @Part files: List<MultipartBody.Part>,
    ): Response<Unit>
}
