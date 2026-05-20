package com.petmanager.presentation.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.petmanager.domain.model.ChatMessageType
import com.petmanager.domain.model.Message
import com.petmanager.presentation.mapper.formatChatDateDivider
import com.petmanager.presentation.mapper.formatChatMessageTime
import com.petmanager.presentation.mapper.chatDateKey
import com.petmanager.R

class MessageAdapter(
    private val messageList: ArrayList<Message>,
    currentUserId: String,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var currentUserId: String = currentUserId

    private sealed class ChatRow {
        data class DateDivider(val label: String) : ChatRow()
        data class MessageItem(val message: Message, val isOutgoing: Boolean) : ChatRow()
    }

    private var rows: List<ChatRow> = emptyList()

    fun updateCurrentUserId(userId: String) {
        val normalized = userId.trim()
        if (normalized == currentUserId) return
        currentUserId = normalized
        refresh()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_DATE -> DateViewHolder(
                inflater.inflate(R.layout.item_chat_date_divider, parent, false),
            )
            VIEW_SEND -> SendViewHolder(
                inflater.inflate(R.layout.send, parent, false),
            )
            else -> ReceiveViewHolder(
                inflater.inflate(R.layout.receive, parent, false),
            )
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is ChatRow.DateDivider -> VIEW_DATE
            is ChatRow.MessageItem -> {
                if ((rows[position] as ChatRow.MessageItem).isOutgoing) VIEW_SEND else VIEW_RECEIVE
            }
        }
    }

    init {
        rebuildRows()
    }

    fun refresh() {
        rebuildRows()
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ChatRow.DateDivider -> {
                (holder as DateViewHolder).label.text = row.label
            }
            is ChatRow.MessageItem -> {
                val showSenderName = shouldShowSenderName(position, row)
                when (holder) {
                    is SendViewHolder -> bindSend(holder, row.message)
                    is ReceiveViewHolder -> bindReceive(holder, row.message, showSenderName)
                }
            }
        }
    }

    private fun rebuildRows() {
        val built = mutableListOf<ChatRow>()
        var lastDateKey: String? = null
        messageList.forEach { message ->
            val dateKey = chatDateKey(message.createdAt)
            if (dateKey != null && dateKey != lastDateKey) {
                built.add(ChatRow.DateDivider(formatChatDateDivider(message.createdAt)))
                lastDateKey = dateKey
            }
            built.add(ChatRow.MessageItem(message, isOutgoingMessage(message)))
        }
        rows = built
    }

    private fun shouldShowSenderName(position: Int, row: ChatRow.MessageItem): Boolean {
        if (row.isOutgoing) return false
        val nickname = row.message.senderNickname?.trim().orEmpty()
        if (nickname.isEmpty()) return false

        for (index in position - 1 downTo 0) {
            when (val prev = rows[index]) {
                is ChatRow.DateDivider -> return true
                is ChatRow.MessageItem -> {
                    if (prev.isOutgoing) return true
                    val prevNick = prev.message.senderNickname?.trim().orEmpty()
                    val prevSender = prev.message.sendId?.trim().orEmpty()
                    val curSender = row.message.sendId?.trim().orEmpty()
                    return prevNick != nickname || prevSender != curSender
                }
            }
        }
        return true
    }

    private fun isOutgoingMessage(message: Message): Boolean {
        val mine = currentUserId.trim()
        val sender = message.sendId?.trim().orEmpty()
        if (mine.isEmpty() || sender.isEmpty()) return false
        if (mine == sender) return true
        val mineId = mine.toLongOrNull()
        val senderId = sender.toLongOrNull()
        return mineId != null && mineId == senderId
    }

    private fun bindSend(holder: SendViewHolder, message: Message) {
        bindMessageContent(holder.sendMessage, holder.messageImage, message)
        val time = formatChatMessageTime(message.createdAt)
        holder.sendTime.text = time
        holder.sendTime.isVisible = time.isNotBlank()
    }

    private fun bindReceive(holder: ReceiveViewHolder, message: Message, showSenderName: Boolean) {
        bindMessageContent(holder.receiveMessage, holder.messageImage, message)
        val nickname = message.senderNickname?.trim().orEmpty()
        if (showSenderName && nickname.isNotEmpty()) {
            holder.senderName.text = nickname
            holder.senderName.isVisible = true
        } else {
            holder.senderName.isVisible = false
        }
        val time = formatChatMessageTime(message.createdAt)
        holder.receiveTime.text = time
        holder.receiveTime.isVisible = time.isNotBlank()
    }

    private fun bindMessageContent(textView: TextView, imageView: ImageView, message: Message) {
        val fileUrl = message.fileUrls.firstOrNull()?.takeIf { it.isNotBlank() }
        val isFileMessage = message.messageType == ChatMessageType.FILE

        if (isFileMessage && fileUrl != null) {
            textView.isVisible = false
            imageView.isVisible = true
            imageView.load(fileUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_paw)
                error(R.drawable.ic_paw)
            }
            imageView.setOnClickListener {
                val ctx = imageView.context
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
                ctx.startActivity(Intent.createChooser(intent, "이미지 보기"))
            }
            return
        }

        imageView.isVisible = false
        imageView.setOnClickListener(null)
        textView.isVisible = true
        textView.text = message.message.orEmpty()
    }

    class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.date_divider_text)
    }

    class SendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sendMessage: TextView = itemView.findViewById(R.id.send_message_text)
        val messageImage: ImageView = itemView.findViewById(R.id.message_image)
        val sendTime: TextView = itemView.findViewById(R.id.send_time_text)
    }

    class ReceiveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.sender_name_text)
        val receiveMessage: TextView = itemView.findViewById(R.id.receive_message_text)
        val messageImage: ImageView = itemView.findViewById(R.id.message_image)
        val receiveTime: TextView = itemView.findViewById(R.id.receive_time_text)
    }

    companion object {
        private const val VIEW_DATE = 0
        private const val VIEW_SEND = 1
        private const val VIEW_RECEIVE = 2
    }
}
