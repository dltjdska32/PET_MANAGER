package com.petmanager.presentation.ui.myinfo

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.petmanager.R
import com.petmanager.databinding.ActivityProfileImageEditBinding
import com.petmanager.domain.model.User
import com.petmanager.presentation.mapper.normalizeImageUrl
import com.petmanager.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 프로필 이미지 upsert — PATCH multipart, 파트 이름 `userProfileImgs` (1장).
 */
@AndroidEntryPoint
class ProfileImageEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileImageEditBinding
    private val authViewModel: AuthViewModel by viewModels()

    private val saveButton: MaterialButton get() = binding.saveButton
    private val pickButton: MaterialButton get() = binding.btnPickImage
    private val progressBar: ProgressBar get() = binding.progressBar
    private val toolbar: MaterialToolbar get() = binding.toolbar

    private var pendingPickUri: Uri? = null

    private val pickVisualMedia = registerForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingPickUri = uri
            renderPreviewUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileImageEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar.setNavigationOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finish()
            }
        )

        pickButton.setOnClickListener {
            pickVisualMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
        saveButton.setOnClickListener { submit() }

        observePreviewSources()
        observeEditState()
        authViewModel.loadProfileDataForImageEditor()
    }

    private fun observePreviewSources() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(authViewModel.userInfo, authViewModel.profileImgUrls) { user, urls ->
                    Pair(user, urls)
                }.collect { (user, urls) -> syncPreview(user, urls) }
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
                            Toast.makeText(this@ProfileImageEditActivity, "프로필 이미지가 수정되었습니다", Toast.LENGTH_SHORT).show()
                            authViewModel.resetProfileEditState()
                            setResult(RESULT_OK)
                            finish()
                        }
                        is AuthViewModel.ProfileEditState.Error -> {
                            setBusy(false)
                            Toast.makeText(this@ProfileImageEditActivity, state.message, Toast.LENGTH_SHORT).show()
                            authViewModel.resetProfileEditState()
                        }
                    }
                }
            }
        }
    }

    private fun syncPreview(user: User?, urls: List<String>) {
        if (pendingPickUri != null) {
            renderPreviewUri(pendingPickUri!!)
            return
        }
        val url = urls.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: user?.profileImageUrl?.takeIf { it.isNotBlank() }?.let { normalizeImageUrl(it) }
        renderPreviewUrl(url)
    }

    private fun renderPreviewUrl(url: String?) {
        val iv = binding.ivProfilePreview
        if (url.isNullOrBlank()) {
            iv.setImageResource(R.drawable.ic_person)
            return
        }
        iv.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_person)
            error(R.drawable.ic_person)
            transformations(CircleCropTransformation())
        }
    }

    private fun renderPreviewUri(uri: Uri) {
        binding.ivProfilePreview.load(uri) {
            crossfade(true)
            placeholder(R.drawable.ic_person)
            error(R.drawable.ic_person)
            transformations(CircleCropTransformation())
        }
    }

    private fun submit() {
        val uri = pendingPickUri
        if (uri == null) {
            Toast.makeText(this, "앨범에서 사진을 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        authViewModel.upsertProfileImage(uri)
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        saveButton.isEnabled = !busy
        pickButton.isEnabled = !busy
    }
}
