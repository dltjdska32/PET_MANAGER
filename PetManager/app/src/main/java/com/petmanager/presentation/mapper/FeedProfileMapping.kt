package com.petmanager.presentation.mapper

import com.petmanager.data.remote.dto.FindFeedRespDto
import com.petmanager.domain.model.Profiles
import com.petmanager.R
import com.petmanager.BuildConfig

/** @param likedOverride null이면 서버 [isLiked], 찜 탭 등에서는 true로 고정 가능 */
fun FindFeedRespDto.toProfiles(
    regionLabel: String,
    likedOverride: Boolean? = null,
): Profiles = Profiles(
    work = R.drawable.ic_paw,
    names = authorNickname,
    dong = regionLabel,
    ment = title,
    isFavorite = likedOverride ?: isLiked,
    postID = id,
    deadline = "좋아요 $likesCount",
    thumbnailUrl = mainImgUrl.firstOrNull()?.let { normalizeImageUrl(it) },
)

internal fun normalizeImageUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val base = BuildConfig.SERVER_BASE_URL.trimEnd('/')
    val path = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return base + path
}
