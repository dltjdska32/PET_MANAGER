package com.petmanager.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.petmanager.data.repository.AuthRepository
import com.petmanager.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _usernameCheckState = MutableStateFlow<LoginState>(LoginState.Idle)
    val usernameCheckState: StateFlow<LoginState> = _usernameCheckState.asStateFlow()

    private val _userInfo = MutableStateFlow<User?>(null)
    val userInfo: StateFlow<User?> = _userInfo.asStateFlow()

    private val _profileEditState = MutableStateFlow<ProfileEditState>(ProfileEditState.Idle)
    val profileEditState: StateFlow<ProfileEditState> = _profileEditState.asStateFlow()

    private val _profileImgUrls = MutableStateFlow<List<String>>(emptyList())
    val profileImgUrls: StateFlow<List<String>> = _profileImgUrls.asStateFlow()

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object Success : LoginState()
        object NeedRegionSetting : LoginState()
        data class Error(val message: String) : LoginState()
    }

    sealed class ProfileEditState {
        object Idle : ProfileEditState()
        object Loading : ProfileEditState()
        object Success : ProfileEditState()
        data class Error(val message: String) : ProfileEditState()
    }

    fun updateProfile(nickName: String?, userMainImgUrl: String?) {
        val hasNick = !nickName.isNullOrBlank()
        val hasImg = userMainImgUrl != null // 빈 문자열도 "이미지 제거" 의도로 보낼 수 있어서 null 만 차단
        if (!hasNick && !hasImg) {
            _profileEditState.value = ProfileEditState.Error("변경할 내용이 없습니다")
            return
        }
        _profileEditState.value = ProfileEditState.Loading
        viewModelScope.launch {
            val result = authRepository.updateProfile(nickName, userMainImgUrl)
            result.onSuccess { user ->
                _userInfo.value = user
                _profileEditState.value = ProfileEditState.Success
            }.onFailure { error ->
                _profileEditState.value = ProfileEditState.Error(error.message ?: "프로필 수정 실패")
            }
        }
    }

    /** GET /api/auth/user/imgs — 프로필 이미지 편집 화면 등에서 미리보기용 */
    fun loadUserProfileImgsFromServer() {
        viewModelScope.launch {
            authRepository.findUserProfileImgs().onSuccess {
                _profileImgUrls.value = it
            }.onFailure {
                _profileImgUrls.value = emptyList()
            }
        }
    }

    /** `/api/auth/user` 갱신 후 `/api/auth/user/imgs` 로 미리보기 URL (순서 보장) */
    fun loadProfileDataForImageEditor() {
        viewModelScope.launch {
            authRepository.fetchMyInfoFromBackend().onSuccess { _userInfo.value = it }
            authRepository.findUserProfileImgs().onSuccess { _profileImgUrls.value = it }
                .onFailure { _profileImgUrls.value = emptyList() }
        }
    }

    /** PATCH multipart `userProfileImgs` — 1장 upsert 후 내 정보 재조회 */
    fun upsertProfileImage(imageUri: Uri) {
        _profileEditState.value = ProfileEditState.Loading
        viewModelScope.launch {
            authRepository.upsertProfileImage(imageUri).onSuccess {
                authRepository.getCachedUserInfo()?.let { _userInfo.value = it }
                _profileEditState.value = ProfileEditState.Success
            }.onFailure { e ->
                _profileEditState.value = ProfileEditState.Error(e.message ?: "프로필 이미지 업로드 실패")
            }
        }
    }

    /** PATCH /api/auth/user/nickname — 서버 검증 3~16자 */
    fun updateNickname(nickname: String) {
        val nick = nickname.trim()
        if (nick.length < 3 || nick.length > 16) {
            _profileEditState.value = ProfileEditState.Error("닉네임은 3자 이상 16자 이하로 입력해주세요")
            return
        }
        _profileEditState.value = ProfileEditState.Loading
        viewModelScope.launch {
            authRepository.updateNickname(nick).onSuccess {
                authRepository.getCachedUserInfo()?.let { _userInfo.value = it }
                _profileEditState.value = ProfileEditState.Success
            }.onFailure { e ->
                _profileEditState.value = ProfileEditState.Error(e.message ?: "닉네임 수정 실패")
            }
        }
    }

    fun resetProfileEditState() {
        _profileEditState.value = ProfileEditState.Idle
    }

    fun checkUsernameDuplicate(username: String) {
        viewModelScope.launch {
            _usernameCheckState.value = LoginState.Loading
            val result = authRepository.checkUsernameDuplicate(username)
            result.fold(
                onSuccess = { exists ->
                    if (exists) {
                        _usernameCheckState.value = LoginState.Error("이미 사용 중인 아이디입니다.")
                    } else {
                        _usernameCheckState.value = LoginState.Success
                    }
                },
                onFailure = { e ->
                    _usernameCheckState.value = LoginState.Error(e.message ?: "중복 확인 실패")
                }
            )
        }
    }

    fun resetUsernameCheckState() {
        _usernameCheckState.value = LoginState.Idle
    }

    fun updateUserRegions(addIds: List<Long>? = null, deleteIds: List<Long>? = null) {
        if (addIds.isNullOrEmpty() && deleteIds.isNullOrEmpty()) {
            _loginState.value = LoginState.Error("변경할 지역 정보를 선택해주세요.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val result = authRepository.updateUserRegions(addIds, deleteIds)
            result.onSuccess {
                _loginState.value = LoginState.Success
            }.onFailure { error ->
                _loginState.value = LoginState.Error(error.message ?: "지역 설정 업데이트 실패")
            }
        }
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val isLoggedIn = authRepository.checkLoginStatus()
                if (isLoggedIn) {
                    getUserInfo()
                } else {
                    _loginState.value = LoginState.Idle
                }
            } catch (_: Exception) {
                _loginState.value = LoginState.Idle
            }
        }
    }

    fun loginWithKakao(context: Context) {
        _loginState.value = LoginState.Loading

        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                _loginState.value = LoginState.Error(error.message ?: "로그인 실패")
            } else if (token != null) {
                loginToBackend("KAKAO", token.accessToken)
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                if (error != null) {
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        _loginState.value = LoginState.Idle
                        return@loginWithKakaoTalk
                    }

                    UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                } else if (token != null) {
                    loginToBackend("KAKAO", token.accessToken)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
        }
    }

    private fun loginToBackend(provider: String, token: String) {
        android.util.Log.d("AuthViewModel", "loginToBackend start. Provider: $provider")
        viewModelScope.launch {
            val result = authRepository.loginWithBackend(provider, token)
            result.onSuccess { outcome ->
                android.util.Log.d(
                    "AuthViewModel",
                    "loginWithBackend OK isNewUser=${outcome.isNewUser} regions=${outcome.userRegions.size}"
                )
                if (outcome.userRegions.isEmpty()) {
                    _loginState.value = LoginState.NeedRegionSetting
                } else {
                    launch {
                        authRepository.fetchMyInfoFromBackend().onSuccess { _userInfo.value = it }
                    }
                    _loginState.value = LoginState.Success
                }
            }.onFailure { error ->
                android.util.Log.e("AuthViewModel", "loginWithBackend Failed: ${error.message}")
                _loginState.value = LoginState.Error(error.message ?: "서버 로그인 실패")
            }
        }
    }

    private fun getUserInfo() {
        viewModelScope.launch {
            try {
                val cached = authRepository.getCachedUserInfo()
                if (cached != null) {
                    _userInfo.value = cached
                    _loginState.value = resolveRegionGate(cached.regionIds.isEmpty())
                    return@launch
                }

                val result = authRepository.fetchMyInfoFromBackend()
                result.onSuccess { user ->
                    _userInfo.value = user
                    _loginState.value = resolveRegionGate(user.regionIds.isEmpty())
                }.onFailure {
                    applyProfileFetchFailureNavigation()
                }
            } catch (_: Exception) {
                applyProfileFetchFailureNavigation()
            }
        }
    }

    private fun applyProfileFetchFailureNavigation() {
        val cached = authRepository.getCachedUserInfo()
        if (cached != null && cached.regionIds.isNotEmpty()) {
            _userInfo.value = cached
            _loginState.value = LoginState.Success
        } else {
            _loginState.value = resolveRegionGate(needsRegion = true)
        }
    }

    private fun resolveRegionGate(needsRegion: Boolean): LoginState {
        if (!needsRegion) return LoginState.Success
        return LoginState.NeedRegionSetting
    }

    fun refreshMyInfo() {
        viewModelScope.launch {
            try {
                val cached = authRepository.getCachedUserInfo()
                if (cached != null) {
                    _userInfo.value = cached
                }

                val result = authRepository.fetchMyInfoFromBackend()
                result.onSuccess { user ->
                    _userInfo.value = user
                    if (user.profileImageUrl.isNullOrBlank()) {
                        authRepository.findUserProfileImgs().onSuccess { urls ->
                            val u = urls.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@onSuccess
                            _userInfo.value = user.copy(profileImageUrl = u)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            performLogout()
        }
    }

    /** 로그아웃 완료까지 suspend — 화면 전환 전 호출 */
    suspend fun performLogout() {
        try {
            authRepository.logout()
            _userInfo.value = null
            _loginState.value = LoginState.Idle
        } catch (e: Exception) {
            _loginState.value = LoginState.Error(e.message ?: "로그아웃 실패")
        }
    }

    fun unlink() {
        viewModelScope.launch {
            try {
                authRepository.unlink()
                _userInfo.value = null
                _loginState.value = LoginState.Idle
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "연결 끊기 실패")
            }
        }
    }

    suspend fun performUnlink() {
        authRepository.unlink()
        _userInfo.value = null
        _loginState.value = LoginState.Idle
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("아이디와 비밀번호를 입력해주세요")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.login(username, password)
            result.onSuccess {
                authRepository.fetchMyInfoFromBackend()
                    .onSuccess { user ->
                        _userInfo.value = user
                        _loginState.value = if (user.regionIds.isEmpty()) {
                            LoginState.NeedRegionSetting
                        } else {
                            LoginState.Success
                        }
                    }
                    .onFailure {
                        val cached = authRepository.getCachedUserInfo()
                        if (cached != null && cached.regionIds.isNotEmpty()) {
                            _userInfo.value = cached
                            _loginState.value = LoginState.Success
                        } else {
                            _loginState.value = LoginState.NeedRegionSetting
                        }
                    }
            }.onFailure { error ->
                _loginState.value = LoginState.Error(error.message ?: "로그인 실패")
            }
        }
    }

    fun signUp(nickName: String, username: String, password: String, email: String, regionIds: List<Long>? = null) {
        if (nickName.isBlank() || username.isBlank() || password.isBlank() || email.isBlank()) {
            _loginState.value = LoginState.Error("모든 항목을 입력해주세요")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val request = com.petmanager.data.remote.api.SignUpRequest(
                nickName = nickName,
                username = username,
                password = password,
                email = email,
                regionIds = regionIds
            )
            val result = authRepository.signUp(request)
            result.onSuccess {
                _loginState.value = LoginState.Success
            }.onFailure { error ->
                _loginState.value = LoginState.Error(error.message ?: "회원가입 실패")
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    fun sendEmailVerification(email: String) {
        if (email.isBlank()) {
            _loginState.value = LoginState.Error("이메일을 입력해주세요")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _loginState.value = LoginState.Error("올바른 이메일 형식이 아닙니다")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.sendEmailVerification(email)
            result.onSuccess {
                _loginState.value = LoginState.Idle
            }.onFailure { error ->
                _loginState.value = LoginState.Error(error.message ?: "이메일 인증 코드 전송 실패")
            }
        }
    }

    fun confirmEmailVerification(email: String, code: String) {
        if (email.isBlank() || code.isBlank()) {
            _loginState.value = LoginState.Error("이메일과 인증 코드를 입력해주세요")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.confirmEmailVerification(email, code)
            result.onSuccess {
                _loginState.value = LoginState.Success
            }.onFailure { error ->
                _loginState.value = LoginState.Error(error.message ?: "이메일 인증 코드 확인 실패")
            }
        }
    }
}
