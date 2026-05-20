package com.petmanager.data.remote.api

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.reflect.TypeToken
import com.petmanager.BuildConfig
import okhttp3.Protocol
import com.petmanager.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.locks.ReentrantLock
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

/**
 * RTR(Reissue): 액세스 만료(401) 시 refresh로 한 번만 재발급 후 원 요청 재시도.
 * - 재발급 실패(리프레시 만료·폐기·오류)일 때만 세션 만료 → 재로그인
 * - 동시 다발 401은 단일 재발급 후 나머지는 새 AT로 재시도 (lock)
 */
@Singleton
class AuthInterceptor @Inject constructor(
    @ApplicationContext context: Context,
) : Interceptor {

    init {
        TokenStorage.init(context)
    }

    private val gson = Gson()

    companion object {
        private val BASE_URL = BuildConfig.SERVER_BASE_URL

        private val refreshLock = ReentrantLock()

        /** 이 요청들은 401이어도 AT 갱신을 시도하지 않음 (로그인 실패·reissue 실패 등) */
        private fun isAuthExemptPath(encodedPath: String): Boolean {
            return when {
                encodedPath.contains("/api/auth/reissue", ignoreCase = true) -> true
                encodedPath.contains("/api/auth/login", ignoreCase = true) -> true
                encodedPath.contains("/api/auth/join", ignoreCase = true) -> true
                encodedPath.contains("/api/auth/email/", ignoreCase = true) -> true
                encodedPath.contains("/api/auth/exists/", ignoreCase = true) -> true
                else -> false
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val accessUsed = TokenStorage.accessToken()
        val hadBearer = !accessUsed.isNullOrBlank()

        val requestWithToken = authorizeRequest(originalRequest, accessUsed)

        var response = chain.proceed(requestWithToken)

        if (response.code != 401) {
            return response
        }

        if (!hadBearer || isAuthExemptPath(originalRequest.url.encodedPath)) {
            return response
        }

        val bodyString = try {
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }

        // Bearer로 호출한 API에서 401 → AT 만료로 보고 reissue 1회 (RTR). 리프레시 무효 시에만 로그인 유도.
        refreshLock.withLock {
            val latestAccess = TokenStorage.accessToken()
            if (!latestAccess.isNullOrBlank() && latestAccess != accessUsed) {
                return chain.proceed(
                    authorizeRequest(originalRequest, latestAccess)
                )
            }

            when (val result = refreshTokensFromNetwork()) {
                is RefreshResult.Success -> {
                    TokenStorage.saveTokenPair(result.tokens.accessToken, result.tokens.refreshToken)
                    AuthEventBus.resetSessionExpiredFlag()
                    return chain.proceed(
                        authorizeRequest(originalRequest, result.tokens.accessToken)
                    )
                }
                is RefreshResult.Invalid -> {
                    handleRefreshFailure()
                }
                is RefreshResult.TransientFailure -> {
                    // 네트워크 타임아웃/일시 장애는 "세션 만료"로 간주하지 않음 (토큰 삭제 금지).
                    // 원 요청의 401을 그대로 반환하고, UI는 일반 네트워크 오류 플로우로 처리하도록 둔다.
                }
                RefreshResult.NotAttempted -> {
                    handleRefreshFailure()
                }
            }
        }

        return raw401Response(originalRequest, bodyString)
    }

    /**
     * multipart/form-data 는 OkHttp 가 boundary 포함 Content-Type 을 설정하므로
     * 여기서 application/json 을 덮어쓰지 않는다.
     */
    private fun authorizeRequest(original: Request, bearerToken: String?): Request {
        val b = original.newBuilder()
        if (!bearerToken.isNullOrBlank()) {
            b.header("Authorization", "Bearer $bearerToken")
        }
        val body = original.body
        val isMultipart = body?.contentType()?.type.equals("multipart", ignoreCase = true)
        if (!isMultipart && original.header("Content-Type") == null && body != null) {
            b.header("Content-Type", "application/json")
        }
        return b.build()
    }

    private fun raw401Response(request: Request, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private sealed class RefreshResult {
        data class Success(val tokens: AuthResponse) : RefreshResult()
        /** RT 만료/폐기 등 “재로그인 필요” 케이스 */
        data object Invalid : RefreshResult()
        /** 네트워크 타임아웃/일시 장애 등 “재시도 가능” 케이스 */
        data object TransientFailure : RefreshResult()
        /** RT가 없어서 시도 자체를 못한 케이스 */
        data object NotAttempted : RefreshResult()
    }

    private fun refreshTokensFromNetwork(): RefreshResult {
        val refreshToken = TokenStorage.refreshToken() ?: return RefreshResult.NotAttempted
        return try {
            // Interceptor 내부 refresh는 별도 client로 수행해야 재귀(interceptor loop)를 피할 수 있다.
            // 단, 기본 OkHttpClient는 타임아웃이 짧아(대개 10초) 실제 환경에서 자주 timeout이 난다.
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .build()

            val bodyMap = mapOf("refreshToken" to refreshToken)
            val jsonBody = gson.toJson(bodyMap)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val url = BASE_URL
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("api/auth/reissue")
                ?.build()
                ?: return RefreshResult.TransientFailure

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // 리프레시 토큰이 만료/무효면 보통 401/400 계열로 내려오므로 “Invalid”로 처리
                    return if (resp.code == 401 || resp.code == 400) RefreshResult.Invalid else RefreshResult.TransientFailure
                }
                val respString = resp.body?.string() ?: return RefreshResult.TransientFailure
                val type = object : TypeToken<ApiResponse<AuthResponse>>() {}.type
                val apiResp: ApiResponse<AuthResponse> = gson.fromJson(respString, type)
                if (apiResp.statusCode == "OK" && apiResp.value != null) {
                    RefreshResult.Success(apiResp.value)
                } else {
                    // 서버가 “만료된 JWT” 등으로 리이슈 거절한 경우는 재로그인 유도
                    if (apiResp.statusCode == "UNAUTHORIZED") RefreshResult.Invalid else RefreshResult.TransientFailure
                }
            }
        } catch (e: IOException) {
            android.util.Log.w("AuthInterceptor", "reissue 네트워크 실패", e)
            RefreshResult.TransientFailure
        } catch (e: Exception) {
            android.util.Log.w("AuthInterceptor", "reissue 실패", e)
            RefreshResult.TransientFailure
        }
    }

    private fun handleRefreshFailure() {
        TokenStorage.clearAllUserPrefs()
        AuthEventBus.emitSessionExpiredOnce()
    }
}
