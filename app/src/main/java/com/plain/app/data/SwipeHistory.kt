package com.plain.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial local de planes descartados/guardados.
 *
 * Se guarda en el propio móvil (los IDs ya se guardaban para no repetir tarjetas,
 * pero sin los datos no había forma de mostrar la lista). No ocupa red ni servidor.
 */
object SwipeHistory {

    private const val PREFS = "plain_history"
    private const val KEY = "entries"
    private const val MAX = 60
    private var appCtx: Context? = null

    fun init(context: Context) { appCtx = context.applicationContext }

    data class Entrada(
        val id: Int,
        val title: String,
        val emoji: String,
        val city: String,
        val category: String,
        val price: String,
        val liked: Boolean,
        val at: Long
    )

    fun add(plan: PlanResponse, liked: Boolean) {
        val context = appCtx ?: return
        val lista = list().filterNot { it.id == plan.id }.toMutableList()
        lista.add(
            Entrada(
                id = plan.id,
                title = plan.title,
                emoji = plan.emoji,
                city = plan.city,
                category = plan.category,
                price = plan.price,
                liked = liked,
                at = System.currentTimeMillis()
            )
        )
        guardar(context, lista.takeLast(MAX))
    }

    fun list(): List<Entrada> {
        val context = appCtx ?: return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entrada(
                    id = o.optInt("id"),
                    title = o.optString("title"),
                    emoji = o.optString("emoji"),
                    city = o.optString("city"),
                    category = o.optString("category"),
                    price = o.optString("price"),
                    liked = o.optBoolean("liked"),
                    at = o.optLong("at")
                )
            }.reversed()   // lo más reciente primero
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear() {
        val context = appCtx ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun guardar(context: Context, lista: List<Entrada>) {
        val arr = JSONArray()
        lista.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("emoji", e.emoji)
                put("city", e.city)
                put("category", e.category)
                put("price", e.price)
                put("liked", e.liked)
                put("at", e.at)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
