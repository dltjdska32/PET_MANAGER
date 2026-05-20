package com.petmanager.presentation.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.petmanager.R
import com.petmanager.data.local.entity.RegionEntity
import com.petmanager.data.repository.RegionRepository
import com.petmanager.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignUpStep3Fragment : Fragment() {
    
    private val authViewModel: AuthViewModel by activityViewModels()
    private var signUpData: SignUpData? = null

    @Inject lateinit var regionRepository: RegionRepository
    private lateinit var sidoRecyclerView: RecyclerView
    private lateinit var regionRecyclerView: RecyclerView
    private lateinit var sidoAdapter: SidoAdapter
    private lateinit var regionAdapter: RegionAdapter
    private lateinit var signUpButton: MaterialButton
    
    private val selectedRegions = mutableSetOf<Long>()
    private var selectedSidoId: Long? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_signup_step3, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        signUpData = (activity as? SignUpActivity)?.signUpData
        sidoRecyclerView = view.findViewById(R.id.sidoRecyclerView)
        regionRecyclerView = view.findViewById(R.id.regionRecyclerView)
        signUpButton = view.findViewById(R.id.signUpButton)
        
        setupRecyclerViews()
        loadSidoData()
        setupListeners()
        observeSignUpState()

        // 저장된 선택 지역 복원
        signUpData?.regionIds?.let {
            selectedRegions.addAll(it)
            updateSignUpButtonState()
        }
    }
    
    private fun setupRecyclerViews() {
        // 시도 Adapter 설정
        sidoAdapter = SidoAdapter(emptyList()) { sidoId ->
            selectedSidoId = sidoId
            loadSigunguData(sidoId)
        }
        sidoRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        sidoRecyclerView.adapter = sidoAdapter
        
        // 시군구 Adapter 설정
        regionAdapter = RegionAdapter(emptyList(), selectedRegions) { regionId ->
            if (selectedRegions.contains(regionId)) {
                selectedRegions.remove(regionId)
            } else {
                if (selectedRegions.size >= 3) {
                    Toast.makeText(requireContext(), "최대 3개까지 선택 가능합니다", Toast.LENGTH_SHORT).show()
                    return@RegionAdapter
                }
                selectedRegions.add(regionId)
            }
            regionAdapter.notifyDataSetChanged()
            updateSignUpButtonState()
        }
        
        regionRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        regionRecyclerView.adapter = regionAdapter
    }
    
    private fun loadSidoData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                regionRepository.initializeRegionsIfNeeded()
                val sidoList = regionRepository.getAllSido()
                sidoAdapter.updateRegions(sidoList)
                
                // 첫 번째 시도 자동 선택
                if (sidoList.isNotEmpty()) {
                    val firstSidoId = sidoList[0].id
                    selectedSidoId = firstSidoId
                    sidoAdapter.setSelectedSido(firstSidoId)
                    loadSigunguData(firstSidoId)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "지역 정보를 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadSigunguData(sidoId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val sigunguList = regionRepository.getSigunguBySido(sidoId)
                regionAdapter.updateRegions(sigunguList)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "시군구 정보를 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun observeSignUpState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { state ->
                    val submitting = (activity as? SignUpActivity)?.isSignUpSubmitting() == true
                    when (state) {
                        is AuthViewModel.LoginState.Loading -> {
                            if (submitting) {
                                signUpButton.isEnabled = false
                                signUpButton.text = "가입 중..."
                            }
                        }
                        else -> {
                            if (submitting) return@collect
                            updateSignUpButtonState()
                            signUpButton.text = "회원가입"
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        signUpButton.setOnClickListener {
            if (selectedRegions.isEmpty()) {
                Toast.makeText(requireContext(), "최소 1개 이상의 지역을 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedRegions.size > 3) {
                Toast.makeText(requireContext(), "최대 3개까지 선택 가능합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            signUpData?.regionIds = selectedRegions.toList()
            (activity as? SignUpActivity)?.completeSignUp()
        }
    }
    
    private fun updateSignUpButtonState() {
        signUpButton.isEnabled = selectedRegions.isNotEmpty() && selectedRegions.size <= 3
    }
    
    // 시도 Adapter (단일 선택)
    inner class SidoAdapter(
        private var regions: List<RegionEntity>,
        private val onItemClick: (Long) -> Unit
    ) : RecyclerView.Adapter<SidoAdapter.SidoViewHolder>() {
        
        private var selectedId: Long? = null
        
        fun updateRegions(newRegions: List<RegionEntity>) {
            regions = newRegions
            notifyDataSetChanged()
        }
        
        fun setSelectedSido(id: Long) {
            selectedId = id
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_region_sido, parent, false) // 새로운 레이아웃 필요
            return SidoViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: SidoViewHolder, position: Int) {
            val region = regions[position]
            holder.bind(region, region.id == selectedId)
        }
        
        override fun getItemCount() = regions.size
        
        inner class SidoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val regionName = itemView.findViewById<TextView>(R.id.regionName)
            private val container = itemView.findViewById<View>(R.id.container)
            
            fun bind(region: RegionEntity, isSelected: Boolean) {
                regionName.text = region.regionName
                
                if (isSelected) {
                    container.setBackgroundResource(R.drawable.bg_sido_selected)
                    regionName.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                } else {
                    container.setBackgroundResource(R.drawable.bg_sido_unselected)
                    regionName.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                }
                
                itemView.setOnClickListener {
                    selectedId = region.id
                    notifyDataSetChanged()
                    onItemClick(region.id)
                }
            }
        }
    }
    
    // 시군구 Adapter (다중 선택)
    inner class RegionAdapter(
        private var regions: List<RegionEntity>,
        private val selectedRegions: MutableSet<Long>,
        private val onItemClick: (Long) -> Unit
    ) : RecyclerView.Adapter<RegionAdapter.RegionViewHolder>() {
        
        fun updateRegions(newRegions: List<RegionEntity>) {
            regions = newRegions
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_region, parent, false)
            return RegionViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: RegionViewHolder, position: Int) {
            val region = regions[position]
            holder.bind(region, selectedRegions.contains(region.id))
        }
        
        override fun getItemCount() = regions.size
        
        inner class RegionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val regionName = itemView.findViewById<TextView>(R.id.regionName)
            private val checkIcon = itemView.findViewById<TextView>(R.id.checkIcon)
            
            fun bind(region: RegionEntity, isSelected: Boolean) {
                regionName.text = region.regionName
                checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                itemView.setOnClickListener {
                    onItemClick(region.id)
                }
            }
        }
    }
}
