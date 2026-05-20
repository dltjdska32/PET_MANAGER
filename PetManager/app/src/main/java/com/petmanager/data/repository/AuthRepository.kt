package com.petmanager.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import com.kakao.sdk.user.UserApiClient
import com.petmanager.data.local.TokenStorage
import com.petmanager.data.local.dao.UserDao
import com.petmanager.data.local.entity.UserEntity
import com.petmanager.data.remote.api.AuthApi
import com.petmanager.data.remote.api.FindUserImgRespDto
import com.petmanager.data.remote.api.JwtDecoder
import com.petmanager.data.remote.api.SocialLoginOutcome
import com.petmanager.data.remote.api.EmailVerifyRequest
import com.petmanager.data.remote.api.EmailVerifyConfirmRequest
import com.petmanager.data.remote.api.LoginRequest
import com.petmanager.data.remote.api.SignUpRequest
import com.petmanager.data.remote.api.SocialLoginDto
import com.petmanager.data.remote.api.RefreshReqDto
import com.petmanager.data.remote.api.SaveUserRegionReqDto
import com.petmanager.data.remote.api.UpdateUserProfileReqDto
import com.petmanager.data.remote.api.UpsertUserNicknameReqDto
import com.petmanager.data.remote.api.UserInfoRespDto
import com.petmanager.data.remote.api.isApiSuccess
import com.petmanager.data.remote.socket.ChatSocketManager
import com.petmanager.domain.model.User
import com.petmanager.presentation.mapper.normalizeImageUrl
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: AuthApi,
    private val userDao: UserDao,
    private val chatSocketManager: ChatSocketManager,
) {
    private val gson = Gson()
    private val prefs: SharedPreferences get() = TokenStorage.prefs()

    companion object {
        private const val TAG = "AuthRepository"
        private const val KEY_LOGIN_PROVIDER = "login_provider"
        private const val PROVIDER_PASSWORD = "PASSWORD"
        private const val PREF_PRIMARY_REGION_ID = "user_primary_region_id"
    }

    /**
     * 자체 백엔드 회원가입
     */
    suspend fun signUp(request: SignUpRequest): Result<Unit> {
        return try {
            val response = authApi.signUp(request)
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.message ?: "회원가입 실패 (HTTP ${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 자체 백엔드 로그인
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val request = LoginRequest(username, password)
            val response = authApi.login(request)
            val body = response.body()
            val tokens = body?.value

            if (response.isSuccessful && body.isApiSuccess() && tokens != null) {
                chatSocketManager.clearSession()
                clearUserProfileCache()
                saveTokens(tokens.accessToken, tokens.refreshToken)
                prefs.edit().putString(KEY_LOGIN_PROVIDER, PROVIDER_PASSWORD).apply()
                ensureUserIdInPrefs()
                Result.success(Unit)
            } else {
                val httpCode = response.code()
                val rawMessage = body?.message

                val message = when {
                    httpCode >= 500 ->
                        "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."

                    // 인증 토큰 관련 서버 메시지는 한글 공통 문구로 치환
                    rawMessage?.contains("authentication tokens don't exist", ignoreCase = true) == true ||
                        rawMessage?.contains("JWT", ignoreCase = true) == true ->
                        "세션이 만료되었습니다. 다시 로그인해주세요."

                    else ->
                        rawMessage ?: "로그인 실패"
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            // 네트워크/변환 오류 등도 통일된 메시지로 처리
            Result.failure(Exception("서버와 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
        }
    }

    /** 자체 로그인(아이디·비밀번호)만 지역 없을 때 스플래시에서 메인 허용에 사용 */
    fun isSocialLoginProvider(): Boolean {
        val p = prefs.getString(KEY_LOGIN_PROVIDER, null) ?: return false
        return !p.equals(PROVIDER_PASSWORD, ignoreCase = true)
    }

    /**
     * 소셜 로그인 (카카오 토큰 -> 백엔드 전송 -> JWT, userRegions, isNewUser)
     */
    suspend fun loginWithBackend(provider: String, token: String): Result<SocialLoginOutcome> {
        return try {
            val request = SocialLoginDto(token)
            val response = authApi.socialLogin(provider, request)
            val body = response.body()
            val data = body?.value

            if (response.isSuccessful && data != null) {
                chatSocketManager.clearSession()
                clearUserProfileCache()
                saveTokens(data.token.accessToken, data.token.refreshToken)
                prefs.edit().putString(KEY_LOGIN_PROVIDER, provider.uppercase()).apply()

                val regions = data.userRegions.orEmpty()
                val userId = JwtDecoder.subject(data.token.accessToken)
                if (userId != null) {
                    saveUserInfo(
                        User(
                            id = userId,
                            nickname = null,
                            email = null,
                            profileImageUrl = null,
                            regionIds = regions
                        )
                    )
                    syncRoomUserRegions(userId, regions)
                }

                Result.success(
                    SocialLoginOutcome(
                        isNewUser = data.isNewUser,
                        userRegions = regions
                    )
                )
            } else {
                val httpCode = response.code()
                val rawMessage = body?.message

                val message = when {
                    httpCode >= 500 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                    else -> rawMessage ?: "소셜 로그인 실패"
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(Exception("서버와 통신 중 오류가 발생했습니다."))
        }
    }

    private suspend fun syncRoomUserRegions(userId: String, regionIds: List<Long>) {
        if (userId.isBlank()) return
        try {
            val stub = UserEntity(id = userId)
            if (regionIds.isEmpty()) {
                userDao.insertUser(stub)
                userDao.deleteUserRegions(userId)
            } else {
                userDao.updateUserInfo(stub, regionIds)
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "syncRoomUserRegions failed", e)
        }
    }

    /**
     * JWT는 유지하고 SharedPreferences의 유저 프로필/지역 캐시만 삭제 (소셜 로그인 직후 등)
     */
    fun clearUserProfileCache() {
        prefs.edit().apply {
            remove("user_id")
            remove("user_nickname")
            remove("user_email")
            remove("user_profile_image")
            remove("user_region_id")
            remove("user_region_ids")
            remove(PREF_PRIMARY_REGION_ID)
            apply()
        }
    }

    private fun saveTokens(accessToken: String, refreshToken: String) {
        TokenStorage.saveTokenPair(accessToken, refreshToken)
    }

    /**
     * 로그인 상태 확인
     */
    suspend fun checkLoginStatus(): Boolean = suspendCancellableCoroutine { continuation ->
        continuation.resume(!TokenStorage.accessToken().isNullOrBlank())
    }

    /**
     * SharedPreferences에 유저 정보 저장 (복수 지역 지원)
     */
    fun saveUserInfo(user: User) {
        prefs.edit().apply {
            putString("user_id", user.id)
            putString("user_nickname", user.nickname)
            putString("user_email", user.email)
            putString("user_profile_image", user.profileImageUrl)
            putLong("user_region_id", user.regionIds.firstOrNull() ?: -1L)
            if (user.regionIds.isEmpty()) {
                remove("user_region_ids")
            } else {
                putString("user_region_ids", user.regionIds.joinToString(","))
            }
            apply()
        }
    }

    /**
     * SharedPreferences에서 유저 정보 읽기 (복수 지역: user_region_ids CSV 우선)
     * user_id 가 없어도 JWT subject 로 id 를 보완한다.
     */
    fun getCachedUserInfo(): User? {
        val id = prefs.getString("user_id", null)?.takeIf { it.isNotBlank() }
            ?: resolveUserId().takeIf { it.isNotBlank() && it != "0" }
            ?: return null
        val regions = readRegionIdsFromPrefs()

        return User(
            id = id,
            nickname = prefs.getString("user_nickname", null),
            email = prefs.getString("user_email", null),
            profileImageUrl = prefs.getString("user_profile_image", null),
            regionIds = regions
        )
    }

    /**
     * Room → SharedPreferences 순으로 관심 지역 ID 조회.
     * 기본 로그인 직후 Room 미동기화여도 prefs 에 저장된 지역을 사용한다.
     */
    suspend fun getUserRegionIds(): List<Long> {
        val userId = resolveUserId().takeIf { it.isNotBlank() && it != "0" }
        if (userId != null) {
            try {
                val roomIds = userDao.getUserRegionIdsSync(userId)
                if (roomIds.isNotEmpty()) return roomIds
            } catch (e: Exception) {
                android.util.Log.w(TAG, "getUserRegionIds Room read failed", e)
            }
        }
        return readRegionIdsFromPrefs()
    }

    /**
     * 사용자가 설정한 대표 지역 ID (Room → prefs).
     * 설정되지 않았거나 삭제된 지역이면 null.
     */
    suspend fun getPrimaryRegionId(): Long? {
        val regionIds = getUserRegionIds()
        if (regionIds.isEmpty()) return null

        val userId = resolveUserId().takeIf { it.isNotBlank() && it != "0" }
        if (userId != null) {
            try {
                val roomPrimary = userDao.getPrimaryRegionIdSync(userId)
                if (roomPrimary != null && roomPrimary in regionIds) return roomPrimary
            } catch (e: Exception) {
                android.util.Log.w(TAG, "getPrimaryRegionId Room read failed", e)
            }
        }

        val prefPrimary = prefs.getLong(PREF_PRIMARY_REGION_ID, -1L)
        return if (prefPrimary != -1L && prefPrimary in regionIds) prefPrimary else null
    }

    /**
     * 홈·글쓰기 기본 지역: 대표 지역 우선, 없으면 ID가 가장 작은 지역.
     */
    suspend fun getDefaultRegionId(): Long? {
        val regionIds = getUserRegionIds()
        if (regionIds.isEmpty()) return null
        return getPrimaryRegionId() ?: regionIds.minOrNull()
    }

    suspend fun setPrimaryRegion(regionId: Long): Result<Unit> {
        val regionIds = getUserRegionIds()
        if (regionId !in regionIds) {
            return Result.failure(Exception("등록된 지역 중에서만 대표 지역을 설정할 수 있습니다."))
        }
        val userId = resolveUserId()
        if (userId.isBlank() || userId == "0") {
            return Result.failure(Exception("로그인 정보를 확인할 수 없습니다."))
        }
        return try {
            userDao.setPrimaryRegion(userId, regionId)
            prefs.edit().putLong(PREF_PRIMARY_REGION_ID, regionId).apply()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setPrimaryRegion failed", e)
            Result.failure(Exception("대표 지역 저장에 실패했습니다."))
        }
    }

    private fun ensureUserIdInPrefs() {
        if (!prefs.getString("user_id", null).isNullOrBlank()) return
        val fromJwt = TokenStorage.accessToken()?.let { JwtDecoder.subject(it) }
        if (!fromJwt.isNullOrBlank()) {
            prefs.edit().putString("user_id", fromJwt).apply()
        }
    }

    private fun readRegionIdsFromPrefs(): List<Long> {
        val csv = prefs.getString("user_region_ids", null)
        if (!csv.isNullOrBlank()) {
            return csv.split(',').mapNotNull { it.trim().toLongOrNull() }
        }
        val single = prefs.getLong("user_region_id", -1L)
        return if (single != -1L) listOf(single) else emptyList()
    }

    private fun writeRegionIdsToPrefs(ids: Collection<Long>) {
        val list = ids.distinct()
        prefs.edit().apply {
            if (list.isEmpty()) {
                remove("user_region_ids")
                putLong("user_region_id", -1L)
            } else {
                putString("user_region_ids", list.joinToString(","))
                putLong("user_region_id", list.first())
            }
            apply()
        }
    }

    private fun mergeRegionIdsAfterPut(add: List<Long>?, del: List<Long>?) {
        val cur = readRegionIdsFromPrefs().toMutableSet()
        add?.forEach { cur.add(it) }
        del?.forEach { cur.remove(it) }
        writeRegionIdsToPrefs(cur)

        val deleted = del.orEmpty()
        if (deleted.isNotEmpty()) {
            val primary = prefs.getLong(PREF_PRIMARY_REGION_ID, -1L)
            if (primary != -1L && primary in deleted) {
                prefs.edit().remove(PREF_PRIMARY_REGION_ID).apply()
            }
        }
    }

    /**
     * 백엔드 유저 내 정보 조회 (GET /api/auth/user)
     *
     * 서버 응답 계약: UserInfoRespDto(nickName, email, userMainImgUrl, regionIds).
     * userId 는 내려주지 않으므로 access token 의 subject 에서 확보한다.
     */
    suspend fun fetchMyInfoFromBackend(): Result<User> {
        return try {
            val response = authApi.getMyInfo()
            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "getMyInfo HTTP ${response.code()}")
                return Result.failure(Exception("내 정보 조회 실패 (HTTP ${response.code()})"))
            }
            val body = response.body()
            if (!body.isApiSuccess()) {
                android.util.Log.e(TAG, "getMyInfo API fail: code=${body?.code}, msg=${body?.message}")
                return Result.failure(Exception("내 정보 API 실패"))
            }
            val dto = body?.value
            android.util.Log.d(TAG, "getMyInfo dto nick=${dto?.nickName}, img=${dto?.userMainImgUrl}")
            val userId = resolveUserId()
            val regionIds = dto?.regionIds.orEmpty().ifEmpty { readRegionIdsFromPrefs() }
            val user = User(
                id = userId,
                email = dto?.email,
                nickname = dto?.nickName,
                profileImageUrl = dto?.userMainImgUrl?.trim()?.takeIf { it.isNotEmpty() },
                regionIds = regionIds,
            )
            android.util.Log.d(TAG, "getMyInfo -> User profileImageUrl=${user.profileImageUrl}")
            saveUserInfo(user)
            syncRoomUserProfile(user)
            Result.success(user)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "fetchMyInfoFromBackend Exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun resolveUserId(): String {
        val fromJwt = TokenStorage.accessToken()?.let { JwtDecoder.subject(it) }
        if (!fromJwt.isNullOrBlank()) return fromJwt
        val fromPrefs = prefs.getString("user_id", null)
        if (!fromPrefs.isNullOrBlank()) return fromPrefs
        return "0"
    }

    /**
     * Room 에 유저 프로필 + 관심 지역을 원자적으로 싱크.
     * 프로필 정보가 확보된 시점(GET /api/auth/user 성공 직후 등)에 호출.
     */
    private suspend fun syncRoomUserProfile(user: User) {
        if (user.id.isBlank() || user.id == "0") return
        try {
            val entity = UserEntity(
                id = user.id,
                nickname = user.nickname,
                email = user.email,
                profileImageUrl = user.profileImageUrl,
            )
            userDao.updateUserInfo(entity, user.regionIds)
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "syncRoomUserProfile failed", e)
        }
    }

    /**
     * 프로필 수정 (PUT /api/auth/user/profile).
     *
     * - nickName / userMainImgUrl 중 null 은 "변경 없음".
     * - 성공 시 서버가 돌려주는 UserInfoRespDto 기준으로 prefs + Room 을 풀 싱크한다.
     * - 응답이 비어 있으면 최소한의 로컬 머지(이전 값 + 요청 값)로 낙관적 갱신.
     */
    suspend fun updateProfile(
        nickName: String?,
        userMainImgUrl: String?,
    ): Result<User> {
        val trimmedNick = nickName?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedImg = userMainImgUrl?.trim()
        val hasNickChange = trimmedNick != null
        val hasImgChange = trimmedImg != null
        if (!hasNickChange && !hasImgChange) {
            return Result.failure(Exception("변경할 내용이 없습니다"))
        }

        return try {
            val response = authApi.updateProfile(
                UpdateUserProfileReqDto(
                    nickName = trimmedNick,
                    userMainImgUrl = trimmedImg,
                )
            )
            val body = response.body()
            val okMeta = response.isSuccessful && (body?.statusCode == "OK" || body?.code == "SUCCESS")
            if (!okMeta) {
                val httpCode = response.code()
                val raw = body?.message
                val msg = when {
                    httpCode >= 500 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                    else -> raw ?: "프로필 수정 실패 (HTTP $httpCode)"
                }
                return Result.failure(Exception(msg))
            }

            // 성공 경로: 서버 응답 우선, 응답 없으면 조회로 복구
            val dto = body?.value
            val user = if (dto != null) {
                val userId = resolveUserId()
                val regionIds = dto.regionIds.orEmpty().ifEmpty { readRegionIdsFromPrefs() }
                User(
                    id = userId,
                    email = dto.email,
                    nickname = dto.nickName,
                    profileImageUrl = dto.userMainImgUrl,
                    regionIds = regionIds,
                )
            } else {
                // 서버가 응답 본문을 안 주는 스펙이라면 재조회로 최신 상태 확보
                val refreshed = fetchMyInfoFromBackend().getOrNull()
                refreshed ?: User(
                    id = resolveUserId(),
                    email = prefs.getString("user_email", null),
                    nickname = trimmedNick ?: prefs.getString("user_nickname", null),
                    profileImageUrl = trimmedImg ?: prefs.getString("user_profile_image", null),
                    regionIds = readRegionIdsFromPrefs(),
                )
            }

            saveUserInfo(user)
            syncRoomUserProfile(user)
            Result.success(user)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "updateProfile failed", e)
            Result.failure(Exception("서버와 통신 중 오류가 발생했습니다."))
        }
    }

    /**
     * 닉네임만 수정 — PATCH /api/auth/user/nickname (UpsertUserNicknameReqDto)
     * 성공 후 GET /api/auth/user 로 로컬·Room 동기화.
     */
    suspend fun updateNickname(nickname: String): Result<Unit> {
        val nick = nickname.trim()
        if (nick.length < 3 || nick.length > 16) {
            return Result.failure(Exception("닉네임은 3자 이상 16자 이하로 입력해주세요"))
        }
        return try {
            val response = authApi.updateNickname(UpsertUserNicknameReqDto(nickname = nick))
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                fetchMyInfoFromBackend()
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.message ?: "닉네임 수정 실패 (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "updateNickname failed", e)
            Result.failure(Exception("서버와 통신 중 오류가 발생했습니다."))
        }
    }

    /**
     * 유저 프로필 이미지 URL 목록 — GET /api/auth/user/imgs
     */
    suspend fun findUserProfileImgs(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.findUserImgs()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val raw = extractImgUrlsFromResponseValue(body.value)
                val urls = raw.map { normalizeImageUrl(it) }
                if (urls.isNotEmpty()) {
                    return@withContext Result.success(urls)
                }
                if (body.isApiSuccess()) {
                    return@withContext Result.success(emptyList())
                }
                return@withContext Result.failure(
                    Exception(body.message ?: "프로필 이미지 조회 실패 (HTTP ${response.code()})")
                )
            }
            Result.failure(Exception(response.body()?.message ?: "프로필 이미지 조회 실패 (HTTP ${response.code()})"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "findUserProfileImgs failed", e)
            Result.failure(Exception("서버와 통신 중 오류가 발생했습니다."))
        }
    }

    /**
     * 프로필 이미지 upsert — PATCH multipart, 파트 이름 `userProfileImgs` (정확히 1개).
     * 성공 후 GET /api/auth/user 로 동기화.
     */
    suspend fun upsertProfileImage(imageUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val part = createUserProfileImgPart(imageUri)
            val response = authApi.upsertProfileImg(listOf(part))
            val body = response.body()
            if (response.isSuccessful && body.isApiSuccess()) {
                fetchMyInfoFromBackend()
                Result.success(Unit)
            } else {
                Result.failure(Exception(body?.message ?: "프로필 이미지 업로드 실패 (HTTP ${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(Exception(e.message ?: "이미지를 열 수 없습니다"))
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "upsertProfileImage failed", e)
            Result.failure(Exception("서버와 통신 중 오류가 발생했습니다."))
        }
    }

    private fun createUserProfileImgPart(uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("이미지를 열 수 없습니다.")
        input.use { stream ->
            val bytes = stream.readBytes()
            val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
            val name = queryDisplayName(uri) ?: "profile.jpg"
            return MultipartBody.Part.createFormData("userProfileImgs", name, body)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            return c.getString(idx)
        }
    }

    /**
     * 유저 지역 정보 설정 (PUT /api/auth/user/region)
     */
    suspend fun updateUserRegions(addRegionIds: List<Long>? = null, deleteRegionIds: List<Long>? = null): Result<Unit> {
        return try {
            val request = SaveUserRegionReqDto(addRegionIds, deleteRegionIds)
            val response = authApi.upsertUserRegions(request)
            val body = response.body()
            if (!response.isSuccessful) {
                return Result.failure(Exception("지역 업데이트 서버 오류"))
            }
            if (body != null && !body.isApiSuccess()) {
                return Result.failure(Exception(body.message ?: "지역 업데이트 실패"))
            }
            mergeRegionIdsAfterPut(addRegionIds, deleteRegionIds)
            ensureUserIdInPrefs()

            val regionIds = readRegionIdsFromPrefs()
            val userId = resolveUserId()
            if (userId.isNotBlank() && userId != "0" && regionIds.isNotEmpty()) {
                syncRoomUserRegions(userId, regionIds)
            }

            fetchMyInfoFromBackend()
                .onSuccess { user ->
                    if (user.regionIds.isEmpty() && regionIds.isNotEmpty()) {
                        saveUserInfo(user.copy(regionIds = regionIds))
                        syncRoomUserRegions(user.id, regionIds)
                    }
                }
                .onFailure {
                    if (userId.isNotBlank() && userId != "0" && regionIds.isNotEmpty()) {
                        val cached = getCachedUserInfo()
                        saveUserInfo(
                            User(
                                id = userId,
                                nickname = cached?.nickname,
                                email = cached?.email,
                                profileImageUrl = cached?.profileImageUrl,
                                regionIds = regionIds,
                            ),
                        )
                        syncRoomUserRegions(userId, regionIds)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "updateUserRegions failed", e)
            Result.failure(e)
        }
    }

    /**
     * 로그아웃
     *
     * 1) 서버에 /api/auth/logout 요청 (RefreshReqDto)
     * 2) 로컬에 저장된 액세스/리프레시 토큰 삭제
     * 3) 카카오 SDK 로그아웃 (소셜 로그인 사용 시)
     */
    suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = TokenStorage.refreshToken()
            endLocalSession(clearRoom = true)

            if (!refreshToken.isNullOrBlank()) {
                try {
                    authApi.logout(RefreshReqDto(refreshToken))
                } catch (_: Exception) {
                }
            }

            suspendCancellableCoroutine { continuation ->
                UserApiClient.instance.logout { _ ->
                    continuation.resume(Result.success(Unit))
                }
            }
        } catch (e: Exception) {
            endLocalSession(clearRoom = true)
            Result.success(Unit)
        }
    }

    /**
     * 로컬 세션 종료 — WS 세션·토큰·(선택) Room 유저 데이터.
     * RTR(reissue) 경로에서는 호출하지 않는다.
     */
    private suspend fun endLocalSession(clearRoom: Boolean) {
        chatSocketManager.clearSession()
        TokenStorage.clearAllUserPrefs()
        if (!clearRoom) return
        try {
            userDao.deleteAllUsers()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Room clear on session end", e)
        }
    }

    /**
     * 연결 끊기
     */
    suspend fun unlink(): Result<Unit> {
        endLocalSession(clearRoom = true)
        return suspendCancellableCoroutine { continuation ->
            UserApiClient.instance.unlink { error ->
                if (error != null) {
                    continuation.resume(Result.failure(error))
                } else {
                    continuation.resume(Result.success(Unit))
                }
            }
        }
    }

    /**
     * 이메일 인증 코드 전송
     */
    suspend fun sendEmailVerification(email: String): Result<Unit> {
        return try {
            val request = EmailVerifyRequest(email)
            val response = authApi.sendEmailVerification(request)
            if (response.isSuccessful && response.body()?.statusCode == "OK") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "이메일 인증 코드 전송 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 이메일 인증 코드 확인
     */
    suspend fun confirmEmailVerification(email: String, code: String): Result<Unit> {
        return try {
            val request = EmailVerifyConfirmRequest(email, code)
            val response = authApi.confirmEmailVerification(request)
            if (response.isSuccessful && response.body()?.statusCode == "OK") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "이메일 인증 코드 확인 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractImgUrlsFromResponseValue(value: Any?): List<String> {
        if (value == null) return emptyList()
        if (value is String) {
            val s = value.trim()
            return if (s.isNotEmpty()) listOf(s) else emptyList()
        }
        if (value is List<*>) {
            return value.mapNotNull { elem ->
                (elem as? String)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        if (value is JsonElement) {
            return extractImgUrlsFromJsonElement(value)
        }
        return extractImgUrlsFromJsonElement(gson.toJsonTree(value))
    }

    private fun extractImgUrlsFromJsonElement(el: JsonElement?): List<String> {
        if (el == null || el.isJsonNull) return emptyList()
        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            val s = el.asString.trim()
            return if (s.isNotEmpty()) listOf(s) else emptyList()
        }
        if (el.isJsonArray) {
            return el.asJsonArray.mapNotNull { elem ->
                if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                    elem.asString.trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        }
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            val dto = runCatching { gson.fromJson(obj, FindUserImgRespDto::class.java) }.getOrNull()
            val fromDto = dto.allUrlsMerged()
            if (fromDto.isNotEmpty()) return fromDto
            return collectUrlsFromArbitraryJsonObject(obj)
        }
        return emptyList()
    }

    private fun FindUserImgRespDto?.allUrlsMerged(): List<String> {
        if (this == null) return emptyList()
        val lists = listOfNotNull(
            imgUrls,
            urls,
            imgs,
            images,
            userProfileImgs,
            userProfileImgUrls,
            authImgUrls,
        )
        val fromLists = lists.flatten().map { it.trim() }.filter { it.isNotEmpty() }
        val singles = listOfNotNull(profileImgUrl, userMainImgUrl, url, imageUrl)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return (fromLists + singles).distinct()
    }

    /** DTO 필드에 없는 키(예: 단일 S3 URL만 있는 객체)도 수집 */
    private fun collectUrlsFromArbitraryJsonObject(o: JsonObject): List<String> {
        val out = mutableListOf<String>()
        for ((_, v) in o.entrySet()) {
            when {
                v.isJsonPrimitive && v.asJsonPrimitive.isString -> {
                    val s = v.asString.trim()
                    if (s.isNotEmpty()) out.add(s)
                }
                v.isJsonArray -> {
                    v.asJsonArray.forEach { e ->
                        if (e.isJsonPrimitive && e.asJsonPrimitive.isString) {
                            val s = e.asString.trim()
                            if (s.isNotEmpty()) out.add(s)
                        }
                    }
                }
            }
        }
        return out.distinct()
    }

    suspend fun checkUsernameDuplicate(username: String): Result<Boolean> {
        return try {
            val response = authApi.checkUsernameDuplicate(username)
            if (response.isSuccessful && response.body()?.statusCode == "OK") {
                Result.success(response.body()?.value ?: false)
            } else {
                Result.failure(Exception(response.body()?.message ?: "아이디 중복 확인 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
