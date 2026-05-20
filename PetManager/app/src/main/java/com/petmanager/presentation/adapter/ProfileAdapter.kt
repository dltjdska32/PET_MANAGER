package com.petmanager.presentation.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.petmanager.domain.model.Profiles
import com.petmanager.presentation.ui.home.PostActivity
import com.petmanager.R

class ProfileAdapter(
    private val profileList: ArrayList<Profiles>,
    private val onToggleLike: (Profiles, Int) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.CustomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return CustomViewHolder(view).apply {
            itemView.setOnClickListener {
                val curPos: Int = adapterPosition
                if (curPos != RecyclerView.NO_POSITION) {
                    val profile: Profiles = profileList[curPos]
                    val postId: String = profile.postID
                    val intent = Intent(parent.context, PostActivity::class.java).apply {
                        putExtra("postId", postId)
                    }
                    parent.context.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return profileList.size
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val profile = profileList[position]
        if (!profile.thumbnailUrl.isNullOrBlank()) {
            holder.work.load(profile.thumbnailUrl) {
                crossfade(true)
                placeholder(profile.work)
                error(profile.work)
            }
        } else {
            holder.work.setImageResource(profile.work)
        }
        holder.names.text = profile.names
        holder.dong.text = profile.dong
        holder.ment.text = profile.ment
        holder.deadline.text = profile.deadline

        holder.love.setImageResource(if (profile.isFavorite) R.drawable.heart else R.drawable.heart_blank)

        holder.love.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onToggleLike(profileList[pos], pos)
            }
        }
    }

    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val work: ImageView = itemView.findViewById(R.id.iv_profile)
        val names: TextView = itemView.findViewById(R.id.tv_names)
        val dong: TextView = itemView.findViewById(R.id.tv_dong)
        val ment: TextView = itemView.findViewById(R.id.tv_ment)
        val love: ImageView = itemView.findViewById(R.id.iv_heart)
        val deadline: TextView = itemView.findViewById(R.id.tv_deadline)
    }
}
