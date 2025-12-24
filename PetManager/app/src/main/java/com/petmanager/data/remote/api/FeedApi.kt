package com.petmanager.data.remote.api

import com.petmanager.domain.model.PostInfo
import retrofit2.Response
import retrofit2.http.*

interface FeedApi {
    @GET("api/feed/posts")
    suspend fun getPosts(
        @Query("location") location: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<List<PostInfo>>
    
    @GET("api/feed/posts/{postId}")
    suspend fun getPost(@Path("postId") postId: String): Response<PostInfo>
    
    @POST("api/feed/posts")
    suspend fun createPost(@Body post: PostInfo): Response<PostInfo>
    
    @PUT("api/feed/posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: String,
        @Body post: PostInfo
    ): Response<PostInfo>
    
    @DELETE("api/feed/posts/{postId}")
    suspend fun deletePost(@Path("postId") postId: String): Response<Unit>
}

