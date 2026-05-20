package com.petmanager.presentation.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.petmanager.data.remote.dto.FeedDetailDto
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.FeedRepository
import com.petmanager.data.repository.RegionRepository
import com.petmanager.databinding.ActivityPostBinding
import com.petmanager.presentation.ui.chat.ChatActivity
import com.petmanager.presentation.mapper.normalizeImageUrl
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostBinding

    @Inject lateinit var feedRepository: FeedRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var regionRepository: RegionRepository

    private var loadedDetail: FeedDetailDto? = null
    private var likedAssumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val postId = intent.getStringExtra("postId").orEmpty()
        Log.d("PostActivity", "postId: $postId")

        if (postId.isEmpty()) {
            Toast.makeText(this, "잘못된 게시글입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            feedRepository.getFeedDetail(postId)
                .onSuccess { bindDetail(it) }
                .onFailure { e ->
                    Log.e("PostActivity", "detail", e)
                    Toast.makeText(this@PostActivity, e.message ?: "상세 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
        }

        binding.favImgbtn.setOnClickListener {
            lifecycleScope.launch {
                feedRepository.toggleLike(postId).onSuccess {
                    likedAssumed = !likedAssumed
                    binding.favImgbtn.setImageResource(
                        if (likedAssumed) R.drawable.heart else R.drawable.heart_blank,
                    )
                    feedRepository.getFeedDetail(postId).onSuccess { bindDetail(it) }
                }.onFailure { e ->
                    Toast.makeText(this@PostActivity, e.message ?: "좋아요 처리 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDeleteFeed.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("게시글 삭제")
                .setMessage("이 게시글을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제") { _, _ ->
                    lifecycleScope.launch {
                        feedRepository.deleteFeed(postId).onSuccess {
                            Toast.makeText(this@PostActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            finish()
                        }.onFailure { e ->
                            Toast.makeText(this@PostActivity, e.message ?: "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }

        binding.chatBtn.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_TITLE, binding.titleTxt.text.toString())
                putExtra(ChatActivity.EXTRA_FEED_ID, postId)
                putExtra(ChatActivity.EXTRA_CHAT_ROOM_NAME, binding.titleTxt.text.toString())
                putExtra(ChatActivity.EXTRA_HOST_ID, loadedDetail?.authorId.orEmpty())
            }
            startActivity(intent)
        }
    }

    private fun bindDetail(d: FeedDetailDto) {
        loadedDetail = d

        val url = d.mainImgUrl.firstOrNull()?.let { normalizeImageUrl(it) }
        if (!url.isNullOrBlank()) {
            binding.postMainImage.load(url) {
                crossfade(true)
                placeholder(R.drawable.ic_paw)
                error(R.drawable.ic_paw)
            }
        } else {
            binding.postMainImage.setImageResource(R.drawable.ic_paw)
        }

        binding.authorNickname.text = d.authorNickname
        lifecycleScope.launch {
            val region = regionRepository.getRegionById(d.regionId)
            binding.regionLabel.text = region?.regionName ?: "지역 ${d.regionId}"
        }

        binding.titleTxt.text = d.title
        binding.managementTypeTxt.text = feedTypeLabel(d.feedType)
        binding.contentsTxt.text = d.description

        if (d.startDate != null && d.endDate != null) {
            binding.dateRangeTxt.visibility = View.VISIBLE
            binding.dateRangeTxt.text = "${d.startDate} ~ ${d.endDate}"
        } else {
            binding.dateRangeTxt.visibility = View.GONE
        }

        val payText = if (d.pay > 0) "${formatPay(d.pay)}원" else "가격 정보 없음"
        binding.priceTxt.text = payText

        binding.likesCountTxt.text = "좋아요 ${d.likesCount}"
        binding.createdAtTxt.text = formatCreatedAt(d.createdAt)

        likedAssumed = d.isLiked
        binding.favImgbtn.setImageResource(
            if (d.isLiked) R.drawable.heart else R.drawable.heart_blank,
        )

        val myId = authRepository.getCachedUserInfo()?.id
        val isAuthor = myId != null && myId == d.authorId
        binding.btnDeleteFeed.visibility = if (isAuthor) View.VISIBLE else View.GONE
        binding.chatBtn.visibility = if (isAuthor) View.GONE else View.VISIBLE
    }

    private fun feedTypeLabel(feedType: String): String = when (feedType) {
        "PET_WALKING" -> "산책"
        "PET_SETTING" -> "돌봄"
        else -> "소통"
    }

    private fun formatPay(pay: Int): String {
        return String.format("%,d", pay)
    }

    private fun formatCreatedAt(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val date = iso.substringBefore("T")
            val parts = date.split("-")
            if (parts.size == 3) "${parts[1].toInt()}/${parts[2].toInt()}" else date
        } catch (_: Exception) {
            iso
        }
    }
}
