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
import com.petmanager.presentation.ui.home.PostActivity
import com.example.test1.R

class HomeAdapter(val profileList: ArrayList<Profiles>) : RecyclerView.Adapter<HomeAdapter.CustomViewHolder>() {


    private lateinit var auth: FirebaseAuth

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
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

        holder.names.text = profile.names
        holder.dong.text = profile.dong
        holder.ment.text = profile.ment
        holder.work.setImageResource(profile.work)
        holder.deadline.text = "마감일: ${profile.deadline}"

        val postID = profile.postID

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        var userID =""
    }

    // 안드로이드 스튜디오에서 기본적으로 제공하는 리사이클러뷰를 상속받음
    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {


        //val post: TextView = itemView.findViewById(R.id.post_id)
        val work: ImageView = itemView.findViewById(R.id.iv_profile) // 호텔 or 산책
        val names: TextView = itemView.findViewById(R.id.tv_names) // 이름
        val dong: TextView = itemView.findViewById(R.id.tv_dong) // 동
        val ment: TextView = itemView.findViewById(R.id.tv_ment) // 내용
        val deadline: TextView = itemView.findViewById(R.id.tv_deadline)
    }
}

