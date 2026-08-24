package com.plain.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Backend local via Cloudflare tunnel (para pruebas en Villena)
    private const val BASE_URL = "https://plain-api.onrender.com/"

    // Token storage
    private var userToken: String? = null
    private var businessToken: String? = null

    // Flow that emits when a 401 is received — the UI observes this to redirect to login
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    fun setUserToken(token: String?) {
        userToken = token
    }

    fun setBusinessToken(token: String?) {
        businessToken = token
    }

    fun getUserToken(): String? = userToken
    fun getBusinessToken(): String? = businessToken

    // Legacy
    fun setToken(token: String?) {
        setUserToken(token)
    }
    fun getToken(): String? = getUserToken()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
        val path = chain.request().url.encodedPath

        val tokenToUse = when {
            path.contains("api/business") && businessToken != null -> businessToken
            userToken != null -> userToken
            else -> null
        }
        tokenToUse?.let {
            request.addHeader("Authorization", "Bearer $it")
        }

        val response = chain.proceed(request.build())

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
            _sessionExpired.tryEmit(Unit)
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply {
            // Logging interceptor solo en debug (importado vía debugImplementation)
            try {
                val loggingInterceptor = Class.forName("okhttp3.logging.HttpLoggingInterceptor")
                    .getDeclaredConstructor().newInstance()
                loggingInterceptor.javaClass.getMethod("setLevel",
                    Class.forName("okhttp3.logging.HttpLoggingInterceptor\$Level"))
                    .invoke(loggingInterceptor,
                        Class.forName("okhttp3.logging.HttpLoggingInterceptor\$Level").getField("BODY").get(null))
                addInterceptor(loggingInterceptor as Interceptor)
            } catch (_: Exception) {
                // Logging interceptor only available in debug builds — silently skip in release
            }
        }
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
