package com.plain.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Backend local via Cloudflare tunnel (para pruebas en Villena)
    const val BASE_URL = "https://plain-api.onrender.com/"

    /** URL del WebSocket de tiempo real (mismo host, esquema wss). */
    val WS_URL: String get() = BASE_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"

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

    /** Cliente sin interceptores para renovar la sesión (evita recursión con el 401). */
    private val bareClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Cliente base para el WebSocket (sin timeout de lectura: la conexión es larga). */
    val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    private val refreshLock = Any()

    /**
     * Pide un access token nuevo con el refresh token. Síncrono (hilo de OkHttp).
     * Devuelve el token nuevo o null si la sesión ya no es renovable.
     */
    fun refreshSessionBlocking(staleToken: String?): String? = synchronized(refreshLock) {
        // Otra petición ya lo renovó mientras esperábamos el cerrojo
        val current = userToken
        if (current != null && current != staleToken) return current
        val refresh = AuthManager.getRefreshToken() ?: return null
        return try {
            val body = Gson().toJson(RefreshRequest(refresh))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(BASE_URL + "api/token/refresh").post(body).build()
            bareClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val parsed = Gson().fromJson(resp.body?.string(), RefreshResponse::class.java)
                AuthManager.saveSession(parsed.accessToken, parsed.refreshToken)
                parsed.accessToken
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Ante un 401 de usuario, renueva la sesión una vez y repite la petición. */
    private val sessionAuthenticator = Authenticator { _, response ->
        val path = response.request.url.encodedPath
        val esAuth = path.contains("api/login") || path.contains("api/register") ||
            path.contains("api/token/refresh") || path.contains("api/business")
        // Solo un reintento: si el propio reintento vuelve con 401, se rinde
        if (esAuth || response.priorResponse != null) return@Authenticator null
        val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = refreshSessionBlocking(stale) ?: return@Authenticator null
        response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(sessionAuthenticator)
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
