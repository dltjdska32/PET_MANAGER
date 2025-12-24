package com.petmanager.presentation.ui.home

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test1.databinding.ActivityMainBinding
import com.example.test1.databinding.ActivityPostBinding
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import com.petmanager.domain.model.ChatInfo
import com.petmanager.domain.model.Info
import com.petmanager.domain.model.PostInfo
import com.petmanager.domain.model.FavoritePost
import com.petmanager.presentation.ui.chat.ChatActivity
import com.example.test1.R

typealias post_info = PostInfo

class PostActivity : AppCompatActivity() {



    private lateinit var auth: FirebaseAuth
    private lateinit var mAuth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    private lateinit var binding: ActivityPostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val chat_btn: Button = binding.chatBtn
        val favorite_imgbtn: ImageView = binding.favImgbtn
        val title_txt: TextView = binding.titleTxt
        val management_type_txt: TextView = binding.managementTypeTxt
        val animal_type_txt: TextView = binding.animalTypeTxt
        val contents_txt: TextView = binding.contentsTxt
        val price_txt: TextView = binding.priceTxt

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser


        // 인텐트에서 데이터를 받아옵니다.
        val postId = intent.getStringExtra("postId")

        Log.d("lsn", "${postId}")

        var title: String? = null
        var management_type: String? = null
        var animal_type: String? = null
        var contents: String? = null
        var price: String? = null

        //찜 여부 확인 후 아이콘 변경
        db.collection("postList")
            .document(postId.toString()) //키값넣는거. where절
            .get()
            .addOnSuccessListener { document ->
                val post = document.toObject<post_info>()
                price = post?.price.toString()
                title = post?.title.toString()
                management_type = post?.management_type.toString()
                animal_type = post?.animal_type.toString()
                contents = post?.contents.toString()
                val isFavorite = false;

                title_txt.setText(title)
                management_type_txt.setText(management_type)
                animal_type_txt.setText(animal_type)
                contents_txt.setText(contents)
                price_txt.setText(price)

                db.collection("Favorite")
                    .whereEqualTo("userID", currentUser?.uid)
                    .whereEqualTo("postID", postId)//키값넣는거. where절
                    .get()
                    .addOnSuccessListener { documents ->
                        if(documents.isEmpty){
                            favorite_imgbtn.setImageResource(R.drawable.heart_blank)
                        }
                        else{
                            favorite_imgbtn.setImageResource(R.drawable.heart)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                    }
            }


        //찜버튼 클릭
        favorite_imgbtn.setOnClickListener {
            db.collection("Favorite")
                .whereEqualTo("userID", currentUser?.uid)
                .whereEqualTo("postID", postId)
                .get()
                .addOnSuccessListener {documents ->
                    if(documents.isEmpty){
                        val post = FavoritePost(
                            currentUser?.uid
                            ,postId
                        )
                        db.collection("Favorite").add(post)
                        favorite_imgbtn.setImageResource(R.drawable.heart)
                    } else {
                        for(document in documents) {
                            db.collection("Favorite").document(document.id)
                                .delete()
                        }
                        favorite_imgbtn.setImageResource(R.drawable.heart_blank)
                    }



                }
        }


        //채팅 버튼 클릭
        chat_btn.setOnClickListener {


            // 본인과 본인이 채팅할 순 없으니까
            db.collection("postList")
                .whereEqualTo(FieldPath.documentId(),postId)
                .get()
                .addOnSuccessListener {documents ->
                    if(!documents.isEmpty){
                        for(document in documents){
                            val post = document.toObject<post_info>()
                            val hostID = post.userID.toString()
                            //본인 게시물 아님이 확인된 상태 여기서 채팅 관련 로직 추가
                            if(hostID != currentUser?.uid){
                                val intent = Intent(this, ChatActivity::class.java)

                                db.collection("user")
                                    .whereEqualTo("userID",currentUser?.uid)
                                    .get()
                                    .addOnSuccessListener() {user_documents ->
                                        for(user_document in user_documents) {
                                            val user =user_document.toObject<Info>()
                                            val userName :String
                                            userName =user.name.toString()
                                            db.collection("user")
                                                .whereEqualTo("userID",hostID)
                                                .get()
                                                .addOnSuccessListener() {host_documents ->
                                                    for(host_document in host_documents) {
                                                        val host =host_document.toObject<Info>()
                                                        val hostName :String
                                                        hostName =host.name.toString()
                                                        val chatInfo = ChatInfo(
                                                            currentUser?.uid.toString(),
                                                            userName,
                                                            hostID,
                                                            hostName,
                                                            postId.toString(),
                                                            title.toString()
                                                        )

                                                        db.collection("chatList")
                                                            .whereEqualTo("userID",currentUser?.uid)
                                                            .whereEqualTo("hostID",hostID)
                                                            .whereEqualTo("postID",postId.toString())
                                                            .get()
                                                            .addOnSuccessListener { documents ->
                                                                if(documents.isEmpty){
                                                                    db.collection("chatList")
                                                                        .add(chatInfo)
                                                                        .addOnSuccessListener { documentReference ->
                                                                            Log.d(ContentValues.TAG, "DocumentSnapshot written with ID: ${documentReference.id}")
                                                                        }
                                                                        .addOnFailureListener { e ->
                                                                            Log.w(ContentValues.TAG, "Error adding document", e)
                                                                        }
                                                                }
                                                            }
                                                            .addOnFailureListener { e ->
                                                                Log.w(ContentValues.TAG, "Error adding document", e)
                                                            }



                                                    }
                                                }
                                        }
                                    }
                                intent.putExtra("title", title)
                                intent.putExtra("postId", postId)
                                intent.putExtra("hostId",hostID)
                                startActivity(intent)
                            }
                        }
                    }
                }
        }
    }
}

