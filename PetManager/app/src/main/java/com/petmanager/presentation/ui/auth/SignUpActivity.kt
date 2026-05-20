package com.petmanager.presentation.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.petmanager.R
import com.petmanager.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignUpActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    val signUpData = SignUpData()

    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private var isSignUpInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = SignUpPagerAdapter(this)
        viewPager.isUserInputEnabled = false

        findViewById<android.widget.ImageButton>(R.id.backButton).setOnClickListener {
            goToPrevStep()
        }

        observeSignUpState()
    }

    fun goToNextStep() {
        val current = viewPager.currentItem
        if (current < 2) {
            viewPager.currentItem = current + 1
            updateIndicators(current + 1)
        }
    }

    fun goToPrevStep() {
        val current = viewPager.currentItem
        if (current > 0) {
            viewPager.currentItem = current - 1
            updateIndicators(current - 1)
        } else {
            finish()
        }
    }

    fun completeSignUp() {
        if (isSignUpInProgress) return

        val data = signUpData

        if (!data.isEmailVerified || data.email.isBlank()) {
            Toast.makeText(this, "이메일 인증을 완료해주세요", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 0
            updateIndicators(0)
            return
        }

        if (!data.isUsernameChecked || data.username.isBlank()) {
            Toast.makeText(this, "아이디 중복 확인을 해주세요", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 1
            updateIndicators(1)
            return
        }

        if (data.nickname.isBlank() || data.password.isBlank()) {
            Toast.makeText(this, "닉네임과 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 1
            updateIndicators(1)
            return
        }

        val regionIds = data.regionIds
        if (regionIds.isNullOrEmpty()) {
            Toast.makeText(this, "최소 1개 이상의 지역을 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        isSignUpInProgress = true
        authViewModel.signUp(
            nickName = data.nickname,
            username = data.username,
            password = data.password,
            email = data.email,
            regionIds = regionIds,
        )
    }

    private fun observeSignUpState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { state ->
                    if (!isSignUpInProgress) return@collect
                    when (state) {
                        is AuthViewModel.LoginState.Loading -> Unit
                        is AuthViewModel.LoginState.Success -> {
                            isSignUpInProgress = false
                            authViewModel.resetState()
                            Toast.makeText(
                                this@SignUpActivity,
                                "회원가입이 완료되었습니다. 로그인해주세요.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            val nextIntent = Intent(this@SignUpActivity, SignInActivity::class.java)
                            nextIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(nextIntent)
                            finish()
                        }
                        is AuthViewModel.LoginState.Error -> {
                            isSignUpInProgress = false
                            Toast.makeText(
                                this@SignUpActivity,
                                state.message,
                                Toast.LENGTH_LONG,
                            ).show()
                            authViewModel.resetState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun isSignUpSubmitting(): Boolean = isSignUpInProgress

    private fun updateIndicators(step: Int) {
        findViewById<android.view.View>(R.id.step1Indicator).setBackgroundResource(
            if (step >= 0) R.drawable.step_indicator_active else R.drawable.step_indicator_inactive,
        )
        findViewById<android.view.View>(R.id.step2Indicator).setBackgroundResource(
            if (step >= 1) R.drawable.step_indicator_active else R.drawable.step_indicator_inactive,
        )
        findViewById<android.view.View>(R.id.step3Indicator).setBackgroundResource(
            if (step >= 2) R.drawable.step_indicator_active else R.drawable.step_indicator_inactive,
        )
    }

    private inner class SignUpPagerAdapter(activity: androidx.fragment.app.FragmentActivity) :
        androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when (position) {
                0 -> SignUpStep1Fragment()
                1 -> SignUpStep2Fragment()
                2 -> SignUpStep3Fragment()
                else -> SignUpStep1Fragment()
            }
        }
    }
}
