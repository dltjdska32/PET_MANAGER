package com.petmanager.presentation.adapter

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.petmanager.domain.model.ChatInfo
import com.petmanager.presentation.ui.chat.ChatActivity
import com.example.test1.R

class ChatAdapter(val chatList: List<ChatInfo>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_list, parent, false)
        return ChatViewHolder(view)
    }

    override fun getItemCount(): Int {
        return chatList.size
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chatList[position]

        holder.names.text = chat.hostName
        holder.titles.text =chat.title


        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, ChatActivity::class.java).apply {
                putExtra("title", chat.title)
                putExtra("postId", chat.postID)
                putExtra("hostId", chat.hostID)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val names: TextView = itemView.findViewById(R.id.name_txt) // 이름
        val titles: TextView = itemView.findViewById(R.id.title_txt)
    }
}

