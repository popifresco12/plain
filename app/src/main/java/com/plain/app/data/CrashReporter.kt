package com.plain.app.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.plain.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captura los fallos de la app y los envía al backend (`/api/crash-reports`).
 *
 * Sin esto, un crash en el móvil era invisible: no hay emulador, no hay analytics
 * y el único aviso era «no va». No usa Firebase a propósito (nada que configurar
 * ni dependencias nuevas): un POST al propio backend con el stack trace.
 *
 * - En el momento del fallo solo se **guarda en disco** (rápido y sin red: el
 *   proceso está muriendo).
 * - Los pendientes se **envían al arrancar** (`flush`), con reintento en el
 *   siguiente inicio si no hay conexión.
 */
object CrashReporter {

    private const val PREFS = "plain_crash"
    private const val KEY_PENDING = "pending"
    private const val MAX_PENDING = 10
    private const val ENDPOINT = "https://plain-api.onrender.com/api/crash-reports"

    @Volatile
    private var pantallaActual: String? = null

    /** Registra el capturador global. Llamar una sola vez, en onCreate. */
    fun install(context: Context) {
        val app = context.applicationContext

        (app as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    pantallaActual = activity.javaClass.simpleName
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )

        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            try {
                guardar(app, error)
            } catch (_: Throwable) {
                // Nunca enmascarar el fallo original por no poder registrarlo
            }
            previo?.uncaughtException(hilo, error)
        }
    }

    /** Reporta un error NO fatal (por ejemplo, un fallo raro de red). */
    fun report(context: Context, error: Throwable, etiqueta: String = "") {
        try {
            guardar(context.applicationContext, error, etiqueta)
        } catch (_: Throwable) {
        }
    }

    /** Manda los informes pendientes. Llamar al arrancar: no bloquea. */
    fun flush(context: Context) {
        val app = context.applicationContext
        val pendientes = leer(app)
        if (pendientes.isEmpty()) return

        Thread {
            val fallidos = mutableListOf<String>()
            for (json in pendientes) {
                if (!enviar(json)) fallidos.add(json)
            }
            escribir(app, fallidos)
        }.start()
    }

    // ── interno ─────────────────────────────────────────────────────────────

    private fun guardar(app: Context, error: Throwable, etiqueta: String = "") {
        val sello = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val mensaje = buildString {
            if (etiqueta.isNotBlank()) append("[").append(etiqueta).append("] ")
            append(error.javaClass.name)
            error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }

        val json = JSONObject().apply {
            put("app_version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            put("android_version", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("screen", pantallaActual ?: "desconocida")
            put("message", "$mensaje · $sello".take(2000))
            put("stacktrace", error.stackTraceToString().take(8000))
        }.toString()

        val cola = leer(app).toMutableList()
        cola.add(json)
        escribir(app, cola.takeLast(MAX_PENDING))
    }

    private fun leer(app: Context): List<String> =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PENDING, emptySet())
            ?.sorted()
            ?: emptyList()

    private fun escribir(app: Context, cola: List<String>) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_PENDING, cola.toSet()).apply()
    }

    private fun enviar(json: String): Boolean {
        var conexion: HttpURLConnection? = null
        return try {
            conexion = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conexion.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val codigo = conexion.responseCode
            codigo in 200..299
        } catch (_: Throwable) {
            false   // se reintenta en el próximo arranque
        } finally {
            conexion?.disconnect()
        }
    }
}
