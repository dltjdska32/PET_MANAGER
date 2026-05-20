package com.petmanager.presentation.ui.myinfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.petmanager.data.local.TokenStorage
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.FeedRepository
import com.petmanager.data.repository.RegionRepository
import com.petmanager.databinding.ActivityMyPostsBinding
import com.petmanager.domain.model.Profiles
import com.petmanager.presentation.adapter.HomeAdapter
import com.petmanager.presentation.mapper.toProfiles
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyPostsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyPostsBinding

    @Inject lateinit var feedRepository: FeedRepository
    @Inject lateinit var regionRepository: RegionRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyPostsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.navigationIcon?.setTint(
            ContextCompat.getColor(this, R.color.text_primary),
        )

        binding.rvPosts.layoutManager = LinearLayoutManager(this)
        binding.rvPosts.setHasFixedSize(true)
        binding.rvPosts.adapter = HomeAdapter(arrayListOf())
    }

    override fun onResume() {
        super.onResume()
        loadMyPosts()
    }

    private fun loadMyPosts() {
        if (authRepository.getCachedUserInfo() == null || TokenStorage.accessToken().isNullOrBlank()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvPosts.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val result = feedRepository.findMyFeeds(page = 0)
            result.fold(
                onSuccess = { slice ->
                    val list = ArrayList<Profiles>()
                    for (dto in slice.content) {
                        val regionName = regionRepository.getRegionById(dto.regionId)?.regionName
                            ?: "지역 ${dto.regionId}"
                        list.add(dto.toProfiles(regionName))
                    }
                    binding.rvPosts.adapter = HomeAdapter(list)
                    if (list.isEmpty()) {
                        binding.rvPosts.visibility = View.GONE
                        binding.emptyState.visibility = View.VISIBLE
                    } else {
                        binding.rvPosts.visibility = View.VISIBLE
                        binding.emptyState.visibility = View.GONE
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this@MyPostsActivity, e.message ?: "목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    binding.rvPosts.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                },
            )
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, MyPostsActivity::class.java)
    }
}
