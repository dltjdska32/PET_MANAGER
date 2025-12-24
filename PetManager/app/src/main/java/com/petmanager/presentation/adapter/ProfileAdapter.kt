package com.petmanager.presentation.adapter

import android.content.ContentValues
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.petmanager.domain.model.Profiles
import com.petmanager.domain.model.Info
import com.petmanager.domain.model.PostInfo
import com.petmanager.domain.model.FavoritePost
import com.petmanager.presentation.ui.home.PostActivity
import com.example.test1.R

typealias post_info = PostInfo

class ProfileAdapter(val profileList: ArrayList<Profiles>) : RecyclerView.Adapter<ProfileAdapter.CustomViewHolder>() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return CustomViewHolder(view).apply {
            itemView.setOnClickListener {
                val curPos: Int = adapterPosition
                val profile: Profiles = profileList.get(curPos)
                val postId: String = profile.postID
                val intent = Intent(parent.context, PostActivity::class.java).apply {
                    putExtra("postId", postId)
                }

                parent.context.startActivity(intent)

            }
        }

    }

    override fun getItemCount(): Int {
        return profileList.size
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val profile = profileList[position]
        holder.work.setImageResource(profile.work)
        holder.names.text = profile.names
        holder.dong.text = profile.dong
        holder.ment.text = profile.ment
        val postID = profile.postID

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        var userID =""

        db.collection("user")
            .whereEqualTo("userID", currentUser?.uid) //키값넣는거. where절
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val user = document.toObject<Info>()
                    val id = user?.userID
                    Log.d("p", "${id}")
                    if(id != null){
                        userID = id
                    }
                    Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                }


                db.collection("Favorite")
                    .whereEqualTo("userID", userID)
                    .whereEqualTo("postID", postID)//키값넣는거. where절
                    .get()
                    .addOnSuccessListener { documents ->
                        if(documents.isEmpty){
                            holder.love.setImageResource(R.drawable.heart_blank)
                        }
                        else{
                            holder.love.setImageResource(R.drawable.heart)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                    }
            }
            .addOnFailureListener { exception ->
                Log.w(ContentValues.TAG, "Error getting documents: ", exception)
            }






        // love 이미지 클릭 리스너 설정
        holder.love.setOnClickListener {
            //profile.isFavorite = !profile.isFavorite
            //val resId = if (profile.isFavorite) R.drawable.heart else R.drawable.heart_blank
            //holder.love.setImageResource(resId)
            val db = Firebase.firestore
            auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            var userID =""

            db.collection("user")
                .whereEqualTo("userID", currentUser?.uid) //키값넣는거. where절
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        val user = document.toObject<Info>()
                        val id = user?.userID
                        Log.d("p", "${id}")
                        if(id != null){
                            userID = id
                        }
                        Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                    }


                    db.collection("Favorite")
                        .whereEqualTo("userID", userID)
                        .whereEqualTo("postID", postID)//키값넣는거. where절
                        .get()
                        .addOnSuccessListener { documents ->
                            if(documents.isEmpty){
                                //해당 글 찜에 추가 후 아이콘 변경
                                val post = FavoritePost(
                                    userID,
                                    postID
                                )
                                db.collection("Favorite")
                                    .add(post)
                                    .addOnSuccessListener { documentReference ->
                                        Log.d(ContentValues.TAG, "DocumentSnapshot written with ID: ${documentReference.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w(ContentValues.TAG, "Error adding document", e)
                                    }
                                val resId = R.drawable.heart
                                holder.love.setImageResource(resId)
                            }
                            else{
                                //이미 있으니 삭제 후 아이콘 변경
                                for (document in documents) {
                                    db.collection("Favorite").document(document.id)
                                        .delete()
                                        .addOnSuccessListener { Log.d("delsuss", "DocumentSnapshot successfully deleted!") }
                                        .addOnFailureListener { e -> Log.w("fail", "Error deleting document", e) }
                                    Log.d(ContentValues.TAG, "${document.id} => ${document.data}")

                                }

                                val resId =  R.drawable.heart_blank
                                holder.love.setImageResource(resId)
                            }
                            /*profile.isFavorite = !profile.isFavorite
                            val resId = if (profile.isFavorite) R.drawable.heart else R.drawable.heart_blank
                            holder.love.setImageResource(resId)
                        */
                        }

                        .addOnFailureListener { exception ->
                            Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                        }

                }
                .addOnFailureListener { exception ->
                    Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                }


        }
    }

    // 안드로이드 스튜디오에서 기본적으로 제공하는 리사이클러뷰를 상속받음
    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        //val post: TextView = itemView.findViewById(R.id.post_id)
        val work: ImageView = itemView.findViewById(R.id.iv_profile) // 호텔 or 산책
        val names: TextView = itemView.findViewById(R.id.tv_names) // 이름
        val dong: TextView = itemView.findViewById(R.id.tv_dong) // 동
        val ment: TextView = itemView.findViewById(R.id.tv_ment) // 내용
        val love: ImageView = itemView.findViewById(R.id.iv_heart) // 찜하기
    }
}

