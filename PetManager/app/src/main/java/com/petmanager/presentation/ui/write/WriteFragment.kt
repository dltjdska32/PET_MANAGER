package com.petmanager.presentation.ui.write

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.FeedRepository
import com.petmanager.data.repository.RegionRepository
import com.petmanager.databinding.WriteFragBinding
import com.petmanager.domain.model.FeedType
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WriteFragment : Fragment() {

    private lateinit var binding: WriteFragBinding
    private var startYear: Int? = null
    private var startMonth: Int? = null
    private var startDay: Int? = null
    private var endYear: Int? = null
    private var endMonth: Int? = null
    private var endDay: Int? = null
    private var mainImageUri: Uri? = null
    private val extraImageUris = mutableListOf<Uri>()
    private var selectedFeedType: FeedType? = null
    private var selectedRegionId: Long? = null

    @Inject lateinit var feedRepository: FeedRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var regionRepository: RegionRepository

    private val pickMainImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        mainImageUri = uri
        if (::binding.isInitialized) {
            binding.mainImagePreview.setImageURI(uri)
            binding.mainImagePreview.visibility = View.VISIBLE
            binding.clearMainImageBtn.visibility = View.VISIBLE
        }
    }

    private val pickExtraImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        val remaining = 5 - extraImageUris.size
        if (remaining <= 0) {
            Toast.makeText(requireContext(), getString(R.string.write_photo_extra_limit), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        if (uris.size > remaining) {
            Toast.makeText(requireContext(), getString(R.string.write_photo_extra_truncated), Toast.LENGTH_SHORT).show()
        }
        extraImageUris.addAll(uris.take(remaining))
        refreshExtraThumbnails()
    }

    companion object {
        const val TAG: String = "로그"

        fun newInstance(): WriteFragment {
            return WriteFragment()
        }

        private fun ymdKey(y: Int, m: Int, d: Int): Int = y * 10_000 + m * 100 + d
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "WriteFragment - onCreate() called")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "WriteFragment - onAttach() called")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = WriteFragBinding.inflate(inflater, container, false)

        val postBtn: MaterialButton = binding.postBtn
        val feedTypeGroup = binding.toggleFeedType

        binding.pickMainImageBtn.setOnClickListener {
            pickMainImage.launch("image/*")
        }
        binding.clearMainImageBtn.setOnClickListener {
            mainImageUri = null
            binding.mainImagePreview.setImageDrawable(null)
            binding.mainImagePreview.visibility = View.GONE
            binding.clearMainImageBtn.visibility = View.GONE
        }
        binding.pickExtraImagesBtn.setOnClickListener {
            if (extraImageUris.size >= 5) {
                Toast.makeText(requireContext(), getString(R.string.write_photo_extra_limit), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickExtraImages.launch("image/*")
        }

        feedTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedFeedType = when (checkedId) {
                    R.id.category_walk_btn -> FeedType.PET_WALKING
                    R.id.category_care_btn -> FeedType.PET_SETTING
                    R.id.category_comm_btn -> FeedType.COMMUNICATION
                    else -> null
                }
            } else if (feedTypeGroup.checkedButtonId == View.NO_ID) {
                selectedFeedType = null
            }
            applyFeedTypeFields(selectedFeedType)
        }

        binding.dateStartBtn.setOnClickListener {
            showDatePickerDialog(isStart = true)
        }
        binding.dateEndBtn.setOnClickListener {
            showDatePickerDialog(isStart = false)
        }

        postBtn.setOnClickListener {
            if (binding.postingOverlay.visibility == View.VISIBLE) return@setOnClickListener

            val title = binding.titleTxt.text.toString().trim()
            val priceStr = binding.priceTxt.text.toString().trim()
            val contents = binding.contentsEdt.text.toString().trim()
            val type = selectedFeedType

            if (title.isEmpty()) {
                Toast.makeText(activity, "제목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (type == null) {
                Toast.makeText(activity, "피드 유형을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val needsPriceDate = type == FeedType.PET_WALKING || type == FeedType.PET_SETTING
            if (needsPriceDate) {
                if (priceStr.isEmpty()) {
                    Toast.makeText(activity, "가격을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (startYear == null || startMonth == null || startDay == null) {
                    Toast.makeText(activity, "시작일을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (endYear == null || endMonth == null || endDay == null) {
                    Toast.makeText(activity, "종료일을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val sk = ymdKey(startYear!!, startMonth!!, startDay!!)
                val ek = ymdKey(endYear!!, endMonth!!, endDay!!)
                if (sk > ek) {
                    Toast.makeText(activity, getString(R.string.write_date_start_after_end), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val pay = if (needsPriceDate) priceStr.toIntOrNull() ?: 0 else 0
            val startIso = if (needsPriceDate) {
                formatIso(startYear!!, startMonth!!, startDay!!)
            } else null
            val endIso = if (needsPriceDate) {
                formatIso(endYear!!, endMonth!!, endDay!!)
            } else null

            val mainUris = mainImageUri?.let { listOf(it) } ?: emptyList()
            val sideUris = extraImageUris.toList()

            viewLifecycleOwner.lifecycleScope.launch {
                val regionId = selectedRegionId ?: authRepository.getDefaultRegionId()
                if (regionId == null) {
                    Toast.makeText(activity, "게시할 지역을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                binding.postingOverlay.visibility = View.VISIBLE
                postBtn.isEnabled = false
                postBtn.alpha = 0.6f
                var resolved = authRepository.getCachedUserInfo()?.nickname
                if (resolved.isNullOrBlank()) {
                    authRepository.fetchMyInfoFromBackend().onSuccess { resolved = it.nickname }
                }
                val nickname = resolved
                if (nickname.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "닉네임을 확인할 수 없습니다. 내 정보를 다시 불러와 주세요.", Toast.LENGTH_SHORT).show()
                    binding.postingOverlay.visibility = View.GONE
                    postBtn.isEnabled = true
                    postBtn.alpha = 1f
                    return@launch
                }
                feedRepository.upsertFeed(
                    userNickname = nickname,
                    title = title,
                    feedType = type,
                    description = contents,
                    pay = pay,
                    regionId = regionId,
                    startDate = startIso,
                    endDate = endIso,
                    mainImageUris = mainUris,
                    sideImageUris = sideUris,
                ).onSuccess {
                    Toast.makeText(activity, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.navigateToHomeTab()
                }.onFailure { e ->
                    Log.e(TAG, "upsertFeed", e)
                    Toast.makeText(activity, e.message ?: "등록에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }.also {
                    binding.postingOverlay.visibility = View.GONE
                    postBtn.isEnabled = true
                    postBtn.alpha = 1f
                }
            }
        }

        applyFeedTypeFields(null)
        updateExtraAttachButtonState()

        binding.writeRegionSelectBtn.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { showRegionPickerDialog() }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch { initDefaultRegion() }
    }

    private suspend fun initDefaultRegion() {
        val defaultId = authRepository.getDefaultRegionId()
        selectedRegionId = defaultId
        val label = defaultId?.let { regionRepository.getRegionDisplayName(it) }
            ?: "게시 지역 선택"
        if (::binding.isInitialized) {
            binding.writeRegionSelectBtn.text = label
        }
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
            .setTitle("게시 지역")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                selectedRegionId = items[which].first
                binding.writeRegionSelectBtn.text = items[which].second
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshExtraThumbnails() {
        val strip = binding.extraImagesStrip
        strip.removeAllViews()
        val thumbPx = resources.getDimensionPixelSize(R.dimen.write_photo_thumb)
        val gap = (8 * resources.displayMetrics.density).toInt()

        extraImageUris.forEach { uri ->
            val cell = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(thumbPx, thumbPx).apply { marginEnd = gap }
            }
            val iv = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
            }
            val close = TextView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.TOP,
                )
                text = "×"
                textSize = 18f
                setPadding(12, 4, 12, 4)
                setBackgroundColor(0x66000000)
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    extraImageUris.remove(uri)
                    refreshExtraThumbnails()
                }
            }
            cell.addView(iv)
            cell.addView(close)
            strip.addView(cell)
        }
        updateExtraAttachButtonState()
    }

    private fun updateExtraAttachButtonState() {
        val canAdd = extraImageUris.size < 5
        binding.pickExtraImagesBtn.isEnabled = canAdd
        binding.pickExtraImagesBtn.alpha = if (canAdd) 1f else 0.5f
    }

    private fun formatIso(y: Int, m: Int, d: Int): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)

    private fun applyFeedTypeFields(type: FeedType?) {
        val needs = type == FeedType.PET_WALKING || type == FeedType.PET_SETTING
        binding.cardPrice.visibility = if (needs) View.VISIBLE else View.GONE
        binding.cardDate.visibility = if (needs) View.VISIBLE else View.GONE
        if (!needs) {
            binding.priceTxt.text?.clear()
            startYear = null
            startMonth = null
            startDay = null
            endYear = null
            endMonth = null
            endDay = null
            binding.dateStartBtn.text = getString(R.string.write_date_start_hint)
            binding.dateEndBtn.text = getString(R.string.write_date_end_hint)
        }
    }

    private fun showDatePickerDialog(isStart: Boolean) {
        val todayStart = todayStartMillis()

        val endMaxMs = if (endYear != null && endMonth != null && endDay != null) {
            atStartOfDayMillis(endYear!!, endMonth!!, endDay!!)
        } else {
            null
        }

        val startMsForEndMin = if (startYear != null && startMonth != null && startDay != null) {
            atStartOfDayMillis(startYear!!, startMonth!!, startDay!!)
        } else {
            null
        }

        val cal = Calendar.getInstance()
        val defaultY = cal.get(Calendar.YEAR)
        val defaultM = cal.get(Calendar.MONTH) + 1
        val defaultD = cal.get(Calendar.DAY_OF_MONTH)

        val (initialY, initialM, initialD) = if (isStart) {
            val (y, m, d) = if (startYear != null) {
                Triple(startYear!!, startMonth!!, startDay!!)
            } else {
                Triple(defaultY, defaultM, defaultD)
            }
            val maxMs = if (endMaxMs != null && endMaxMs >= todayStart) endMaxMs else null
            clampYmdToRange(y, m, d, todayStart, maxMs)
        } else {
            val (y, m, d) = if (endYear != null) {
                Triple(endYear!!, endMonth!!, endDay!!)
            } else {
                Triple(defaultY, defaultM, defaultD)
            }
            val minMs = maxOf(todayStart, startMsForEndMin ?: todayStart)
            clampYmdToRange(y, m, d, minMs, null)
        }

        val dialog = DatePickerDialog(
            requireContext(),
            { _, y, monthOfYear, dayOfMonth ->
                val m = monthOfYear + 1
                val pickedKey = ymdKey(y, m, dayOfMonth)
                if (isStart) {
                    if (endYear != null && endMonth != null && endDay != null) {
                        val endKey = ymdKey(endYear!!, endMonth!!, endDay!!)
                        if (pickedKey > endKey) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.write_date_start_after_end),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@DatePickerDialog
                        }
                    }
                    startYear = y
                    startMonth = m
                    startDay = dayOfMonth
                    binding.dateStartBtn.text = formatIso(y, m, dayOfMonth)
                } else {
                    if (startYear != null && startMonth != null && startDay != null) {
                        val startKey = ymdKey(startYear!!, startMonth!!, startDay!!)
                        if (pickedKey < startKey) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.write_date_end_before_start),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@DatePickerDialog
                        }
                    }
                    endYear = y
                    endMonth = m
                    endDay = dayOfMonth
                    binding.dateEndBtn.text = formatIso(y, m, dayOfMonth)
                }
            },
            initialY,
            initialM - 1,
            initialD,
        )

        val picker = dialog.datePicker
        if (isStart) {
            picker.minDate = todayStart
            if (endMaxMs != null && endMaxMs >= todayStart) {
                picker.maxDate = endMaxMs
            }
        } else {
            picker.minDate = maxOf(todayStart, startMsForEndMin ?: todayStart)
        }
        dialog.show()
    }

    private fun todayStartMillis(): Long =
        Calendar.getInstance().run {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

    private fun atStartOfDayMillis(y: Int, month1Based: Int, d: Int): Long =
        Calendar.getInstance().run {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

    private fun clampYmdToRange(
        y: Int,
        m: Int,
        d: Int,
        minMs: Long,
        maxMs: Long?,
    ): Triple<Int, Int, Int> {
        var ms = atStartOfDayMillis(y, m, d)
        if (ms < minMs) {
            val c = Calendar.getInstance().apply { timeInMillis = minMs }
            return Triple(
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
            )
        }
        if (maxMs != null && ms > maxMs) {
            val c = Calendar.getInstance().apply { timeInMillis = maxMs }
            return Triple(
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
            )
        }
        return Triple(y, m, d)
    }
}
