package com.petmanager.data.remote.dto

/** 피드 목록·좋아요 목록 조회 응답 (FindFeedRespDto) */
data class FindFeedRespDto(
    val id: String,
    val title: String,
    val mainImgUrl: List<String>,
    val likesCount: Int,
    val regionId: Long,
    val authorNickname: String,
    val isLiked: Boolean = false,
)

/** Spring Data Slice JSON (필요한 필드만 매핑) */
data class FeedSliceDto<T>(
    val content: List<T> = emptyList(),
    val last: Boolean = true,
    val first: Boolean? = null,
    val size: Int? = null,
    val number: Int? = null,
    val numberOfElements: Int? = null,
    val empty: Boolean? = null,
)

/** 피드 상세 (백엔드 Feed 문서 역직렬화) */
data class FeedDetailDto(
    val id: String? = null,
    val authorId: String,
    val username: String,
    val authorNickname: String,
    val title: String,
    val description: String,
    val regionId: Long,
    val mainImgUrl: List<String> = emptyList(),
    val sideImgUrl: List<String> = emptyList(),
    val pay: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val feedType: String,
    val searchTokens: List<String>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isDeleted: Boolean = false,
)

enum class FeedSortFlag {
    CREATED_AT,
    LIKE_COUNT,
}
