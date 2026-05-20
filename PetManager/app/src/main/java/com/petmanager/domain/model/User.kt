package com.petmanager.domain.model

data class User(
    val id: String,
    val nickname: String?,
    val email: String?,
    val profileImageUrl: String?,
    val regionIds: List<Long> = emptyList() // 단수 regionId에서 복수 regionIds로 변경
) {
    // 지역 정보가 설정되어 있는지 확인하는 헬퍼 프로퍼티
    val hasRegions: Boolean get() = regionIds.isNotEmpty()
}
