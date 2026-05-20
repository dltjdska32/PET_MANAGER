package com.petmanager.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petmanager.data.remote.dto.ChatNextCursorDto
import com.petmanager.data.remote.socket.ChatSocketManager
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.ChatAccessDeniedException
import com.petmanager.data.repository.ChatRepository
import com.petmanager.domain.model.Message
import com.petmanager.presentation.mapper.toMessage
import com.petmanager.presentation.mapper.toMessagesChronological
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val chatSocketManager: ChatSocketManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatRoomUiState>(ChatRoomUiState.Loading)
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private var currentRoomId: String? = null
    private var olderCursor: ChatNextCursorDto? = null
    private var hasMoreOlder = false

    val currentUserId: String?
        get() = authRepository.getCachedUserInfo()?.id

    init {
        viewModelScope.launch {
            chatSocketManager.newMessages.collect { incoming ->
                val roomId = currentRoomId ?: return@collect
                val state = _uiState.value
                if (state !is ChatRoomUiState.Ready || state.roomId != roomId) return@collect
                if (incoming.roomId.isNotBlank() && incoming.roomId != roomId) return@collect
                val message = incoming.toMessage() ?: return@collect
                if (message.logId.isBlank()) return@collect
                if (state.messages.any { it.logId == message.logId }) return@collect
                _uiState.update {
                    if (it is ChatRoomUiState.Ready) {
                        it.copy(
                            messages = it.messages + message,
                            listUpdate = ChatListUpdate.Append,
                        )
                    } else {
                        it
                    }
                }
            }
        }
        viewModelScope.launch {
            chatSocketManager.connectionErrors.collect { msg ->
                val state = _uiState.value
                if (state is ChatRoomUiState.Ready) {
                    _uiState.value = state.copy(errorMessage = msg)
                }
            }
        }
    }

    fun enterRoom(
        feedId: String,
        roomId: String?,
        chatRoomName: String?,
        displayTitle: String,
    ) {
        viewModelScope.launch {
            _uiState.value = ChatRoomUiState.Loading
            val resolvedFeedId = feedId.trim()
            if (resolvedFeedId.isEmpty()) {
                _uiState.value = ChatRoomUiState.Error("게시글 정보가 없어 채팅방에 입장할 수 없습니다.")
                return@launch
            }
            chatRepository.joinChatRoom(
                feedId = resolvedFeedId,
                roomId = roomId?.trim()?.takeIf { it.isNotEmpty() },
                chatRoomName = chatRoomName?.trim()?.takeIf { it.isNotEmpty() },
            )
                .onSuccess { join ->
                    currentRoomId = join.roomId
                    val logs = join.chatLogs
                    val messages = logs?.content.orEmpty().toMessagesChronological()
                    olderCursor = logs?.nextCursor
                    hasMoreOlder = logs?.hasNext == true
                    val displayTitleResolved = displayTitle.ifBlank {
                        join.chatRoomName?.takeIf { it.isNotBlank() }
                            ?: join.feedInfo.title
                    }
                    _uiState.value = ChatRoomUiState.Ready(
                        roomId = join.roomId,
                        title = displayTitleResolved,
                        feedId = join.feedInfo.feedId,
                        messages = messages,
                        hasMoreOlder = hasMoreOlder,
                        listUpdate = ChatListUpdate.Initial,
                    )
                    chatSocketManager.connect()
                    chatSocketManager.joinRoom(join.roomId)
                }
                .onFailure { e ->
                    _uiState.value = when (e) {
                        is ChatAccessDeniedException -> ChatRoomUiState.AccessDenied(
                            e.message ?: "채팅방에 접근할 수 없습니다.",
                        )
                        else -> ChatRoomUiState.Error(e.message ?: "채팅방 입장에 실패했습니다.")
                    }
                }
        }
    }

    fun loadOlderMessages() {
        val roomId = currentRoomId ?: return
        val state = _uiState.value as? ChatRoomUiState.Ready ?: return
        if (!hasMoreOlder || state.isLoadingOlder) return

        val oldest = state.messages.firstOrNull()
        val cursorCreatedAt = oldest?.createdAt ?: olderCursor?.lastCreatedAt
        val cursorId = oldest?.logId ?: olderCursor?.lastId
        if (cursorCreatedAt.isNullOrBlank() || cursorId.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update {
                if (it is ChatRoomUiState.Ready) it.copy(isLoadingOlder = true) else it
            }
            chatRepository.getChatLogs(
                roomId = roomId,
                size = PAGE_SIZE,
                lastCreatedAt = cursorCreatedAt,
                lastId = cursorId,
            ).onSuccess { slice ->
                val older = slice.content.toMessagesChronological()
                val existingIds = state.messages.map { it.logId }.toSet()
                val uniqueOlder = older.filter { it.logId !in existingIds }
                olderCursor = slice.nextCursor
                hasMoreOlder = slice.hasNext && uniqueOlder.isNotEmpty()
                _uiState.update {
                    if (it is ChatRoomUiState.Ready) {
                        it.copy(
                            messages = if (uniqueOlder.isEmpty()) it.messages else uniqueOlder + it.messages,
                            hasMoreOlder = hasMoreOlder,
                            isLoadingOlder = false,
                            listUpdate = if (uniqueOlder.isEmpty()) {
                                ChatListUpdate.None
                            } else {
                                ChatListUpdate.Prepend(uniqueOlder.size)
                            },
                        )
                    } else {
                        it
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    if (it is ChatRoomUiState.Ready) {
                        it.copy(
                            isLoadingOlder = false,
                            errorMessage = e.message ?: "이전 메시지를 불러오지 못했습니다.",
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val roomId = currentRoomId ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        chatSocketManager.sendTextMessage(roomId, trimmed)
    }

    fun uploadFiles(uris: List<Uri>, caption: String? = null) {
        val roomId = currentRoomId ?: return
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                if (it is ChatRoomUiState.Ready) it.copy(isUploading = true) else it
            }
            chatRepository.uploadChatFiles(roomId, uris, message = caption)
                .onFailure { e ->
                    _uiState.update {
                        if (it is ChatRoomUiState.Ready) {
                            it.copy(errorMessage = e.message ?: "파일 업로드에 실패했습니다.")
                        } else {
                            it
                        }
                    }
                }
            _uiState.update {
                if (it is ChatRoomUiState.Ready) it.copy(isUploading = false) else it
            }
        }
    }

    fun clearListUpdate() {
        _uiState.update {
            if (it is ChatRoomUiState.Ready && it.listUpdate != ChatListUpdate.None) {
                it.copy(listUpdate = ChatListUpdate.None)
            } else {
                it
            }
        }
    }

    /** 사용자가 [나가기] 버튼을 눌렀을 때만 leave-room WS 이벤트 발행 */
    fun leaveChatRoomExplicit(onResult: (Result<Unit>) -> Unit) {
        val roomId = currentRoomId
        if (roomId.isNullOrBlank()) {
            onResult(Result.success(Unit))
            return
        }
        _uiState.update {
            if (it is ChatRoomUiState.Ready) it.copy(isLeaving = true) else it
        }
        chatSocketManager.leaveRoomExplicit(roomId) { result ->
            result.onSuccess { currentRoomId = null }
            _uiState.update {
                if (it is ChatRoomUiState.Ready) it.copy(isLeaving = false) else it
            }
            onResult(result)
        }
    }

    override fun onCleared() {
        // 뒤로가기·화면 종료 시 leave-room 요청 금지
        super.onCleared()
    }

    sealed class ChatRoomUiState {
        object Loading : ChatRoomUiState()
        data class Ready(
            val roomId: String,
            val title: String,
            val feedId: String,
            val messages: List<Message> = emptyList(),
            val hasMoreOlder: Boolean = false,
            val isLoadingOlder: Boolean = false,
            val isUploading: Boolean = false,
            val isLeaving: Boolean = false,
            val errorMessage: String? = null,
            val listUpdate: ChatListUpdate = ChatListUpdate.None,
        ) : ChatRoomUiState()
        data class Error(val message: String) : ChatRoomUiState()
        data class AccessDenied(val message: String) : ChatRoomUiState()
    }

    sealed class ChatListUpdate {
        object None : ChatListUpdate()
        object Initial : ChatListUpdate()
        object Append : ChatListUpdate()
        data class Prepend(val count: Int) : ChatListUpdate()
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
