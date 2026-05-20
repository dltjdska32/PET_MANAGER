package com.petmanager.presentation.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.petmanager.presentation.ui.auth.RegionSelectionActivity
import com.petmanager.presentation.ui.auth.SignInActivity
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.presentation.viewmodel.AuthViewModel
import com.petmanager.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 애니메이션 적용
        animateSplash()

        // 로그인 상태 체크 시작 (JWT 토큰 유효성 검사)
        authViewModel.checkLoginStatus()

        // 최소 2초간 스플래시 화면 표시
        val splashStartTime = System.currentTimeMillis()
        val minSplashDuration = 2000L

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { state ->
                    if (hasNavigated) return@collect

                    val elapsedTime = System.currentTimeMillis() - splashStartTime
                    val remainingTime = minSplashDuration - elapsedTime

                    when (state) {
                        is AuthViewModel.LoginState.Success -> {
                            val delay = if (remainingTime > 0) remainingTime else 0
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!hasNavigated) {
                                    hasNavigated = true
                                    navigateToMain()
                                }
                            }, delay)
                        }
                        is AuthViewModel.LoginState.NeedRegionSetting -> {
                            val delay = if (remainingTime > 0) remainingTime else 0
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!hasNavigated) {
                                    hasNavigated = true
                                    val intent = Intent(this@SplashActivity, RegionSelectionActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                            }, delay)
                        }
                        is AuthViewModel.LoginState.Idle,
                        is AuthViewModel.LoginState.Error -> {
                            val delay = if (remainingTime > 0) remainingTime else 0
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!hasNavigated) {
                                    hasNavigated = true
                                    navigateToSignIn()
                                }
                            }, delay)
                        }
                        is AuthViewModel.LoginState.Loading -> {
                        }
                    }
                }
            }
        }
    }

    private fun animateSplash() {
        val logoImage = findViewById<ImageView>(R.id.image_view)
        val titleText = findViewById<TextView>(R.id.text_view)
        val subtitleText = findViewById<TextView>(R.id.subtitle_text)

        // 로고 페이드인 + 스케일
        logoImage.alpha = 0f
        logoImage.scaleX = 0.8f
        logoImage.scaleY = 0.8f

        logoImage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 타이틀 페이드인
        titleText.alpha = 0f
        titleText.translationY = 30f
        titleText.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(400)
            .setDuration(600)
            .start()

        // 서브타이틀 페이드인
        subtitleText?.let {
            it.alpha = 0f
            it.translationY = 20f
            it.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(600)
                .setDuration(500)
                .start()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

