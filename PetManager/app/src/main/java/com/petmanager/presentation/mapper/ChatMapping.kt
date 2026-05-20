package com.petmanager.presentation.mapper

import com.google.gson.Gson
import com.petmanager.data.remote.dto.FileMetaDto
import com.petmanager.data.remote.dto.FindChatLogsRespDto
import com.petmanager.data.remote.dto.FindChatRoomsRespDto
import com.petmanager.data.remote.dto.MessageType
import com.petmanager.data.remote.dto.SendMessageRespDto
import com.petmanager.domain.model.ChatInfo
import com.petmanager.domain.model.ChatMessageType
import com.petmanager.domain.model.Message
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun FindChatRoomsRespDto.toChatInfo(): ChatInfo {
    val legacy = feedInfo
    return ChatInfo(
        roomId = roomId,
        feedId = (feedId?.trim()?.takeIf { it.isNotEmpty() }
            ?: legacy?.feedId?.trim()?.takeIf { it.isNotEmpty() })
            .orEmpty(),
        title = (title?.trim()?.takeIf { it.isNotEmpty() }
            ?: legacy?.title?.trim()?.takeIf { it.isNotEmpty() })
            .orEmpty(),
        feedMainImageUrl = feedMainImgUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizeImageUrl(it) },
        lastMessage = lastMessage?.trim()?.takeIf { it.isNotEmpty() },
        lastMessageCreatedAt = lastMessageCreatedAt,
        feedAuthorId = (feedAuthorId ?: legacy?.authorId)?.toString().orEmpty(),
        feedAuthorNickname = (feedAuthorNickname?.trim()?.takeIf { it.isNotEmpty() }
            ?: legacy?.authorNickname?.trim()?.takeIf { it.isNotEmpty() })
            .orEmpty(),
        chatRoomName = roomName?.trim()?.takeIf { it.isNotEmpty() },
    )
}

fun formatChatMessageTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = parseChatDate(iso) ?: return ""
    return SimpleDateFormat("a h:mm", Locale.KOREA).format(date)
}

/** 채팅방 날짜 구분선 — 오늘 / 어제 / yyyy년 M월 d일 */
fun formatChatDateDivider(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = parseChatDate(iso) ?: return ""
    val messageCal = Calendar.getInstance().apply { time = date }
    val today = Calendar.getInstance()
    return when {
        isSameDay(messageCal, today) -> "오늘"
        isYesterday(messageCal, today) -> "어제"
        else -> SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(date)
    }
}

fun chatDateKey(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val date = parseChatDate(iso) ?: return null
    return SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(date)
}

private fun parseChatDate(iso: String): java.util.Date? {
    return try {
        val cleaned = iso.replace("Z", "").substringBefore(".")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA)
        parser.timeZone = TimeZone.getDefault()
        parser.parse(cleaned.take(19))
    } catch (_: Exception) {
        null
    }
}

/** 채팅 목록 우측 시간 — 오늘은 시각, 어제·그 이전은 날짜 */
fun formatChatListTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = parseChatDate(iso) ?: return ""
    val messageCal = Calendar.getInstance().apply { time = date }
    val today = Calendar.getInstance()
    return when {
        isSameDay(messageCal, today) -> formatChatMessageTime(iso)
        isYesterday(messageCal, today) -> "어제"
        messageCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
            SimpleDateFormat("M월 d일", Locale.KOREA).format(date)
        }
        else -> SimpleDateFormat("yy.M.d", Locale.KOREA).format(date)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(message: Calendar, today: Calendar): Boolean {
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(message, yesterday)
}

private fun fileUrlsFrom(meta: List<FileMetaDto>?): List<String> =
    meta.orEmpty()
        .mapNotNull { it.url.trim().takeIf { url -> url.isNotBlank() } }
        .map { normalizeImageUrl(it) }

private fun displayText(type: MessageType, message: String?): String {
    return when (type) {
        MessageType.TEXT -> message.orEmpty()
        MessageType.FILE -> ""
    }
}

private fun resolveLogId(vararg candidates: String?): String? =
    candidates.firstNotNullOfOrNull { candidate ->
        candidate?.trim()?.takeIf { it.isNotEmpty() }
    }

private val LOG_ID_JSON_KEYS = listOf("logid", "logId", "chatLogId")

/** WS new-message — FindChatLogsRespDto 우선, SendMessageRespDto·수동 logId 보정 fallback */
fun Gson.parseIncomingChatLog(json: String): FindChatLogsRespDto? {
    runCatching { fromJson(json, FindChatLogsRespDto::class.java) }.getOrNull()
        ?.takeIf { !it.logid.isNullOrBlank() }
        ?.let { return it }

    runCatching { fromJson(json, SendMessageRespDto::class.java) }.getOrNull()
        ?.let { send -> return send.toFindChatLogsRespDto() }

    return runCatching {
        val obj = JSONObject(json)
        val logId = LOG_ID_JSON_KEYS.firstNotNullOfOrNull { key ->
            obj.optString(key, null)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        } ?: return null
        val patched = JSONObject(json)
        if (!patched.has("logid")) {
            patched.put("logid", logId)
        }
        fromJson(patched.toString(), FindChatLogsRespDto::class.java)
            ?.takeIf { !it.logid.isNullOrBlank() }
    }.getOrNull()
}

private fun SendMessageRespDto.toFindChatLogsRespDto(): FindChatLogsRespDto? {
    val id = resolveLogId(chatLogId) ?: return null
    return FindChatLogsRespDto(
        roomId = "",
        logid = id,
        createdAt = createdAt,
        sender = sender,
        readUserIds = readUserIds,
        messageType = messageType,
        message = message,
        file = files,
    )
}

fun FindChatLogsRespDto.toMessage(): Message? {
    val id = resolveLogId(logid) ?: return null
    return Message(
        logId = id,
        message = displayText(messageType, message),
        sendId = sender.userId.toString(),
        senderNickname = sender.userNickname.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        messageType = when (messageType) {
            MessageType.TEXT -> ChatMessageType.TEXT
            MessageType.FILE -> ChatMessageType.FILE
        },
        fileUrls = if (messageType == MessageType.FILE) fileUrlsFrom(file) else emptyList(),
    )
}

fun SendMessageRespDto.toMessage(): Message? {
    val id = resolveLogId(chatLogId) ?: return null
    return Message(
        logId = id,
        message = displayText(messageType, message),
        sendId = sender.userId.toString(),
        senderNickname = sender.userNickname.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        messageType = when (messageType) {
            MessageType.TEXT -> ChatMessageType.TEXT
            MessageType.FILE -> ChatMessageType.FILE
        },
        fileUrls = if (messageType == MessageType.FILE) fileUrlsFrom(files) else emptyList(),
    )
}

/** 서버는 최신순으로 내려주므로 UI(오래된→최신)용으로 뒤집음 */
fun List<FindChatLogsRespDto>.toMessagesChronological(): List<Message> =
    mapNotNull { it.toMessage() }.asReversed()
