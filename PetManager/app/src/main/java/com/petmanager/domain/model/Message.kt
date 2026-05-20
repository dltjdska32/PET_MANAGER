package com.petmanager.domain.model

enum class ChatMessageType {
    TEXT,
    FILE,
}

data class Message(
    var logId: String = "",
    var message: String? = null,
    var sendId: String? = null,
    var senderNickname: String? = null,
    var createdAt: String? = null,
    var messageType: ChatMessageType = ChatMessageType.TEXT,
    var fileUrls: List<String> = emptyList(),
) {
    constructor() : this("", "", "", null, null, ChatMessageType.TEXT, emptyList())

    val isImageFile: Boolean
        get() = messageType == ChatMessageType.FILE &&
            fileUrls.any { url ->
                val lower = url.substringAfterLast('.', "").lowercase()
                lower in IMAGE_EXTENSIONS
            }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
}

