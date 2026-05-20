package com.petmanager.data.remote.api

import com.petmanager.data.remote.dto.FeedDetailDto
import com.petmanager.data.remote.dto.FeedSliceDto
import com.petmanager.data.remote.dto.FindFeedRespDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface FeedApi {

    @GET("api/feed")
    suspend fun findFeeds(
        @Query("feedType") feedType: String?,
        @Query("keyword") keyword: String,
        @Query("sort") sort: String,
        @Query("regionId") regionId: Long,
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
        @Query("cursor.feedId") cursorFeedId: String? = null,
        @Query("cursor.likesCount") cursorLikesCount: Int? = null,
        @Query("cursor.createdAt") cursorCreatedAt: String? = null,
    ): Response<ApiResponse<FeedSliceDto<FindFeedRespDto>>>

    @GET("api/feed/{id}")
    suspend fun getFeedDetail(@Path("id") id: String): Response<ApiResponse<FeedDetailDto>>

    @Multipart
    @POST("api/feed")
    suspend fun upsertFeed(
        @Part("feedId") feedId: RequestBody?,
        @Part("userNickname") userNickname: RequestBody,
        @Part mainImgs: List<MultipartBody.Part>,
        @Part sideImgs: List<MultipartBody.Part>,
        @Part("title") title: RequestBody,
        @Part("feedType") feedType: RequestBody,
        @Part("description") description: RequestBody,
        @Part("startDate") startDate: RequestBody?,
        @Part("endDate") endDate: RequestBody?,
        @Part("pay") pay: RequestBody,
        @Part("regionId") regionId: RequestBody,
    ): Response<ApiResponse<Unit>>

    @DELETE("api/feed/{id}")
    suspend fun deleteFeed(@Path("id") id: String): Response<ApiResponse<Unit>>

    @POST("api/feed/likes/{id}")
    suspend fun toggleLike(@Path("id") id: String): Response<ApiResponse<Unit>>

    @GET("api/feed/likes/me")
    suspend fun findMyLikedFeeds(
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
        @Query("lastCreatedAt") lastCreatedAt: String? = null,
        @Query("lastId") lastId: String? = null,
    ): Response<ApiResponse<FeedSliceDto<FindFeedRespDto>>>

    /** 내 게시글 — 컨트롤러 `@GetMapping("/me")` + 클래스 매핑 `api/feed` → `GET api/feed/me` */
    @GET("api/feed/me")
    suspend fun findMyFeeds(
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<FeedSliceDto<FindFeedRespDto>>>
}
