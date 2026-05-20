package com.petmanager.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.ChatRepository
import com.petmanager.domain.model.ChatInfo
import com.petmanager.presentation.mapper.toChatInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = ChatListUiState.Loading
            chatRepository.getChatRooms(size = 20)
                .onSuccess { slice ->
                    val rooms = slice.content.map { it.toChatInfo() }
                    _uiState.value = ChatListUiState.Success(rooms)
                }
                .onFailure { e ->
                    _uiState.value = ChatListUiState.Error(e.message ?: "채팅 목록을 불러오지 못했습니다.")
                }
        }
    }

    sealed class ChatListUiState {
        object Loading : ChatListUiState()
        data class Success(val rooms: List<ChatInfo>) : ChatListUiState()
        data class Error(val message: String) : ChatListUiState()
    }
}
