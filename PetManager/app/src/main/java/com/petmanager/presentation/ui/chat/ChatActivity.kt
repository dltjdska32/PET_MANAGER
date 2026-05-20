package com.petmanager.presentation.ui.chat

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.petmanager.databinding.ActivityChatBinding
import com.petmanager.domain.model.Message
import com.petmanager.presentation.adapter.MessageAdapter
import com.petmanager.presentation.viewmodel.ChatRoomViewModel
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatRoomViewModel by viewModels()
    private val messageList = arrayListOf<Message>()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var isLoadingOlderScroll = false

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        if (uris.size > MAX_ATTACH_FILES) {
            Toast.makeText(this, "파일은 최대 ${MAX_ATTACH_FILES}개까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
            viewModel.uploadFiles(uris.take(MAX_ATTACH_FILES))
        } else {
            viewModel.uploadFiles(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val feedId = intent.getStringExtra(EXTRA_FEED_ID).orEmpty()
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        val chatRoomName = intent.getStringExtra(EXTRA_CHAT_ROOM_NAME)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        if (feedId.isBlank()) {
            Toast.makeText(this, "잘못된 채팅 요청입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        messageAdapter = MessageAdapter(
            messageList = messageList,
            currentUserId = viewModel.currentUserId.orEmpty(),
        )
        layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatRecyclerView.layoutManager = layoutManager
        binding.chatRecyclerView.adapter = messageAdapter
        setupScrollListener()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnLeave.setOnClickListener { confirmLeaveRoom() }
        binding.btnAttach.setOnClickListener { pickFiles.launch("*/*") }

        setupSendButton()
        viewModel.currentUserId?.let { messageAdapter.updateCurrentUserId(it) }

        viewModel.enterRoom(
            feedId = feedId,
            roomId = roomId,
            chatRoomName = chatRoomName,
            displayTitle = title,
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ChatRoomViewModel.ChatRoomUiState.Loading -> {
                            binding.chatTitle.text = title
                        }
                        is ChatRoomViewModel.ChatRoomUiState.Ready -> {
                            binding.chatTitle.text = state.title
                            viewModel.currentUserId?.let { messageAdapter.updateCurrentUserId(it) }
                            binding.loadingOlderProgress.isVisible = state.isLoadingOlder
                            binding.uploadProgress.isVisible = state.isUploading
                            binding.btnAttach.isEnabled = !state.isUploading && !state.isLeaving
                            updateSendButtonEnabled(!state.isUploading && !state.isLeaving)
                            binding.btnLeave.isEnabled = !state.isLeaving
                            binding.btnLeave.text = if (state.isLeaving) "나가는 중..." else "나가기"
                            applyMessages(state.messages, state.listUpdate)
                            state.errorMessage?.let { msg ->
                                Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_SHORT).show()
                            }
                            viewModel.clearListUpdate()
                        }
                        is ChatRoomViewModel.ChatRoomUiState.AccessDenied -> {
                            Toast.makeText(this@ChatActivity, state.message, Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is ChatRoomViewModel.ChatRoomUiState.Error -> {
                            Toast.makeText(this@ChatActivity, state.message, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    private var sendActionsEnabled = true

    private fun setupSendButton() {
        binding.messageEdit.doAfterTextChanged {
            updateSendButtonEnabled(sendActionsEnabled)
        }

        binding.sendBtn.setOnTouchListener { view, event ->
            if (!view.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }

        binding.sendBtn.setOnClickListener {
            val text = binding.messageEdit.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            binding.sendBtn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.sendMessage(text)
            binding.messageEdit.setText("")
        }
    }

    private fun updateSendButtonEnabled(actionsEnabled: Boolean) {
        sendActionsEnabled = actionsEnabled
        val hasText = binding.messageEdit.text?.toString()?.trim()?.isNotEmpty() == true
        val enabled = actionsEnabled && hasText
        binding.sendBtn.isEnabled = enabled
        binding.sendBtn.alpha = if (enabled) 1f else 0.42f
    }

    private fun setupScrollListener() {
        binding.chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy >= 0 || isLoadingOlderScroll) return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (firstVisible <= LOAD_MORE_THRESHOLD) {
                    viewModel.loadOlderMessages()
                }
            }
        })
    }

    private fun applyMessages(messages: List<Message>, update: ChatRoomViewModel.ChatListUpdate) {
        when (update) {
            is ChatRoomViewModel.ChatListUpdate.Prepend -> {
                if (update.count <= 0) return
                isLoadingOlderScroll = true
                val firstVisible = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
                val topView = layoutManager.findViewByPosition(firstVisible)
                val offset = topView?.top ?: 0
                val oldItemCount = messageAdapter.itemCount

                val newIds = messages.map { it.logId }
                if (messageList.map { it.logId } != newIds) {
                    messageList.clear()
                    messageList.addAll(messages)
                    messageAdapter.refresh()
                    val addedRows = (messageAdapter.itemCount - oldItemCount).coerceAtLeast(0)
                    layoutManager.scrollToPositionWithOffset(firstVisible + addedRows, offset)
                }
                isLoadingOlderScroll = false
            }
            ChatRoomViewModel.ChatListUpdate.Append -> {
                messageList.clear()
                messageList.addAll(messages)
                messageAdapter.refresh()
                scrollToBottom()
            }
            ChatRoomViewModel.ChatListUpdate.Initial -> {
                messageList.clear()
                messageList.addAll(messages)
                messageAdapter.refresh()
                scrollToBottom()
            }
            ChatRoomViewModel.ChatListUpdate.None -> {
                val newIds = messages.map { it.logId }
                if (messageList.map { it.logId } != newIds) {
                    messageList.clear()
                    messageList.addAll(messages)
                    messageAdapter.refresh()
                }
            }
        }
    }

    private fun scrollToBottom() {
        if (messageAdapter.itemCount <= 0) return
        binding.chatRecyclerView.post {
            binding.chatRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
        }
    }

    private fun confirmLeaveRoom() {
        AlertDialog.Builder(this)
            .setTitle("채팅방 나가기")
            .setMessage("채팅방을 나가시겠습니까?\n다시 참여하려면 게시글에서 채팅을 시작해야 합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("나가기") { _, _ ->
                viewModel.leaveChatRoomExplicit { result ->
                    result.onSuccess {
                        Toast.makeText(this, "채팅방을 나갔습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }.onFailure { e ->
                        Toast.makeText(
                            this,
                            e.message ?: "채팅방 나가기에 실패했습니다.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_FEED_ID = "feedId"
        const val EXTRA_ROOM_ID = "roomId"
        const val EXTRA_CHAT_ROOM_NAME = "chatRoomName"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HOST_ID = "hostId"

        private const val LOAD_MORE_THRESHOLD = 2
        private const val MAX_ATTACH_FILES = 10
    }
}
