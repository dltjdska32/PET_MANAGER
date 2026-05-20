package com.petmanager.presentation.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.petmanager.R
import com.petmanager.data.remote.api.AuthEventBus
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    /** Success / NeedRegion 중복 처리·LiveData 재전달로 인한 이중 전환 방지 */
    private var authNavigationConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        
        authNavigationConsumed = savedInstanceState?.getBoolean(STATE_AUTH_NAV_DONE) == true
        // 로그인 상태 초기화
        authViewModel.resetState()

        // 카카오 로그인 버튼 — SDK 전환 전부터 전체 덮개로 로그인 폼이 보이지 않게 함
        findViewById<View>(R.id.signInButton).setOnClickListener {
            setAuthBlockingOverlay(true)
            authViewModel.loginWithKakao(this)
        }

        // 일반 로그인 버튼 (기존 기능 유지)
        findViewById<android.widget.Button>(R.id.loginButton).setOnClickListener {
            val username = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.usernameInput).text.toString()
            val password = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.passwordInput).text.toString()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.login(username, password)
            } else {
                android.widget.Toast.makeText(this, "아이디와 비밀번호를 입력해주세요", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 회원가입 링크
        findViewById<android.widget.TextView>(R.id.signUpLink).setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        setupObservers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AUTH_NAV_DONE, authNavigationConsumed)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { state ->
                    when (state) {
                        is AuthViewModel.LoginState.Loading -> {
                            authNavigationConsumed = false
                            setAuthBlockingOverlay(true)
                        }
                        is AuthViewModel.LoginState.Success -> {
                            if (authNavigationConsumed) return@collect
                            authNavigationConsumed = true
                            setAuthBlockingOverlay(false)
                            navigateToMain()
                        }
                        is AuthViewModel.LoginState.NeedRegionSetting -> {
                            if (authNavigationConsumed) return@collect
                            authNavigationConsumed = true
                            val intent = Intent(this@SignInActivity, RegionSelectionActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            val opts = ActivityOptionsCompat.makeCustomAnimation(this@SignInActivity, 0, 0)
                            startActivity(intent, opts.toBundle())
                            finish()
                            overridePendingTransition(0, 0)
                        }
                        is AuthViewModel.LoginState.Error -> {
                            setAuthBlockingOverlay(false)
                            android.widget.Toast.makeText(this@SignInActivity, state.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            setAuthBlockingOverlay(false)
                        }
                    }
                }
            }
        }
    }

    private fun setAuthBlockingOverlay(visible: Boolean) {
        findViewById<FrameLayout>(R.id.authBlockingOverlay).visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    companion object {
        private const val STATE_AUTH_NAV_DONE = "sign_in_auth_nav_done"
    }

    private fun navigateToMain() {
        // 새 로그인 성공 시, 이후 세션 만료 이벤트가 다시 동작하도록 플래그 리셋
        AuthEventBus.resetSessionExpiredFlag()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(
            intent,
            ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
