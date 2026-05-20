package com.petmanager.data.repository

import android.content.Context
import android.net.Uri
import com.petmanager.data.remote.api.FeedApi
import com.petmanager.data.remote.api.isApiSuccess
import com.petmanager.data.remote.dto.FeedDetailDto
import com.petmanager.data.remote.dto.FeedSliceDto
import com.petmanager.data.remote.dto.FeedSortFlag
import com.petmanager.data.remote.dto.FindFeedRespDto
import com.petmanager.domain.model.FeedType
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedApi: FeedApi,
) {

    suspend fun findFeeds(
        regionId: Long,
        page: Int = 0,
        size: Int = 20,
        feedType: FeedType? = FeedType.COMMUNICATION,
        keyword: String = "",
        sort: FeedSortFlag = FeedSortFlag.CREATED_AT,
        cursorFeedId: String? = null,
        cursorLikesCount: Int? = null,
        cursorCreatedAt: String? = null,
    ): Result<FeedSliceDto<FindFeedRespDto>> {
        return try {
            val typeStr = feedType?.name
            val response = feedApi.findFeeds(
                feedType = typeStr,
                keyword = keyword,
                sort = sort.name,
                regionId = regionId,
                page = page,
                size = size,
                cursorFeedId = cursorFeedId,
                cursorLikesCount = cursorLikesCount,
                cursorCreatedAt = cursorCreatedAt,
            )
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                val slice = body?.value ?: FeedSliceDto()
                Result.success(slice)
            } else {
                Result.failure(Exception(body?.message ?: "피드 목록 조회 실패 (HTTP ${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeedDetail(id: String): Result<FeedDetailDto> {
        return try {
            val response = feedApi.getFeedDetail(id)
            val body = response.body()
            val detail = body?.value
            if (response.isSuccessful && body.isApiSuccess() && detail != null) {
                Result.success(detail)
            } else {
                Result.failure(Exception(body?.message ?: "피드 상세 조회 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFeed(id: String): Result<Unit> {
        return try {
            val response = feedApi.deleteFeed(id)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.message ?: "피드 삭제 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(id: String): Result<Unit> {
        return try {
            val response = feedApi.toggleLike(id)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.message ?: "좋아요 처리 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findMyLikedFeeds(
        page: Int = 0,
        size: Int = 20,
        lastCreatedAt: String? = null,
        lastId: String? = null,
    ): Result<FeedSliceDto<FindFeedRespDto>> {
        return try {
            val response = feedApi.findMyLikedFeeds(page, size, lastCreatedAt, lastId)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(body?.value ?: FeedSliceDto())
            } else {
                Result.failure(Exception(body?.message ?: "좋아요 목록 조회 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findMyFeeds(
        page: Int = 0,
        size: Int = 20,
    ): Result<FeedSliceDto<FindFeedRespDto>> {
        return try {
            val response = feedApi.findMyFeeds(page, size)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(body?.value ?: FeedSliceDto())
            } else {
                Result.failure(Exception(body?.message ?: "내 게시글 목록 조회 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertFeed(
        userNickname: String,
        title: String,
        feedType: FeedType,
        description: String,
        pay: Int,
        regionId: Long,
        startDate: String?,
        endDate: String?,
        mainImageUris: List<Uri>,
        sideImageUris: List<Uri> = emptyList(),
        feedId: String? = null,
    ): Result<Unit> {
        return try {
            val plain = "text/plain; charset=utf-8".toMediaTypeOrNull()
            fun plainBody(s: String): RequestBody = s.toRequestBody(plain)

            val idPart: RequestBody? = feedId?.let { plainBody(it) }

            val mainParts = mainImageUris.mapIndexed { index, uri ->
                filePart("mainImgs", uri, "main_$index")
            }
            val sideParts = sideImageUris.mapIndexed { index, uri ->
                filePart("sideImgs", uri, "side_$index")
            }

            val response = feedApi.upsertFeed(
                feedId = idPart,
                userNickname = plainBody(userNickname),
                mainImgs = mainParts,
                sideImgs = sideParts,
                title = plainBody(title),
                feedType = plainBody(feedType.name),
                description = plainBody(description),
                startDate = startDate?.let { plainBody(it) },
                endDate = endDate?.let { plainBody(it) },
                pay = plainBody(pay.toString()),
                regionId = plainBody(regionId.toString()),
            )
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.message ?: "피드 등록·수정 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun filePart(field: String, uri: Uri, fallbackName: String): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("이미지를 열 수 없습니다.")
        input.use { stream ->
            val bytes = stream.readBytes()
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val name = queryDisplayName(uri) ?: "$fallbackName.jpg"
            return MultipartBody.Part.createFormData(field, name, body)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            return c.getString(idx)
        }
    }
}
