package com.petmanager.presentation.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.petmanager.domain.model.Profiles
import com.petmanager.presentation.ui.home.PostActivity
import com.petmanager.R

class HomeAdapter(val profileList: ArrayList<Profiles>) : RecyclerView.Adapter<HomeAdapter.CustomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_list_item, parent, false)
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

    override fun getItemCount(): Int = profileList.size

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val profile = profileList[position]

        holder.title.text = profile.ment
        holder.region.text = profile.dong
        holder.author.text = profile.names
        holder.likes.text = profile.deadline

        holder.thumbLiked.setImageResource(
            if (profile.isFavorite) R.drawable.ic_heart_liked_overlay
            else R.drawable.ic_heart_thumb_outline_white,
        )

        if (!profile.thumbnailUrl.isNullOrBlank()) {
            holder.thumbnail.load(profile.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_paw)
                error(R.drawable.ic_paw)
                transformations(RoundedCornersTransformation(12f))
            }
            holder.thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            holder.thumbnail.setImageResource(profile.work)
            holder.thumbnail.scaleType = ImageView.ScaleType.CENTER
        }
    }

    class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.iv_profile)
        val thumbLiked: ImageView = itemView.findViewById(R.id.iv_thumb_liked)
        val title: TextView = itemView.findViewById(R.id.tv_ment)
        val region: TextView = itemView.findViewById(R.id.tv_dong)
        val author: TextView = itemView.findViewById(R.id.tv_names)
        val likes: TextView = itemView.findViewById(R.id.tv_deadline)
    }
}
