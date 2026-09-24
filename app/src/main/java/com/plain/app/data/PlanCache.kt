package com.plain.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Caché offline de planes por ciudad: la app abre al instante con lo último
 * visto y sigue funcionando sin cobertura. JSON en el almacenamiento interno
 * (sin Room: son listas pequeñas y así no hace falta procesador de anotaciones).
 */
object PlanCache {
    private val gson = Gson()
    private val type = object : TypeToken<List<PlanResponse>>() {}.type
    private const val MAX_PLANS = 200

    private fun file(ctx: Context, key: String): File {
        val safe = key.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(60)
        return File(File(ctx.filesDir, "plan_cache").apply { mkdirs() }, "$safe.json")
    }

    fun key(city: String, radiusKm: Int): String = "${city}_r$radiusKm"

    fun save(ctx: Context, key: String, plans: List<PlanResponse>) {
        try {
            val f = file(ctx, key)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(gson.toJson(plans.take(MAX_PLANS)))
            tmp.renameTo(f)  // escritura atómica: nunca queda un JSON a medias
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context, key: String): List<PlanResponse>? = try {
        val f = file(ctx, key)
        if (f.exists()) gson.fromJson<List<PlanResponse>>(f.readText(), type) else null
    } catch (_: Exception) {
        null
    }

    /** Edad de la caché en minutos (para el aviso «guardados hace X»). */
    fun ageMinutes(ctx: Context, key: String): Long? {
        val f = file(ctx, key)
        return if (f.exists()) (System.currentTimeMillis() - f.lastModified()) / 60_000 else null
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, "plan_cache").deleteRecursively()
    }
}
