package com.petmanager.presentation.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.petmanager.domain.model.ChatInfo
import com.petmanager.presentation.mapper.formatChatListTime
import com.petmanager.presentation.ui.chat.ChatActivity
import com.petmanager.R

class ChatAdapter(private val chatList: List<ChatInfo>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_list, parent, false)
        return ChatViewHolder(view)
    }

    override fun getItemCount(): Int = chatList.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chatList[position]
        holder.title.text = chat.title

        val preview = chat.lastMessage
        if (preview.isNullOrBlank()) {
            holder.lastMessage.text = "아직 대화가 없어요"
            holder.lastMessage.setTextColor(holder.itemView.context.getColor(R.color.text_hint))
        } else {
            holder.lastMessage.text = preview
            holder.lastMessage.setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
        }

        val timeLabel = formatChatListTime(chat.lastMessageCreatedAt)
        holder.lastMessageTime.text = timeLabel
        holder.lastMessageTime.isVisible = timeLabel.isNotBlank()

        bindThumbnail(holder.feedThumbnail, chat.feedMainImageUrl)

        holder.itemView.setOnClickListener {
            if (chat.feedId.isBlank()) {
                Toast.makeText(
                    holder.itemView.context,
                    "게시글 정보를 불러올 수 없어 채팅방에 입장할 수 없습니다.",
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnClickListener
            }
            val intent = Intent(holder.itemView.context, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_FEED_ID, chat.feedId)
                putExtra(ChatActivity.EXTRA_ROOM_ID, chat.roomId)
                putExtra(ChatActivity.EXTRA_CHAT_ROOM_NAME, chat.chatRoomName)
                putExtra(ChatActivity.EXTRA_TITLE, chat.title)
                putExtra(ChatActivity.EXTRA_HOST_ID, chat.feedAuthorId)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    private fun bindThumbnail(imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.ic_paw)
            return
        }
        val radius = imageView.resources.getDimension(R.dimen.chat_list_thumbnail_radius)
        imageView.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_paw)
            error(R.drawable.ic_paw)
            transformations(RoundedCornersTransformation(radius))
        }
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val feedThumbnail: ImageView = itemView.findViewById(R.id.feed_thumbnail)
        val title: TextView = itemView.findViewById(R.id.title_txt)
        val lastMessage: TextView = itemView.findViewById(R.id.last_message_txt)
        val lastMessageTime: TextView = itemView.findViewById(R.id.last_message_time_txt)
    }
}
