package com.petmanager.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * 액세스·리프레시 토큰 저장을 한 곳에서 관리 (AuthRepository / AuthInterceptor 동일 데이터).
 * RTR: reissue 응답으로 항상 둘 다 갱신.
 */
object TokenStorage {

    const val PREFS_NAME = "pet_manager_prefs"
    private const val SECURE_PREFS_NAME = "pet_manager_secure_prefs"
    private const val KEY_ACCESS = "jwt_token"
    private const val KEY_REFRESH = "refresh_token"

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }

    private fun requireCtx(): Context {
        check(::appContext.isInitialized) { "TokenStorage.init(Application) 먼저 호출" }
        return appContext
    }

    fun prefs(): SharedPreferences = requireCtx().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        openEncryptedSharedPreferencesWithRecovery()
    }

    fun accessToken(): String? = prefs().getString(KEY_ACCESS, null)

    fun refreshToken(): String? = securePrefs.getString(KEY_REFRESH, null)

    /**
     * 로그인·reissue 성공 시 호출. commit으로 인터셉터와 즉시 일치.
     */
    fun saveTokenPair(accessToken: String, refreshToken: String) {
        prefs().edit().putString(KEY_ACCESS, accessToken).commit()
        securePrefs.edit().putString(KEY_REFRESH, refreshToken).commit()
    }

    /**
     * 리프레시까지 실패(폐기·만료) 시: 토큰만 제거하고 나머지 prefs는 그대로 둘 수도 있으나,
     * 세션 무효와 동일하게 처리하려면 [clearAllUserPrefs] 사용.
     */
    fun clearCredentialsOnly() {
        prefs().edit().remove(KEY_ACCESS).apply()
        try {
            securePrefs.edit().remove(KEY_REFRESH).apply()
        } catch (_: Exception) {
            prefs().edit().remove(KEY_REFRESH).apply()
        }
    }

    /**
     * 로그아웃·세션 무효 시 사용자 데이터 삭제.
     * [is_region_initialized] 등 앱 설치 상태 플래그는 유지 (Room 지역 시드 재실행 방지).
     */
    fun clearAllUserPrefs() {
        val p = prefs()
        val keepRegionInit = p.getBoolean("is_region_initialized", false)
        p.edit().clear().commit()
        p.edit().putBoolean("is_region_initialized", keepRegionInit).commit()
        try {
            securePrefs.edit().clear().commit()
        } catch (_: Exception) {
        }
    }

    private fun openEncryptedSharedPreferencesWithRecovery(): SharedPreferences {
        try {
            return buildEncryptedSharedPreferences()
        } catch (e: Exception) {
            android.util.Log.w(
                "TokenStorage",
                "암호화 prefs 열기 실패, Keystore 초기화 후 재시도",
                e
            )
        }
        try {
            wipeCorruptedSecurePrefs()
            return buildEncryptedSharedPreferences()
        } catch (e: Exception) {
            android.util.Log.w(
                "TokenStorage",
                "재시도 실패 — refresh_token을 일반 prefs에 저장합니다",
                e
            )
            return prefs()
        }
    }

    private fun buildEncryptedSharedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(requireCtx())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            requireCtx(),
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun wipeCorruptedSecurePrefs() {
        try {
            requireCtx().deleteSharedPreferences(SECURE_PREFS_NAME)
        } catch (_: Exception) {
        }
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (_: Exception) {
        }
    }
}
