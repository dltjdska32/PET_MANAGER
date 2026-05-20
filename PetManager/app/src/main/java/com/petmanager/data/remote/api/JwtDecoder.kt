package com.petmanager.data.remote.api

import android.util.Base64
import org.json.JSONObject

/**
 * HS512 검증 없이 페이로드만 읽을 때 사용 (로컬 userId 동기화용).
 */
object JwtDecoder {

    fun subject(accessToken: String): String? {
        val parts = accessToken.split('.')
        if (parts.size < 2) return null
        return try {
            val json = String(decodeUrlBase64(parts[1]), Charsets.UTF_8)
            val sub = JSONObject(json).optString("sub", "")
            sub.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUrlBase64(segment: String): ByteArray {
        var s = segment.replace('-', '+').replace('_', '/')
        when (s.length % 4) {
            2 -> s += "=="
            3 -> s += "="
        }
        return Base64.decode(s, Base64.DEFAULT)
    }
}
