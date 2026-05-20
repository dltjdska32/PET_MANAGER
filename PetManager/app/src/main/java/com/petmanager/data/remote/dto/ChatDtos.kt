package com.petmanager.data.remote.dto

import com.google.gson.annotations.SerializedName

/** 채팅 모듈 커서 페이지네이션 (module-chat Slice) */
data class ChatSliceDto<T>(
    val content: List<T> = emptyList(),
    val hasNext: Boolean = false,
    val nextCursor: ChatNextCursorDto? = null,
)

data class ChatNextCursorDto(
    val lastId: String,
    val lastCreatedAt: String,
)

data class ChatUserInfoDto(
    val userId: Int,
    val username: String,
    val userNickname: String,
    val userEmail: String,
)

data class FeedInfoDto(
    val feedId: String,
    val authorId: Int,
    val authorUsername: String,
    val authorNickname: String,
    val title: String,
)

enum class MessageType {
    TEXT,
    FILE,
}

data class FileMetaDto(
    val originalName: String,
    val storedName: String,
    val mimeType: String,
    val url: String,
)

data class FindChatLogsRespDto(
    val roomId: String = "",
    @field:SerializedName(value = "logid", alternate = ["logId", "chatLogId"])
    val logid: String? = null,
    val createdAt: String? = null,
    val sender: ChatUserInfoDto,
    val readUserIds: List<Int> = emptyList(),
    val messageType: MessageType,
    val message: String? = null,
    @field:SerializedName(value = "file", alternate = ["files"])
    val file: List<FileMetaDto>? = null,
)

data class FindChatRoomsRespDto(
    val roomId: String,
    @SerializedName(value = "feedId", alternate = ["feed_id"])
    val feedId: String? = null,
    @SerializedName(value = "feedMainImgUrl", alternate = ["feed_main_img_url", "mainImgUrl"])
    val feedMainImgUrl: String? = null,
    val title: String? = null,
    @SerializedName(value = "feedAuthorId", alternate = ["feed_author_id"])
    val feedAuthorId: Int? = null,
    @SerializedName(value = "feedAuthorNickname", alternate = ["feed_author_nickname"])
    val feedAuthorNickname: String? = null,
    val lastMessage: String? = null,
    val lastMessageId: String? = null,
    val lastMessageCreatedAt: String? = null,
    val createdAt: String? = null,
    val roomName: String? = null,
    /** 구 응답(flat 필드 없을 때) 호환 */
    val feedInfo: FeedInfoDto? = null,
)

data class JoinChatRoomReqDto(
    val feedId: String,
    val chatRoomName: String? = null,
    val roomId: String? = null,
)

data class JoinChatRoomRespDto(
    val roomId: String,
    val feedInfo: FeedInfoDto,
    val chatMembers: List<ChatUserInfoDto>,
    val creatorId: Int,
    val chatLogs: ChatSliceDto<FindChatLogsRespDto>? = null,
    val chatRoomName: String? = null,
)

data class SendMessageRespDto(
    @field:SerializedName(value = "chatLogId", alternate = ["logid", "logId"])
    val chatLogId: String? = null,
    val messageType: MessageType,
    val message: String? = null,
    val sender: ChatUserInfoDto,
    val readUserIds: List<Int> = emptyList(),
    @field:SerializedName(value = "files", alternate = ["file"])
    val files: List<FileMetaDto>? = null,
    val isDeleted: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
