package com.plain.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder

/**
 * WebSocket de tiempo real (uno por app, mientras está en primer plano).
 *
 * Eventos del servidor: `message` (chat), `typing`, `notification`, `hello`, `pong`.
 * Reconecta solo con espera creciente (1 s → 30 s). Si el token ha caducado
 * (cierre 4401) renueva la sesión y vuelve a entrar.
 */
object RealtimeClient {
    private val _events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val events: SharedFlow<JsonObject> = _events

    private val _unread = MutableStateFlow(0)
    /** Avisos sin leer (campana de la pantalla principal). */
    val unread: StateFlow<Int> = _unread

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** Chat abierto ahora mismo: sus mensajes no generan notificación del sistema. */
    @Volatile var activeGroupId: Int? = null

    private val gson = Gson()
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var socket: WebSocket? = null
    @Volatile private var wanted = false
    private var attempts = 0

    fun setUnread(n: Int) { _unread.value = n.coerceAtLeast(0) }

    fun start(context: Context) {
        appContext = context.applicationContext
        wanted = true
        main.post { connect() }
    }

    fun stop() {
        wanted = false
        main.removeCallbacksAndMessages(null)
        socket?.close(1000, "app en segundo plano")
        socket = null
        _connected.value = false
    }

    fun sendTyping(groupId: Int) {
        socket?.send("{\"type\":\"typing\",\"group_id\":$groupId}")
    }

    private fun connect() {
        if (!wanted || socket != null) return
        val token = ApiClient.getUserToken() ?: return
        val url = ApiClient.WS_URL + "?token=" + URLEncoder.encode(token, "UTF-8")
        socket = ApiClient.wsClient.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun scheduleReconnect(refreshFirst: Boolean, staleToken: String?) {
        socket = null
        _connected.value = false
        if (!wanted) return
        if (refreshFirst) {
            // Renovar en un hilo aparte (es una llamada de red síncrona)
            Thread {
                val fresh = ApiClient.refreshSessionBlocking(staleToken)
                if (fresh != null) main.post { connect() }
                // Sin sesión renovable: no insistir; la app ya pedirá login
            }.start()
            return
        }
        val delayMs = (1000L shl attempts.coerceAtMost(5)).coerceAtMost(30_000L)
        attempts++
        main.postDelayed({ connect() }, delayMs)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempts = 0
            _connected.value = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = try {
                gson.fromJson(text, JsonObject::class.java)
            } catch (_: Exception) {
                return
            }
            if (obj.get("type")?.asString == "notification") {
                val n = try {
                    gson.fromJson(obj.getAsJsonObject("notification"), NotificationItem::class.java)
                } catch (_: Exception) {
                    null
                }
                if (n != null) {
                    val ctx = appContext
                    val esChatAbierto = n.kind == "message" && n.intData("group_id") == activeGroupId
                    if (!esChatAbierto) _unread.value = _unread.value + 1
                    if (ctx != null) PlainNotifier.show(ctx, n)
                }
            }
            _events.tryEmit(obj)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket && socket != null) return
            val token = ApiClient.getUserToken()
            main.post { scheduleReconnect(refreshFirst = code == 4401, staleToken = token) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket && socket != null) return
            val token = ApiClient.getUserToken()
            // El servidor rechaza el token antes de aceptar → a veces llega como fallo con 403/401
            val authFail = response?.code == 401 || response?.code == 403
            main.post { scheduleReconnect(refreshFirst = authFail, staleToken = token) }
        }
    }
}
