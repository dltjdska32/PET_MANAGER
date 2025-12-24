package com.petmanager.presentation.ui.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test1.databinding.ActivityChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.petmanager.domain.model.Message
import com.petmanager.presentation.adapter.MessageAdapter
import com.petmanager.data.remote.api.RetrofitInstance
import com.petmanager.data.remote.api.FCMNotification
import com.petmanager.data.remote.api.NotificationData
import com.petmanager.data.remote.api.FCMResponse
import com.petmanager.domain.model.Info
import com.example.test1.R

class ChatActivity : AppCompatActivity() {

    private lateinit var receiverTitle: String
    private lateinit var receiverPostId: String
    private lateinit var receiverHostId: String


    //바인딩 객체
    private lateinit var binding : ActivityChatBinding

    lateinit var mAuth : FirebaseAuth
    lateinit var mDbRef : DatabaseReference
    private lateinit var receiverRoom : String
    private lateinit var senderRoom : String
    private lateinit var auth: FirebaseAuth



    private lateinit var messageList: ArrayList<Message>

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        //enableEdgeToEdge()
        //setContentView(R.layout.activity_chat)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        messageList = ArrayList()
        val messageAdapter: MessageAdapter = MessageAdapter(this,messageList)

        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = messageAdapter

        receiverTitle = intent.getStringExtra("title").toString()
        receiverPostId = intent.getStringExtra("postId").toString()
        receiverHostId = intent.getStringExtra("hostId").toString()


        mAuth = FirebaseAuth.getInstance()
        mDbRef = FirebaseDatabase.getInstance().reference

        val senderUid = mAuth.currentUser?.uid

        receiverRoom = receiverPostId + senderUid + receiverHostId
        senderRoom = receiverPostId + receiverHostId + senderUid


        supportActionBar?.title = receiverTitle


        //메시지 전송
        binding.sendBtn.setOnClickListener{
            val message = binding.messageEdit.text.toString()
            val messageObject = Message(message,senderUid)


            /*
            mDbRef.child("chats").child(receiverHostId).child("messages").push()
                .setValue(messageObject).addOnSuccessListener {
                    //저장 성공하면 상대방 채팅 db도 생성
                    mDbRef.child("chats").child(senderUid.toString()).child("messages").push()
                        .setValue(messageObject)
                }

             */
            //데이터 저장
            mDbRef.child("chats").child(senderRoom).child("messages").push()
                .setValue(messageObject).addOnSuccessListener {
                    //저장 성공하면 상대방 채팅 db도 생성
                    mDbRef.child("chats").child(receiverRoom).child("messages").push()
                        .setValue(messageObject)
                }

            db.collection("user")
                .whereEqualTo("userID", currentUser?.uid)
                .get()
                .addOnSuccessListener { documents ->
                    for(document in documents){
                        val user = document.toObject<Info>()
                        val receiverToken = user.fcmToken.toString()
                        val title = "새 메시지 도착"

                        // FCM 알림 전송
                        sendNotification(receiverToken, title, binding.messageEdit.text.toString())

                    }

                }



            //입력 적용
            binding.messageEdit.setText("")
            binding.chatRecyclerView.post {
                binding.chatRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
            }




            /*
            mDbRef.child("chats").child(senderUid.toString()).child(receiverPostId).child(receiverHostId).child("messages").push()
                .setValue(messageObject).addOnSuccessListener {
                    //저장 성공하면 상대방 채팅 db도 생성
                    mDbRef.child("chats").child(receiverHostId).child(receiverPostId).child(senderUid.toString()).child("messages").push()
                        .setValue(messageObject)
                }

            //입력 적용
            binding.messageEdit.setText("")

             */



        }

        //메시지 가져오기
        mDbRef.child("chats").child(senderRoom).child("messages")
            .addValueEventListener(object:ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    messageList.clear()

                    for(postSnapshot in snapshot.children){

                        val message = postSnapshot.getValue(Message::class.java)
                        messageList.add(message!!)
                    }
                    messageAdapter.notifyDataSetChanged()
                    binding.chatRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
                    //messageList
                }

                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
                }
            })


    }



    fun sendNotification(receiverToken: String, title: String, body: String) {
        val notification = FCMNotification(
            to = receiverToken,
            notification = NotificationData(
                title = title,
                body = body
            )
        )

        RetrofitInstance.api.sendNotification(notification).enqueue(object : retrofit2.Callback<FCMResponse> {
            override fun onResponse(call: Call<FCMResponse>, response: retrofit2.Response<FCMResponse>) {
                if (response.isSuccessful) {
                    Log.d("FCM", "Notification sent successfully")
                } else {
                    Log.e("FCM", "Failed to send notification: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<FCMResponse>, t: Throwable) {
                Log.e("FCM", "Error sending notification", t)
            }
        })
    }

}

