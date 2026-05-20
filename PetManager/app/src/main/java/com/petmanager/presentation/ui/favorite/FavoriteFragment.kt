package com.petmanager.presentation.ui.favorite

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.petmanager.data.local.TokenStorage
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.FeedRepository
import com.petmanager.data.repository.RegionRepository
import com.petmanager.databinding.FavoriteFragBinding
import com.petmanager.domain.model.Profiles
import com.petmanager.presentation.adapter.ProfileAdapter
import com.petmanager.presentation.mapper.toProfiles
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FavoriteFragment : Fragment() {

    private lateinit var binding: FavoriteFragBinding

    @Inject lateinit var feedRepository: FeedRepository
    @Inject lateinit var regionRepository: RegionRepository
    @Inject lateinit var authRepository: AuthRepository

    private val profileList = arrayListOf<Profiles>()
    private lateinit var adapter: ProfileAdapter
    private var skipResumeRefresh = true

    companion object {
        const val TAG: String = "로그"

        fun newInstance(): FavoriteFragment {
            return FavoriteFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "FavoriteFragment - onCreate() called")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "Favorite - onAttach() called")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FavoriteFragBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProfileAdapter(profileList) { item, profilePosition ->
            if (profilePosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return@ProfileAdapter
            viewLifecycleOwner.lifecycleScope.launch {
                feedRepository.toggleLike(item.postID).onSuccess {
                    profileList.removeAt(profilePosition)
                    adapter.notifyItemRemoved(profilePosition)
                    adapter.notifyItemRangeChanged(profilePosition, profileList.size - profilePosition)
                    updateEmptyState(profileList)
                }.onFailure { e ->
                    Toast.makeText(requireContext(), e.message ?: "좋아요 처리 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.rvProfile.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProfile.setHasFixedSize(true)
        binding.rvProfile.adapter = adapter

        binding.emptyCtaBtn.setOnClickListener {
            (activity as? MainActivity)?.let { main ->
                main.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavi)
                    ?.selectedItemId = R.id.bottom_nav_home
            }
        }

        loadLikedFeeds()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        if (skipResumeRefresh) {
            skipResumeRefresh = false
            return
        }
        loadLikedFeeds()
    }

    private fun loadLikedFeeds() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 게이트웨이는 JWT 없으면 401 — 프로필 캐시만 있고 AT 가 비는 경우 방지
            if (authRepository.getCachedUserInfo() == null || TokenStorage.accessToken().isNullOrBlank()) {
                updateEmptyState(profileList)
                return@launch
            }
            feedRepository.findMyLikedFeeds(page = 0).onSuccess { slice ->
                profileList.clear()
                profileList.addAll(
                    slice.content.map { dto ->
                        val regionName = regionRepository.getRegionById(dto.regionId)?.regionName
                            ?: "지역 ${dto.regionId}"
                        dto.toProfiles(regionName, likedOverride = true)
                    }
                )
                adapter.notifyDataSetChanged()
                updateEmptyState(profileList)
            }.onFailure { e ->
                Log.e(TAG, "loadLikedFeeds", e)
                Toast.makeText(requireContext(), e.message ?: "목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                updateEmptyState(profileList)
            }
        }
    }

    private fun updateEmptyState(list: List<Profiles>) {
        if (list.isEmpty()) {
            binding.rvProfile.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.rvProfile.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }
}
