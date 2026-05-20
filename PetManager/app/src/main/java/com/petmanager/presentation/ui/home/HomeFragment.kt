package com.petmanager.presentation.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.FeedRepository
import com.petmanager.data.repository.RegionRepository
import com.petmanager.databinding.HomeFragBinding
import com.petmanager.domain.model.Profiles
import com.petmanager.domain.model.FeedType
import com.petmanager.presentation.adapter.HomeAdapter
import com.petmanager.presentation.mapper.toProfiles
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private lateinit var binding: HomeFragBinding
    private var selectedRegionId: Long? = null
    private var selectedFeedType: FeedType = FeedType.COMMUNICATION
    private var currentLoadJob: Job? = null
    /** 첫 onResume 은 onViewCreated 직후라 이미 loadFeeds 했음 — 중복 호출 생략 */
    private var skipResumeRefresh = true

    @Inject lateinit var feedRepository: FeedRepository
    @Inject lateinit var regionRepository: RegionRepository
    @Inject lateinit var authRepository: AuthRepository

    companion object {
        const val TAG: String = "로그"

        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "HomeFragment - onCreate() called")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "HomeFragment - onAttach() called")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HomeFragBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvProfile.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProfile.setHasFixedSize(true)
        binding.rvProfile.adapter = HomeAdapter(arrayListOf())

        // 기본 선택: 소통
        binding.chipComm.isChecked = true

        binding.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedFeedType = when (id) {
                R.id.chipWalk -> FeedType.PET_WALKING
                R.id.chipCare -> FeedType.PET_SETTING
                else -> FeedType.COMMUNICATION
            }
            loadFeeds()
        }

        binding.searchInputLayout.setEndIconOnClickListener {
            loadFeeds()
        }
        binding.keywordEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadFeeds()
                true
            } else {
                false
            }
        }

        binding.regionSelectBtn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                showRegionPickerDialog()
            }
        }

        binding.emptyCtaBtn.setOnClickListener {
            (activity as? MainActivity)?.let { main ->
                main.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavi)
                    ?.selectedItemId = R.id.bottom_nav_write
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            initDefaultRegion()
            loadFeeds()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        if (skipResumeRefresh) {
            skipResumeRefresh = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            initDefaultRegion()
            loadFeeds()
        }
    }

    private fun loadFeeds() {
        currentLoadJob?.cancel()
        currentLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val regionId = selectedRegionId
                ?: authRepository.getDefaultRegionId()
            if (regionId == null) {
                Toast.makeText(requireContext(), "관심 지역을 먼저 설정해 주세요.", Toast.LENGTH_SHORT).show()
                updateEmptyState(emptyList())
                return@launch
            }

            val result = feedRepository.findFeeds(
                regionId = regionId,
                page = 0,
                feedType = selectedFeedType,
                keyword = binding.keywordEdit.text?.toString().orEmpty().trim(),
            )
            result.onSuccess { slice ->
                val mapped = slice.content.map { dto ->
                    val regionName = regionRepository.getRegionById(dto.regionId)?.regionName
                        ?: "지역 ${dto.regionId}"
                    dto.toProfiles(regionName)
                }
                val list = ArrayList(mapped)
                binding.rvProfile.adapter = HomeAdapter(list)
                updateEmptyState(list)
            }.onFailure { e ->
                Log.e(TAG, "loadFeeds", e)
                Toast.makeText(requireContext(), e.message ?: "피드를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                updateEmptyState(emptyList())
            }
        }
    }

    private suspend fun initDefaultRegion() {
        val defaultId = authRepository.getDefaultRegionId()
        selectedRegionId = defaultId

        val label = defaultId?.let { regionRepository.getRegionDisplayName(it) } ?: "내 지역 선택"
        binding.regionSelectBtn.text = label
    }

    private suspend fun showRegionPickerDialog() {
        if (authRepository.getCachedUserInfo() == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val ids = authRepository.getUserRegionIds()
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), "관심 지역을 먼저 설정해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val nameById = regionRepository.getRegionDisplayNames(ids)
        val items = ids.map { id -> id to (nameById[id] ?: "지역 $id") }
        val names = items.map { it.second }.toTypedArray()
        val currentIndex = items.indexOfFirst { it.first == selectedRegionId }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("내 지역 선택")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                selectedRegionId = items[which].first
                binding.regionSelectBtn.text = items[which].second
                dialog.dismiss()
                loadFeeds()
            }
            .setNegativeButton("취소", null)
            .show()
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
