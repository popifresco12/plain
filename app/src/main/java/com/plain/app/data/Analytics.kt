package com.plain.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Eventos de producto (visto, me gusta, descartado, abierto).
 *
 * Se acumulan y se envían en lote cada 15 eventos o al cerrar la pantalla: así
 * no se castiga la batería ni los datos del móvil. Sin esto no había forma de
 * saber qué contenido funciona; se decidía a ojo.
 */
object Analytics {

    private const val LOTE = 15
    private val pendientes = mutableListOf<EventItem>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var appCtx: Context? = null

    /** Se llama una vez al arrancar: así las pantallas no tienen que pasarlo. */
    fun init(context: Context) { appCtx = context.applicationContext }

    fun track(event: String, planId: Int? = null, city: String? = null) {
        synchronized(pendientes) {
            pendientes.add(EventItem(planId = planId, event = event, city = city))
            if (pendientes.size >= LOTE) enviar()
        }
    }

    /** Manda lo acumulado (llamar al salir de la pantalla principal). */
    fun flush() {
        synchronized(pendientes) {
            if (pendientes.isEmpty()) return
        }
        enviar()
    }

    private fun enviar() {
        val lote: List<EventItem>
        synchronized(pendientes) {
            if (pendientes.isEmpty()) return
            lote = pendientes.take(LOTE)
            pendientes.removeAll(lote)
        }
        scope.launch {
            try {
                ApiClient.service.sendEvents(EventsRequest(lote))
            } catch (_: Exception) {
                // Si falla, no reintentamos: la analítica no debe molestar al usuario
            }
        }
    }
}
