package com.petmanager

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.petmanager.data.local.TokenStorage
import com.petmanager.data.repository.RegionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PetManagerApplication : Application() {

    @Inject lateinit var regionRepository: RegionRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        TokenStorage.init(this)

        // 카카오 SDK 초기화 (네이티브 앱 키)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // 지역 마스터 데이터 — DB 비어 있으면 매번 시드 (마이그레이션 후에도 복구)
        applicationScope.launch {
            regionRepository.initializeRegionsIfNeeded()
        }
    }
}



