package com.petmanager.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petmanager.data.local.entity.RegionEntity
import com.petmanager.data.repository.AuthRepository
import com.petmanager.data.repository.RegionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 지역 설정/수정 화면의 상태를 단일 StateFlow 로 관리 (UDF).
 *
 * 프로세스 사망·구성변경 후에도 동일하게 복구되도록 [SavedStateHandle] 을 통해 `mode` 를 보존한다.
 * Activity 에서 extras 로 넘긴 값은 `DEFAULT_ARGS` 로 자동 주입되므로 별도 초기화 호출이 필요 없다.
 *
 * - Initial 모드: 가입 직후 최초 지역 설정. 성공 시 [RegionSelectionEffect.SaveSuccessInitial].
 * - Edit 모드: 저장된 지역 프리셀렉트 후 diff(add/delete) 전송. 성공 시 [RegionSelectionEffect.SaveSuccessEdit].
 */
@HiltViewModel
class RegionSelectionViewModel @Inject constructor(
    private val regionRepository: RegionRepository,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    enum class Mode { Initial, Edit }

    companion object {
        const val MAX_SELECTABLE_REGIONS = 3
        const val KEY_MODE = "extra_mode"
        const val MODE_INITIAL = "INITIAL"
        const val MODE_EDIT = "EDIT"

        private const val STATE_SELECTED_SIDO_ID = "selected_sido_id"
        private const val STATE_SELECTED_REGION_IDS = "selected_region_ids"
        private const val STATE_INITIAL_REGION_IDS = "initial_region_ids"
    }

    data class UiState(
        val mode: Mode = Mode.Initial,
        val isLoading: Boolean = false,
        val isSubmitting: Boolean = false,
        val sidoList: List<RegionEntity> = emptyList(),
        val selectedSidoId: Long? = null,
        val sigunguList: List<RegionEntity> = emptyList(),
        val initialRegionIds: Set<Long> = emptySet(),
        val selectedRegionIds: Set<Long> = emptySet(),
    ) {
        val pendingAdd: List<Long> get() = (selectedRegionIds - initialRegionIds).toList()
        val pendingDelete: List<Long> get() = (initialRegionIds - selectedRegionIds).toList()
        val hasChanges: Boolean get() = pendingAdd.isNotEmpty() || pendingDelete.isNotEmpty()
        val isSubmittable: Boolean
            get() = selectedRegionIds.isNotEmpty() &&
                selectedRegionIds.size <= MAX_SELECTABLE_REGIONS &&
                !isSubmitting
    }

    private val mode: Mode = when (savedStateHandle.get<String>(KEY_MODE)) {
        MODE_EDIT -> Mode.Edit
        else -> Mode.Initial
    }

    private val _state = MutableStateFlow(UiState(mode = mode))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<RegionSelectionEffect>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<RegionSelectionEffect> = _effect.asSharedFlow()

    init {
        bootstrap()
    }

    private fun bootstrap() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                regionRepository.initializeRegionsIfNeeded()
                val sidoList = regionRepository.getAllSido()
                if (sidoList.isEmpty()) {
                    _state.update { it.copy(isLoading = false) }
                    _effect.tryEmit(RegionSelectionEffect.ShowMessage("지역 정보를 불러오지 못했습니다"))
                    return@launch
                }

                // 저장된 상태가 있으면 복구(프로세스 사망 → 재생성 시나리오)
                val restoredSelected = savedStateHandle.get<LongArray>(STATE_SELECTED_REGION_IDS)
                    ?.toSet()
                val restoredInitial = savedStateHandle.get<LongArray>(STATE_INITIAL_REGION_IDS)
                    ?.toSet()
                val restoredSidoId = savedStateHandle.get<Long>(STATE_SELECTED_SIDO_ID)

                val (initialIds, defaultSidoId) = when {
                    restoredInitial != null -> restoredInitial to (restoredSidoId ?: sidoList.first().id)
                    mode == Mode.Edit -> loadSavedUserRegions(sidoList)
                    else -> emptySet<Long>() to sidoList.first().id
                }
                val selectedIds = restoredSelected ?: initialIds
                val sigunguList = regionRepository.getSigunguBySido(defaultSidoId)

                _state.update {
                    it.copy(
                        isLoading = false,
                        sidoList = sidoList,
                        selectedSidoId = defaultSidoId,
                        sigunguList = sigunguList,
                        initialRegionIds = initialIds,
                        selectedRegionIds = selectedIds,
                    )
                }
                persistInitial(initialIds)
                persistSelection(selectedIds)
                persistSido(defaultSidoId)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                _effect.tryEmit(RegionSelectionEffect.ShowMessage("지역 정보를 불러오는데 실패했습니다"))
            }
        }
    }

    private suspend fun loadSavedUserRegions(
        sidoList: List<RegionEntity>,
    ): Pair<Set<Long>, Long> {
        val savedIds = authRepository.getCachedUserInfo()?.regionIds.orEmpty().toSet()
        if (savedIds.isEmpty()) {
            return emptySet<Long>() to sidoList.first().id
        }
        val firstRegion = regionRepository.getRegionById(savedIds.first())
        val sidoId = firstRegion?.parentId ?: firstRegion?.id ?: sidoList.first().id
        return savedIds to sidoId
    }

    fun onSidoSelected(sidoId: Long) {
        if (_state.value.selectedSidoId == sidoId) return
        _state.update { it.copy(selectedSidoId = sidoId, sigunguList = emptyList()) }
        persistSido(sidoId)
        viewModelScope.launch {
            try {
                val sigunguList = regionRepository.getSigunguBySido(sidoId)
                _state.update { it.copy(sigunguList = sigunguList) }
            } catch (e: Exception) {
                _effect.tryEmit(RegionSelectionEffect.ShowMessage("시군구 정보를 불러오는데 실패했습니다"))
            }
        }
    }

    fun onRegionToggle(regionId: Long) {
        val current = _state.value.selectedRegionIds
        val next = if (regionId in current) {
            current - regionId
        } else {
            if (current.size >= MAX_SELECTABLE_REGIONS) {
                _effect.tryEmit(
                    RegionSelectionEffect.ShowMessage("최대 ${MAX_SELECTABLE_REGIONS}개까지 선택 가능합니다")
                )
                return
            }
            current + regionId
        }
        _state.update { it.copy(selectedRegionIds = next) }
        persistSelection(next)
    }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        if (s.selectedRegionIds.isEmpty()) {
            _effect.tryEmit(RegionSelectionEffect.ShowMessage("최소 1개 이상의 지역을 선택해주세요"))
            return
        }
        if (s.selectedRegionIds.size > MAX_SELECTABLE_REGIONS) {
            _effect.tryEmit(
                RegionSelectionEffect.ShowMessage("최대 ${MAX_SELECTABLE_REGIONS}개까지 선택 가능합니다")
            )
            return
        }

        val addIds: List<Long>
        val deleteIds: List<Long>

        when (s.mode) {
            Mode.Initial -> {
                addIds = s.selectedRegionIds.toList()
                deleteIds = emptyList()
            }
            Mode.Edit -> {
                addIds = s.pendingAdd
                deleteIds = s.pendingDelete
                if (addIds.isEmpty() && deleteIds.isEmpty()) {
                    _effect.tryEmit(RegionSelectionEffect.ShowMessage("변경된 지역이 없습니다"))
                    return
                }
            }
        }

        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = authRepository.updateUserRegions(
                addRegionIds = addIds.ifEmpty { null },
                deleteRegionIds = deleteIds.ifEmpty { null },
            )
            _state.update { it.copy(isSubmitting = false) }
            result.onSuccess {
                _effect.tryEmit(
                    if (s.mode == Mode.Edit) RegionSelectionEffect.SaveSuccessEdit
                    else RegionSelectionEffect.SaveSuccessInitial
                )
            }.onFailure { e ->
                _effect.tryEmit(
                    RegionSelectionEffect.ShowMessage(e.message ?: "지역 설정 업데이트 실패")
                )
            }
        }
    }

    private fun persistSelection(ids: Set<Long>) {
        savedStateHandle[STATE_SELECTED_REGION_IDS] = ids.toLongArray()
    }

    private fun persistInitial(ids: Set<Long>) {
        savedStateHandle[STATE_INITIAL_REGION_IDS] = ids.toLongArray()
    }

    private fun persistSido(id: Long) {
        savedStateHandle[STATE_SELECTED_SIDO_ID] = id
    }
}

sealed interface RegionSelectionEffect {
    data object SaveSuccessInitial : RegionSelectionEffect
    data object SaveSuccessEdit : RegionSelectionEffect
    data class ShowMessage(val text: String) : RegionSelectionEffect
}
