package com.petmanager.presentation.ui.myinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import coil.load
import coil.transform.CircleCropTransformation
import com.petmanager.R
import com.petmanager.databinding.MyInfoFragBinding
import com.petmanager.domain.model.User
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.RegionRepository
import com.petmanager.presentation.mapper.normalizeImageUrl
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.petmanager.presentation.ui.auth.RegionSelectionActivity
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyInfoFragment : Fragment() {

    private lateinit var binding: MyInfoFragBinding
    private val authViewModel: AuthViewModel by activityViewModels()

    @Inject lateinit var regionRepository: RegionRepository
    @Inject lateinit var authRepository: AuthRepository

    companion object {
        fun newInstance(): MyInfoFragment {
            return MyInfoFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MyInfoFragBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // 화면 진입·복귀 시마다 백엔드 최신 정보 갱신 (/api/auth/user)
        authViewModel.refreshMyInfo()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.userInfo.collect { user ->
                    binding.tvNickname.text = user?.nickname ?: "-"
                    binding.tvEmail.text = user?.email ?: "-"
                    bindProfileAvatar(user)
                    // suspend 호출로 순차 실행 → StateFlow conflate 로 레이스 차단
                    renderRegionChips(user?.regionIds.orEmpty())
                }
            }
        }

        binding.editNicknameBtn.setOnClickListener {
            startActivity(Intent(requireContext(), NicknameEditActivity::class.java))
        }

        binding.profileImageBtn.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileImageEditActivity::class.java))
        }

        // 지역 정보 수정 — 저장된 유저 지역을 프리셀렉트한 상태로 지역 선택 화면 재사용
        binding.editRegionBtn.setOnClickListener {
            startActivity(RegionSelectionActivity.editIntent(requireContext()))
        }

        binding.setPrimaryRegionBtn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                showPrimaryRegionDialog()
            }
        }

        binding.myPostsBtn.setOnClickListener {
            startActivity(MyPostsActivity.createIntent(requireContext()))
        }

        // 로그아웃
        binding.logoutBtn.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        val activity = activity as? MainActivity
        activity?.signOutAndStartSignInActivity()
    }

    private fun bindProfileAvatar(user: User?) {
        val iv = binding.ivProfileAvatar
        val raw = user?.profileImageUrl?.trim()?.takeIf { it.isNotEmpty() }
        android.util.Log.d("MyInfoFragment", "bindProfileAvatar raw=$raw")
        if (raw == null) {
            iv.setImageResource(R.drawable.ic_paw)
            return
        }
        val url = normalizeImageUrl(raw)
        android.util.Log.d("MyInfoFragment", "bindProfileAvatar loading url=$url")
        iv.load(url) {
            crossfade(true)
            allowHardware(false)
            placeholder(R.drawable.ic_paw)
            error(R.drawable.ic_paw)
            transformations(CircleCropTransformation())
            listener(
                onError = { _, result ->
                    android.util.Log.e("MyInfoFragment", "Coil load FAILED: ${result.throwable.message}", result.throwable)
                },
                onSuccess = { _, _ ->
                    android.util.Log.d("MyInfoFragment", "Coil load SUCCESS for $url")
                }
            )
        }
    }

    /**
     * Chip 렌더링.
     * - suspend 로 정의해 collect 블록 안에서 순차 실행.
     * - IO(지역명 조회) 가 끝난 직후에만 removeAllViews → addView 를 일괄 수행해
     *   이전 emit 의 잔여 coroutine 이 새 UI 위에 Chip 을 덧붙이는 레이스를 제거.
     */
    private suspend fun renderRegionChips(regionIds: List<Long>) {
        val chipGroup: ChipGroup = binding.regionChipGroup

        if (regionIds.isEmpty()) {
            chipGroup.removeAllViews()
            chipGroup.visibility = View.GONE
            binding.tvRegion.visibility = View.VISIBLE
            binding.tvRegion.text = "아직 등록된 지역이 없어요"
            return
        }

        val nameById = regionRepository.getRegionDisplayNames(regionIds)
        val primaryId = authRepository.getPrimaryRegionId()

        chipGroup.removeAllViews()
        regionIds.forEach { id ->
            val chip = Chip(requireContext()).apply {
                text = buildString {
                    append(nameById[id] ?: "지역 $id")
                    if (id == primaryId) append(" · 대표")
                }
                isClickable = false
                isCheckable = false
                isCloseIconVisible = false
            }
            chipGroup.addView(chip)
        }
        binding.tvRegion.visibility = View.VISIBLE
        binding.tvRegion.text = when {
            primaryId != null -> {
                val name = nameById[primaryId] ?: "지역 $primaryId"
                "대표: $name · 총 ${regionIds.size}개"
            }
            else -> "총 ${regionIds.size}개 등록됨 (대표 미설정)"
        }
        chipGroup.visibility = View.VISIBLE
    }

    private suspend fun showPrimaryRegionDialog() {
        regionRepository.initializeRegionsIfNeeded()

        val ids = authRepository.getUserRegionIds()
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), "관심 지역을 먼저 설정해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val nameById = regionRepository.getRegionDisplayNames(ids)
        val items = ids.map { id -> id to (nameById[id] ?: "지역 $id") }
        val names = items.map { it.second }.toTypedArray()
        val currentPrimary = authRepository.getPrimaryRegionId()
        val currentIndex = items.indexOfFirst { it.first == currentPrimary }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("대표 지역 설정")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selectedId = items[which].first
                viewLifecycleOwner.lifecycleScope.launch {
                    authRepository.setPrimaryRegion(selectedId)
                        .onSuccess {
                            Toast.makeText(requireContext(), "대표 지역이 설정되었습니다.", Toast.LENGTH_SHORT).show()
                            renderRegionChips(ids)
                        }
                        .onFailure { e ->
                            Toast.makeText(
                                requireContext(),
                                e.message ?: "대표 지역 설정에 실패했습니다.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
