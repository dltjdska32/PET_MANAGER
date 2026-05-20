package com.petmanager.presentation.ui.auth

import android.os.Bundle
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

class SignUpStep2Fragment : Fragment() {
    
    private val authViewModel: AuthViewModel by activityViewModels()
    private var signUpData: SignUpData? = null
    
    private lateinit var nicknameLayout: TextInputLayout
    private lateinit var nicknameInput: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var usernameInput: TextInputEditText
    private lateinit var checkUsernameButton: MaterialButton
    private lateinit var usernameCheckStatus: TextView
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var passwordConfirmLayout: TextInputLayout
    private lateinit var passwordConfirmInput: TextInputEditText
    private lateinit var nextButton: MaterialButton
    
    private var isUsernameChecked = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_signup_step2, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        signUpData = (activity as? SignUpActivity)?.signUpData
        
        nicknameLayout = view.findViewById(R.id.nicknameLayout)
        nicknameInput = view.findViewById(R.id.nicknameInput)
        usernameLayout = view.findViewById(R.id.usernameLayout)
        usernameInput = view.findViewById(R.id.usernameInput)
        checkUsernameButton = view.findViewById(R.id.checkUsernameButton)
        usernameCheckStatus = view.findViewById(R.id.usernameCheckStatus)
        passwordLayout = view.findViewById(R.id.passwordLayout)
        passwordInput = view.findViewById(R.id.passwordInput)
        passwordConfirmLayout = view.findViewById(R.id.passwordConfirmLayout)
        passwordConfirmInput = view.findViewById(R.id.passwordConfirmInput)
        nextButton = view.findViewById(R.id.nextButton)
        
        setupListeners()
        setupTextWatchers()
        setupObservers()
        
        // 저장된 데이터 복원
        signUpData?.let {
            nicknameInput.setText(it.nickname)
            usernameInput.setText(it.username)
            passwordInput.setText(it.password)
            if (it.isUsernameChecked) {
                isUsernameChecked = true
                usernameCheckStatus.text = "✓ 사용 가능한 아이디입니다"
                usernameCheckStatus.setTextColor(requireContext().getColor(R.color.primary))
                usernameCheckStatus.visibility = View.VISIBLE
                checkUsernameButton.isEnabled = false
                checkUsernameButton.text = "확인완료"
            }
        }
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.usernameCheckState.collect { state ->
                    when (state) {
                        is AuthViewModel.LoginState.Loading -> {
                            checkUsernameButton.isEnabled = false
                        }
                        is AuthViewModel.LoginState.Success -> {
                            isUsernameChecked = true
                            signUpData?.isUsernameChecked = true
                            usernameCheckStatus.text = "✓ 사용 가능한 아이디입니다"
                            usernameCheckStatus.setTextColor(requireContext().getColor(R.color.primary))
                            usernameCheckStatus.visibility = View.VISIBLE
                            checkUsernameButton.isEnabled = false
                            checkUsernameButton.text = "확인완료"
                            authViewModel.resetUsernameCheckState()
                        }
                        is AuthViewModel.LoginState.Error -> {
                            isUsernameChecked = false
                            signUpData?.isUsernameChecked = false
                            checkUsernameButton.isEnabled = true
                            usernameLayout.error = state.message
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            authViewModel.resetUsernameCheckState()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        checkUsernameButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            if (validateUsername(username)) {
                authViewModel.checkUsernameDuplicate(username)
            }
        }
        
        nextButton.setOnClickListener {
            val nickname = nicknameInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val passwordConfirm = passwordConfirmInput.text.toString().trim()
            
            var isValid = true
            
            if (!validateNickname(nickname)) {
                isValid = false
            }
            
            if (!validateUsername(username)) {
                isValid = false
            }
            
            if (!isUsernameChecked) {
                Toast.makeText(requireContext(), "아이디 중복 확인을 해주세요", Toast.LENGTH_SHORT).show()
                isValid = false
            }
            
            if (!validatePassword(password)) {
                isValid = false
            }
            
            if (!validatePasswordConfirm(password, passwordConfirm)) {
                isValid = false
            }
            
            if (isValid) {
                signUpData?.nickname = nickname
                signUpData?.username = username
                signUpData?.password = password
                (activity as? SignUpActivity)?.goToNextStep()
            }
        }
    }
    
    private fun setupTextWatchers() {
        usernameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
                    validateUsername(s.toString())
                } else {
                    usernameLayout.error = null
                }
                
                if (isUsernameChecked) {
                    isUsernameChecked = false
                    signUpData?.isUsernameChecked = false
                    usernameCheckStatus.visibility = View.GONE
                    checkUsernameButton.isEnabled = true
                    checkUsernameButton.text = "중복확인"
                }
            }
        })
    }
    
    private fun validateNickname(nickname: String): Boolean {
        return when {
            nickname.isEmpty() -> {
                nicknameLayout.error = "닉네임은 필수입니다"
                false
            }
            nickname.length < 5 -> {
                nicknameLayout.error = "닉네임은 5자 이상이어야 합니다"
                false
            }
            nickname.length > 16 -> {
                nicknameLayout.error = "닉네임은 16자 이하여야 합니다"
                false
            }
            else -> {
                nicknameLayout.error = null
                true
            }
        }
    }
    
    private fun validateUsername(username: String): Boolean {
        return when {
            username.isEmpty() -> {
                usernameLayout.error = "아이디는 필수입니다"
                false
            }
            username.length < 5 -> {
                usernameLayout.error = "아이디는 5자 이상이어야 합니다"
                false
            }
            username.length > 30 -> {
                usernameLayout.error = "아이디는 30자 이하여야 합니다"
                false
            }
            else -> {
                usernameLayout.error = null
                true
            }
        }
    }
    
    private fun validatePassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[~!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,30}$"
        val regex = Regex(passwordPattern)
        
        return when {
            password.isEmpty() -> {
                passwordLayout.error = "패스워드는 필수입니다"
                false
            }
            password.length < 8 -> {
                passwordLayout.error = "비밀번호는 8자 이상이어야 합니다"
                false
            }
            password.length > 30 -> {
                passwordLayout.error = "비밀번호는 30자 이하여야 합니다"
                false
            }
            !regex.matches(password) -> {
                passwordLayout.error = "영문, 숫자, 특수문자를 포함해야 합니다"
                false
            }
            else -> {
                passwordLayout.error = null
                true
            }
        }
    }
    
    private fun validatePasswordConfirm(password: String, passwordConfirm: String): Boolean {
        return when {
            passwordConfirm.isEmpty() -> {
                passwordConfirmLayout.error = "비밀번호 확인을 입력해주세요"
                false
            }
            password != passwordConfirm -> {
                passwordConfirmLayout.error = "비밀번호가 일치하지 않습니다"
                false
            }
            else -> {
                passwordConfirmLayout.error = null
                true
            }
        }
    }
}

