package com.plain.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // For Android emulator, 10.0.2.2 maps to host machine localhost
    // For real device, use computer's local IP
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // Token storage
    private var userToken: String? = null
    private var businessToken: String? = null
    private var currentAuthType: AuthType = AuthType.NONE

    enum class AuthType {
        NONE, USER, BUSINESS
    }

    // Flow that emits when a 401 is received — the UI observes this to redirect to login
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    fun setUserToken(token: String?) {
        userToken = token
        if (token != null) currentAuthType = AuthType.USER
    }

    fun setBusinessToken(token: String?) {
        businessToken = token
        if (token != null) currentAuthType = AuthType.BUSINESS
    }

    fun getUserToken(): String? = userToken
    fun getBusinessToken(): String? = businessToken
    fun getCurrentAuthType(): AuthType = currentAuthType

    fun clearAllTokens() {
        userToken = null
        businessToken = null
        currentAuthType = AuthType.NONE
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
        val path = chain.request().url.encodedPath

        // Add appropriate token based on endpoint
        val tokenToUse = when {
            path.contains("api/business") && businessToken != null -> businessToken
            userToken != null -> userToken
            else -> null
        }

        tokenToUse?.let {
            request.addHeader("Authorization", "Bearer $it")
        }

        val response = chain.proceed(request.build())

        // On 401: clear token and notify so the UI can redirect to login
        // Skip login/register to avoid infinite redirect on auth failures
        if (response.code == 401 &&
            !path.contains("api/login") &&
            !path.contains("api/register") &&
            !path.contains("api/business/login") &&
            !path.contains("api/business/register")
        ) {
            if (path.contains("api/business")) {
                businessToken = null
            } else {
                userToken = null
            }
            AuthManager.clearTokenForPath(path)
            _sessionExpired.tryEmit(Unit)
        }

        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: ApiService = retrofit.create(ApiService::class.java)
}