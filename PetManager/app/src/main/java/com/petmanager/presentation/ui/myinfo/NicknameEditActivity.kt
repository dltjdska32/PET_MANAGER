package com.petmanager.presentation.ui.myinfo

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.petmanager.databinding.ActivityNicknameEditBinding
import com.petmanager.domain.model.User
import com.petmanager.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 닉네임만 수정 — PATCH /api/auth/user/nickname, 본문 `{ "nickname": "..." }` (3~16자).
 */
@AndroidEntryPoint
class NicknameEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNicknameEditBinding
    private val authViewModel: AuthViewModel by viewModels()

    private val etNickname: TextInputEditText get() = binding.etNickname
    private val tilNickname: TextInputLayout get() = binding.tilNickname
    private val saveButton: MaterialButton get() = binding.saveButton
    private val progressBar: ProgressBar get() = binding.progressBar
    private val toolbar: MaterialToolbar get() = binding.toolbar

    private var prefilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNicknameEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar.setNavigationOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finish()
            }
        )

        saveButton.setOnClickListener { submit() }

        observeUserInfo()
        observeEditState()
        authViewModel.refreshMyInfo()
    }

    private fun observeUserInfo() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.userInfo.collect { user -> applyPrefill(user) }
            }
        }
    }

    private fun observeEditState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.profileEditState.collect { state ->
                    when (state) {
                        AuthViewModel.ProfileEditState.Idle -> setBusy(false)
                        AuthViewModel.ProfileEditState.Loading -> setBusy(true)
                        AuthViewModel.ProfileEditState.Success -> {
                            setBusy(false)
                            Toast.makeText(this@NicknameEditActivity, "닉네임이 수정되었습니다", Toast.LENGTH_SHORT).show()
                            authViewModel.resetProfileEditState()
                            setResult(RESULT_OK)
                            finish()
                        }
                        is AuthViewModel.ProfileEditState.Error -> {
                            setBusy(false)
                            Toast.makeText(this@NicknameEditActivity, state.message, Toast.LENGTH_SHORT).show()
                            authViewModel.resetProfileEditState()
                        }
                    }
                }
            }
        }
    }

    private fun applyPrefill(user: User?) {
        if (user == null) return
        if (!prefilled) {
            etNickname.setText(user.nickname.orEmpty())
            prefilled = true
        }
    }

    private fun submit() {
        val newNick = etNickname.text?.toString()?.trim().orEmpty()
        val currentNick = authViewModel.userInfo.value?.nickname.orEmpty()

        tilNickname.error = null
        if (newNick.length < 3) {
            tilNickname.error = "닉네임은 3자 이상 입력해주세요"
            return
        }
        if (newNick.length > 16) {
            tilNickname.error = "닉네임은 16자 이하로 입력해주세요"
            return
        }
        if (newNick == currentNick) {
            Toast.makeText(this, "변경된 내용이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        authViewModel.updateNickname(newNick)
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        saveButton.isEnabled = !busy
        etNickname.isEnabled = !busy
    }
}
