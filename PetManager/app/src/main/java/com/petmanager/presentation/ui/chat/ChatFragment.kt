package com.petmanager.presentation.ui.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test1.databinding.ChatFragBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.petmanager.domain.model.ChatInfo
import com.petmanager.presentation.adapter.ChatAdapter

class ChatFragment : Fragment() {

    private lateinit var binding: ChatFragBinding
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = arrayListOf<ChatInfo>()

    companion object {
        const val TAG: String = "로그"

        fun newInstance(): ChatFragment {
            return ChatFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ChatFragBinding.inflate(inflater, container, false)

        val db = Firebase.firestore
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        chatAdapter = ChatAdapter(chatList)

        // RecyclerView 설정
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChat.setHasFixedSize(true)
        binding.rvChat.adapter = chatAdapter

        // 첫 번째 쿼리 실행
        db.collection("chatList")
            .whereEqualTo("userID", currentUser?.uid)
            .get()
            .addOnSuccessListener { documents ->
                chatList.clear()
                for (document in documents) {
                    val chat = document.toObject<ChatInfo>()
                    chatList.add(chat)
                }
                chatAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents: ", exception)
            }

        // 두 번째 쿼리 실행
        db.collection("chatList")
            .whereEqualTo("hostID", currentUser?.uid)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val chat = document.toObject<ChatInfo>()
                    // 순서를 바꿔서 추가
                    chatList.add(ChatInfo(
                        chat.hostID,
                        chat.hostName,
                        chat.userID,
                        chat.userName,
                        chat.postID,
                        chat.title
                    ))
                }
                chatAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents: ", exception)
            }

        return binding.root
    }
}

