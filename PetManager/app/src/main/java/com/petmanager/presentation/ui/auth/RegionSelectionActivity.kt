package com.petmanager.presentation.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.petmanager.R
import com.petmanager.data.local.entity.RegionEntity
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.presentation.viewmodel.RegionSelectionEffect
import com.petmanager.presentation.viewmodel.RegionSelectionViewModel
import com.petmanager.presentation.viewmodel.RegionSelectionViewModel.Companion.KEY_MODE
import com.petmanager.presentation.viewmodel.RegionSelectionViewModel.Companion.MODE_EDIT
import com.petmanager.presentation.viewmodel.RegionSelectionViewModel.Mode
import com.petmanager.presentation.viewmodel.RegionSelectionViewModel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegionSelectionActivity : AppCompatActivity() {

    private val viewModel: RegionSelectionViewModel by viewModels()

    private lateinit var sidoRecyclerView: RecyclerView
    private lateinit var regionRecyclerView: RecyclerView
    private lateinit var sidoAdapter: SidoListAdapter
    private lateinit var regionAdapter: RegionListAdapter
    private lateinit var startButton: MaterialButton
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var pageTitle: TextView
    private lateinit var welcomeTitle: TextView

    private val isEditMode: Boolean
        get() = intent.getStringExtra(KEY_MODE) == MODE_EDIT

    companion object {
        fun editIntent(context: Context): Intent =
            Intent(context, RegionSelectionActivity::class.java).apply {
                putExtra(KEY_MODE, MODE_EDIT)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContentView(R.layout.activity_region_selection)

        sidoRecyclerView = findViewById(R.id.sidoRecyclerView)
        regionRecyclerView = findViewById(R.id.regionRecyclerView)
        startButton = findViewById(R.id.startButton)
        progressBar = findViewById(R.id.regionProgressBar)
        pageTitle = findViewById(R.id.pageTitle)
        welcomeTitle = findViewById(R.id.welcomeTitle)

        applyModeCopy()

        findViewById<android.widget.ImageButton>(R.id.backButton)
            .setOnClickListener { handleBack() }

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = handleBack()
            }
        )

        setupRecyclerViews()
        setupListeners()
        observeState()
        observeEffects()
    }

    private fun applyModeCopy() {
        if (isEditMode) {
            pageTitle.text = "지역 수정"
            welcomeTitle.text = "변경할 활동 지역을 다시 선택해 주세요.\n최대 3개까지 선택 가능합니다."
            startButton.text = "수정 완료"
        } else {
            pageTitle.text = "지역 설정"
            welcomeTitle.text = "우쭈쭈 이용을 위해 활동할 지역을 선택해 주세요. \n최대 3개까지 선택 가능합니다."
            startButton.text = "설정 완료"
        }
    }

    private fun handleBack() {
        if (isEditMode) finish() else goBackToSignIn()
    }

    private fun setupRecyclerViews() {
        sidoAdapter = SidoListAdapter { sidoId -> viewModel.onSidoSelected(sidoId) }
        sidoRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        sidoRecyclerView.adapter = sidoAdapter

        regionAdapter = RegionListAdapter { regionId -> viewModel.onRegionToggle(regionId) }
        regionRecyclerView.layoutManager = LinearLayoutManager(this)
        regionRecyclerView.adapter = regionAdapter
    }

    private fun setupListeners() {
        startButton.setOnClickListener { viewModel.submit() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun observeEffects() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { handleEffect(it) }
            }
        }
    }

    private fun render(state: UiState) {
        progressBar.visibility = if (state.isLoading || state.isSubmitting) View.VISIBLE else View.GONE
        sidoAdapter.submitList(state.sidoList.map { SidoItem(it, it.id == state.selectedSidoId) })
        regionAdapter.submitList(state.sigunguList.map { RegionItem(it, it.id in state.selectedRegionIds) })
        startButton.isEnabled = state.isSubmittable
    }

    private fun handleEffect(effect: RegionSelectionEffect) {
        when (effect) {
            RegionSelectionEffect.SaveSuccessInitial -> goToMain()
            RegionSelectionEffect.SaveSuccessEdit -> {
                Toast.makeText(this, "지역 정보가 수정되었습니다", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            is RegionSelectionEffect.ShowMessage ->
                Toast.makeText(this, effect.text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    private fun goBackToSignIn() {
        val intent = Intent(this, SignInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    // region Adapters (ListAdapter + DiffUtil)

    private data class SidoItem(val region: RegionEntity, val isSelected: Boolean)

    private class SidoDiff : DiffUtil.ItemCallback<SidoItem>() {
        override fun areItemsTheSame(oldItem: SidoItem, newItem: SidoItem): Boolean =
            oldItem.region.id == newItem.region.id

        override fun areContentsTheSame(oldItem: SidoItem, newItem: SidoItem): Boolean =
            oldItem == newItem
    }

    private class SidoListAdapter(
        private val onItemClick: (Long) -> Unit,
    ) : ListAdapter<SidoItem, SidoListAdapter.VH>(SidoDiff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_region_sido, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), onItemClick)
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val regionName = itemView.findViewById<TextView>(R.id.regionName)
            private val container = itemView.findViewById<View>(R.id.container)

            fun bind(item: SidoItem, onItemClick: (Long) -> Unit) {
                regionName.text = item.region.regionName
                if (item.isSelected) {
                    container.setBackgroundResource(R.drawable.bg_sido_selected)
                    regionName.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                } else {
                    container.setBackgroundResource(R.drawable.bg_sido_unselected)
                    regionName.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                }
                itemView.setOnClickListener { onItemClick(item.region.id) }
            }
        }
    }

    private data class RegionItem(val region: RegionEntity, val isSelected: Boolean)

    private class RegionDiff : DiffUtil.ItemCallback<RegionItem>() {
        override fun areItemsTheSame(oldItem: RegionItem, newItem: RegionItem): Boolean =
            oldItem.region.id == newItem.region.id

        override fun areContentsTheSame(oldItem: RegionItem, newItem: RegionItem): Boolean =
            oldItem == newItem
    }

    private class RegionListAdapter(
        private val onItemClick: (Long) -> Unit,
    ) : ListAdapter<RegionItem, RegionListAdapter.VH>(RegionDiff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_region, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), onItemClick)
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val regionName = itemView.findViewById<TextView>(R.id.regionName)
            private val checkIcon = itemView.findViewById<TextView>(R.id.checkIcon)

            fun bind(item: RegionItem, onItemClick: (Long) -> Unit) {
                regionName.text = item.region.regionName
                checkIcon.visibility = if (item.isSelected) View.VISIBLE else View.GONE
                itemView.setOnClickListener { onItemClick(item.region.id) }
            }
        }
    }

    // endregion
}
