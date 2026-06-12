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

    private var authToken: String? = null

    // Flow that emits when a 401 is received — the UI observes this to redirect to login
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    fun setToken(token: String?) {
        authToken = token
    }

    fun getToken(): String? = authToken

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
        authToken?.let {
            request.addHeader("Authorization", "Bearer $it")
        }
        val response = chain.proceed(request.build())

        // On 401: clear token and notify so the UI can redirect to login
        // Skip login/register to avoid infinite redirect on auth failures
        val path = chain.request().url.encodedPath
        if (response.code == 401 && !path.contains("api/login") && !path.contains("api/register")) {
            authToken = null
            AuthManager.clearToken()
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
