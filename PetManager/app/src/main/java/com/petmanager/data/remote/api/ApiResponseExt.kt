package com.petmanager.data.remote.api

/**
 * 백엔드 [com.petmanager.config.Response] JSON:
 * - record 이므로 Jackson 기본 직렬화 시 statusCode 는 숫자(200)일 수 있음.
 * - [code] 가 "SUCCESS" 이면 성공으로 간주.
 */
fun <T> ApiResponse<T>?.isApiSuccess(): Boolean {
    if (this == null) return false
    if (code == "SUCCESS") return true
    if (statusCode == "OK") return true
    val sc = statusCode?.toString() ?: return false
    return sc == "200" || sc.equals("OK", ignoreCase = true)
}
