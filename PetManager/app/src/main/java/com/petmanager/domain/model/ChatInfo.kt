package com.petmanager.domain.model

data class ChatInfo(
    val roomId: String = "",
    val feedId: String = "",
    val title: String = "",
    val feedMainImageUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageCreatedAt: String? = null,
    val feedAuthorId: String = "",
    val feedAuthorNickname: String = "",
    val chatRoomName: String? = null,
)
