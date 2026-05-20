package com.petmanager.data.repository

import android.content.Context
import android.net.Uri
import com.petmanager.data.remote.api.ChatApi
import com.petmanager.data.remote.api.ApiResponse
import com.petmanager.data.remote.api.isApiSuccess
import com.petmanager.data.remote.dto.ChatSliceDto
import com.petmanager.data.remote.dto.FindChatLogsRespDto
import com.petmanager.data.remote.dto.FindChatRoomsRespDto
import com.petmanager.data.remote.dto.JoinChatRoomReqDto
import com.petmanager.data.remote.dto.JoinChatRoomRespDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatApi: ChatApi,
) {

    suspend fun getChatRooms(
        size: Int = 20,
        lastUpdatedAt: String? = null,
        lastRoomId: String? = null,
    ): Result<ChatSliceDto<FindChatRoomsRespDto>> {
        return try {
            val response = chatApi.getChatRooms(size, lastUpdatedAt, lastRoomId)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(body?.value ?: ChatSliceDto())
            } else {
                Result.failure(Exception(body?.message ?: "채팅방 목록 조회 실패 (HTTP ${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinChatRoom(
        feedId: String,
        roomId: String? = null,
        chatRoomName: String? = null,
    ): Result<JoinChatRoomRespDto> {
        return try {
            val response = chatApi.joinChatRoom(
                JoinChatRoomReqDto(
                    feedId = feedId,
                    chatRoomName = chatRoomName?.takeIf { it.isNotBlank() },
                    roomId = roomId?.takeIf { it.isNotBlank() },
                ),
            )
            val body = response.body()
            val value = body?.value
            if (response.isSuccessful && body.isApiSuccess() && value != null) {
                Result.success(value)
            } else {
                Result.failure(mapChatError(body, response.code(), "채팅방 입장 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatLogs(
        roomId: String,
        size: Int = 20,
        lastCreatedAt: String? = null,
        lastId: String? = null,
    ): Result<ChatSliceDto<FindChatLogsRespDto>> {
        return try {
            val response = chatApi.getChatLogs(roomId, size, lastCreatedAt, lastId)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(body?.value ?: ChatSliceDto())
            } else {
                Result.failure(Exception(body?.message ?: "채팅 내역 조회 실패"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChatFiles(
        roomId: String,
        fileUris: List<Uri>,
        message: String? = null,
    ): Result<Unit> {
        if (fileUris.isEmpty()) {
            return Result.failure(IllegalArgumentException("업로드할 파일이 없습니다."))
        }
        if (fileUris.size > MAX_FILES) {
            return Result.failure(IllegalArgumentException("파일은 최대 ${MAX_FILES}개까지 업로드할 수 있습니다."))
        }
        return try {
            val plain = "text/plain; charset=utf-8".toMediaTypeOrNull()
            fun plainBody(s: String): RequestBody = s.toRequestBody(plain)

            val parts = fileUris.mapIndexed { index, uri ->
                filePart("files", uri, "chat_file_$index")
            }
            val messagePart = message?.takeIf { it.isNotBlank() }?.let { plainBody(it) }

            val response = chatApi.uploadChatFiles(
                roomId = plainBody(roomId),
                messageType = plainBody("FILE"),
                message = messagePart,
                files = parts,
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("파일 업로드 실패 (HTTP ${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun filePart(field: String, uri: Uri, fallbackName: String): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("파일을 열 수 없습니다.")
        input.use { stream ->
            val bytes = stream.readBytes()
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val name = queryDisplayName(uri) ?: fallbackName
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

    companion object {
        private const val MAX_FILES = 10
        private const val CHAT_ACCESS_DENIED_CODE = "CHAT_ERR_02"
    }

    private fun mapChatError(body: ApiResponse<*>?, httpCode: Int, fallback: String): Exception {
        val message = body?.message?.takeIf { it.isNotBlank() } ?: fallback
        if (body?.code == CHAT_ACCESS_DENIED_CODE || httpCode == 403 && body?.code == CHAT_ACCESS_DENIED_CODE) {
            return ChatAccessDeniedException(message)
        }
        return Exception(message)
    }
}
