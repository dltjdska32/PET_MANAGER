package com.petmanager.presentation.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.petmanager.R
import com.petmanager.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class SignUpStep1Fragment : Fragment() {
    
    private val authViewModel: AuthViewModel by activityViewModels()
    private var signUpData: SignUpData? = null
    
    private lateinit var emailLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var emailVerifyButton: MaterialButton
    private lateinit var verifyContainer: View
    private lateinit var verifyCodeInput: TextInputEditText
    private lateinit var confirmVerifyButton: MaterialButton
    private lateinit var timerText: TextView
    private lateinit var nextButton: MaterialButton
    
    private var verificationTimer: CountDownTimer? = null
    private var isEmailVerificationSent = false
    private var isEmailVerificationConfirming = false
    private var lastVerifiedEmail: String? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_signup_step1, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Activity에서 데이터를 가져옴 (원복)
        signUpData = (activity as? SignUpActivity)?.signUpData
        
        emailLayout = view.findViewById(R.id.emailLayout)
        emailInput = view.findViewById(R.id.emailInput)
        emailVerifyButton = view.findViewById(R.id.emailVerifyButton)
        verifyContainer = view.findViewById(R.id.verifyContainer)
        verifyCodeInput = view.findViewById(R.id.verifyCodeInput)
        confirmVerifyButton = view.findViewById(R.id.confirmVerifyButton)
        timerText = view.findViewById(R.id.timerText)
        nextButton = view.findViewById(R.id.nextButton)
        
        setupListeners()
        setupObservers()
        setupEmailWatcher()
        
        // 이미 인증된 경우 상태 복원
        signUpData?.let {
            if (it.isEmailVerified && it.email.isNotEmpty()) {
                emailInput.setText(it.email)
                lastVerifiedEmail = it.email
                emailInput.isEnabled = false
                emailVerifyButton.isEnabled = false
                emailVerifyButton.text = "완료"
                verifyContainer.visibility = View.GONE
                nextButton.isEnabled = true
            }
        }
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { state ->
                    when (state) {
                        is AuthViewModel.LoginState.Idle -> {
                            if (isEmailVerificationSent) {
                                Toast.makeText(requireContext(), "인증 코드가 전송되었습니다", Toast.LENGTH_SHORT).show()
                                showVerificationInput()
                                isEmailVerificationSent = false
                            }
                        }
                        is AuthViewModel.LoginState.Success -> {
                            if (isEmailVerificationConfirming) {
                                val verifiedEmail = emailInput.text.toString().trim()
                                signUpData?.isEmailVerified = true
                                signUpData?.email = verifiedEmail
                                lastVerifiedEmail = verifiedEmail
                                updateEmailVerifyStatus(true)
                                Toast.makeText(requireContext(), "이메일 인증이 완료되었습니다", Toast.LENGTH_SHORT).show()
                                isEmailVerificationConfirming = false
                            }
                        }
                        is AuthViewModel.LoginState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            isEmailVerificationSent = false
                            isEmailVerificationConfirming = false
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    private fun setupListeners() {
        emailVerifyButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "이메일을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "이메일 형식이 올바르지 않습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            isEmailVerificationSent = true
            authViewModel.sendEmailVerification(email)
        }
        
        confirmVerifyButton.setOnClickListener {
            val code = verifyCodeInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            
            if (code.isEmpty()) {
                Toast.makeText(requireContext(), "인증 코드를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            isEmailVerificationConfirming = true
            authViewModel.confirmEmailVerification(email, code)
        }
        
        nextButton.setOnClickListener {
            if (signUpData?.isEmailVerified == true) {
                (activity as? SignUpActivity)?.goToNextStep()
            } else {
                Toast.makeText(requireContext(), "이메일 인증을 완료해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showVerificationInput() {
        verifyContainer.visibility = View.VISIBLE
        timerText.visibility = View.VISIBLE
        // 인증 버튼 활성화 유지 (재전송 가능하도록)
        emailVerifyButton.isEnabled = true
        emailVerifyButton.text = "재전송"
        verifyCodeInput.requestFocus()
        startTimer()
        nextButton.isEnabled = false
    }
    
    private fun setupEmailWatcher() {
        emailInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentEmail = s.toString().trim()
                // 이메일이 변경되고 이전에 인증된 이메일과 다르면 인증 상태 초기화
                if (lastVerifiedEmail != null && currentEmail != lastVerifiedEmail) {
                    resetVerificationState()
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun resetVerificationState() {
        // 인증 상태 초기화
        signUpData?.isEmailVerified = false
        verifyContainer.visibility = View.GONE
        verificationTimer?.cancel()
        emailVerifyButton.isEnabled = true
        emailVerifyButton.text = "인증"
        verifyCodeInput.setText("")
        timerText.text = ""
        timerText.visibility = View.GONE
        nextButton.isEnabled = false
        lastVerifiedEmail = null
    }
    
    private fun startTimer() {
        verificationTimer?.cancel()
        
        var timeLeft = 180000L // 3분 = 180초 = 180000ms
        verificationTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("%02d:%02d", minutes, seconds)
            }
            
            override fun onFinish() {
                timerText.text = ""
                timerText.visibility = View.GONE
                confirmVerifyButton.isEnabled = false
                // 타이머 종료 시 처리
                emailInput.isEnabled = true
                emailVerifyButton.isEnabled = true
                emailVerifyButton.text = "인증"
                verifyCodeInput.setText("")
                Toast.makeText(requireContext(), "인증 시간이 만료되었습니다. 다시 인증해주세요.", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
    
    private fun updateEmailVerifyStatus(verified: Boolean) {
        if (verified) {
            verifyContainer.visibility = View.GONE
            verificationTimer?.cancel()
            emailVerifyButton.isEnabled = false
            emailVerifyButton.text = "완료"
            nextButton.isEnabled = true
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        verificationTimer?.cancel()
    }
}

