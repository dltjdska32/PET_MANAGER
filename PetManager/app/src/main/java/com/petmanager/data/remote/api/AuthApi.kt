package com.petmanager.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// 로그인 요청 DTO
data class LoginRequest(
    val username: String, // email -> username 변경
    val password: String
)

// 회원가입 요청 DTO
data class SignUpRequest(
    val nickName: String,
    val username: String,
    val password: String,
    val email: String,
    val regionIds: List<Long>? = null
)

// 소셜 로그인 요청 DTO
data class SocialLoginDto(
    val token: String
)

// 이메일 인증 요청 DTO
data class EmailVerifyRequest(
    val email: String
)

// 이메일 인증 확인 요청 DTO
data class EmailVerifyConfirmRequest(
    val email: String,
    val otp: String
)

// 인증 응답 DTO (TokenRespDto)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)

// 리프레시 토큰 재발급 요청 DTO (서버 RefreshReqDto 와 매핑)
data class RefreshReqDto(
    val refreshToken: String
)

// 유저 내 정보 조회 응답 DTO — 백엔드 UserInfoRespDto(record) 와 1:1 매칭
// record 시그니처: (String nickName, String email, String userMainImgUrl, List<Long> regionIds)
// userId 는 서버가 내려주지 않으므로 클라에서 JWT subject 로 해결한다.
data class UserInfoRespDto(
    @SerializedName(value = "nickName", alternate = ["nickname"])
    val nickName: String?,
    val email: String?,
    @SerializedName(
        value = "userMainImgUrl",
        alternate = ["user_main_img_url", "profileImgUrl", "profileImageUrl", "mainImgUrl"],
    )
    val userMainImgUrl: String?,
    val regionIds: List<Long>? = null,
)

// 소셜 로그인 응답: 백엔드 SocialLoginRespDto(record) 와 동일
data class SocialLoginRespDto(
    @SerializedName("tokenRespDto")
    val token: AuthResponse,
    @SerializedName(value = "userRegions", alternate = ["user_regions"])
    val userRegions: List<Long>? = null,
    @SerializedName("isNewUser")
    val isNewUser: Boolean
)

/** 소셜 로그인 성공 후 화면 분기용 */
data class SocialLoginOutcome(
    val isNewUser: Boolean,
    val userRegions: List<Long>
)

// 유저 지역 수정 요청 DTO
data class SaveUserRegionReqDto(
    val addRegionIds: List<Long>? = null,
    val deleteRegionIds: List<Long>? = null
)

/**
 * 프로필 수정 요청 DTO.
 * 서버 계약 가정: 값이 null 이면 "변경 없음", non-null 이면 해당 필드 교체.
 * (서버 측 @JsonProperty("nickName"), @JsonProperty("userMainImgUrl") 가정)
 */
data class UpdateUserProfileReqDto(
    val nickName: String? = null,
    val userMainImgUrl: String? = null,
)

/** 백엔드 UpsertUserNicknameReqDto — 필드명 `nickname` */
data class UpsertUserNicknameReqDto(
    val nickname: String,
)

/**
 * GET /api/auth/user/imgs 의 `value` 가 객체일 때 Gson 매핑용 (필드명 여러 후보).
 * `value` 가 배열·문자열이면 [AuthRepository] 에서 유연 파싱.
 */
data class FindUserImgRespDto(
    val imgUrls: List<String>? = null,
    val urls: List<String>? = null,
    val imgs: List<String>? = null,
    val images: List<String>? = null,
    val userProfileImgs: List<String>? = null,
    val userProfileImgUrls: List<String>? = null,
    val authImgUrls: List<String>? = null,
    val profileImgUrl: String? = null,
    val userMainImgUrl: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
)

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>
    
    @POST("api/auth/join")
    suspend fun signUp(@Body request: SignUpRequest): Response<ApiResponse<Void>>
    
    @POST("api/auth/login/{provider}/callback")
    suspend fun socialLogin(
        @Path("provider") provider: String,
        @Body request: SocialLoginDto
    ): Response<ApiResponse<SocialLoginRespDto>>
    
    // 앱 토큰 재발급 API (/api/auth/reissue)
    @POST("api/auth/reissue")
    suspend fun refreshToken(@Body request: RefreshReqDto): Response<ApiResponse<AuthResponse>>
    
    // 로그아웃 API (/api/auth/logout)
    @POST("api/auth/logout")
    suspend fun logout(@Body request: RefreshReqDto): Response<ApiResponse<String>>
    
    @POST("api/auth/email/otp/send")
    suspend fun sendEmailVerification(@Body request: EmailVerifyRequest): Response<ApiResponse<Void>>
    
    @POST("api/auth/email/otp")
    suspend fun confirmEmailVerification(@Body request: EmailVerifyConfirmRequest): Response<ApiResponse<Void>>
    
    @GET("api/auth/exists/{username}")
    suspend fun checkUsernameDuplicate(@Path("username") username: String): Response<ApiResponse<Boolean>>

    @GET("api/auth/user")
    suspend fun getMyInfo(): Response<ApiResponse<UserInfoRespDto>>

    // 유저 지역 설정 (PUT /api/auth/user/region) — value:null 은 Gson이 ApiResponse<Void> 역직렬화 시 실패할 수 있어 String? 사용
    @PUT("api/auth/user/region")
    suspend fun upsertUserRegions(@Body dto: SaveUserRegionReqDto): Response<ApiResponse<String?>>

    /**
     * 프로필 수정 (PUT /api/auth/user/profile).
     * 응답은 수정 후 최신 UserInfoRespDto 를 그대로 돌려준다고 가정.
     * 서버 측에서 String? 로 내려주는 구조라면 [UserInfoRespDto] -> [String?] 로 바꾸면 됨.
     */
    @PUT("api/auth/user/profile")
    suspend fun updateProfile(@Body dto: UpdateUserProfileReqDto): Response<ApiResponse<UserInfoRespDto>>

    /** 닉네임만 수정 — PATCH /api/auth/user/nickname */
    @PATCH("api/auth/user/nickname")
    suspend fun updateNickname(@Body dto: UpsertUserNicknameReqDto): Response<ApiResponse<String?>>

    /** 유저 프로필 이미지 목록 조회 — `value` 는 Gson 이 Map/List/String 등으로 올 수 있어 [Any] 로 받음 */
    @GET("api/auth/user/imgs")
    suspend fun findUserImgs(): Response<ApiResponse<Any>>

    /**
     * 프로필 이미지 upsert — PATCH multipart, 파트 이름 [userProfileImgs] (정확히 1개).
     */
    @Multipart
    @PATCH("api/auth/user/imgs")
    suspend fun upsertProfileImg(
        @Part userProfileImgs: List<MultipartBody.Part>,
    ): Response<ApiResponse<String?>>
}
