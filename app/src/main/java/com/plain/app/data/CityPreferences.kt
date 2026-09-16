package com.plain.app.data

import android.content.Context

/**
 * Almacena la ciudad seleccionada por el usuario.
 * Si nunca se ha elegido (null), la app intenta detectarla por GPS.
 */
object CityPreferences {
    private const val PREFS = "plain_prefs"
    private const val KEY_CITY = "selected_city"
    private const val KEY_GPS_DETECTED = "gps_city_detected"
    private const val KEY_RADIUS = "search_radius_km"
    private const val KEY_FILTER_TYPE = "filter_plan_type"
    private const val KEY_FILTER_CATEGORY = "filter_category"
    private const val KEY_FILTER_FREE = "filter_free_only"
    private const val KEY_CHAT_SEEN = "chat_seen_at_"

    fun getCity(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CITY, null)
    }

    fun setCity(context: Context, city: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CITY, city).apply()
    }

    /** Ciudad detectada por GPS la última vez (para mostrar "detectada" en ajustes). */
    fun getGpsDetectedCity(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GPS_DETECTED, null)
    }

    fun setGpsDetectedCity(context: Context, city: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GPS_DETECTED, city).apply()
    }

    /**
     * Radio de búsqueda en km: 0 = solo la ciudad elegida.
     * Permite ver planes de ciudades cercanas sin cambiar de ciudad.
     */
    fun getRadiusKm(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_RADIUS, 0)

    fun setRadiusKm(context: Context, km: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_RADIUS, km).apply()
    }

    // ── Filtros del swipe (null = sin filtro) ──

    fun getFilterType(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FILTER_TYPE, null)

    fun getFilterCategory(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FILTER_CATEGORY, null)

    fun getFilterFree(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FILTER_FREE, false)

    /** Marca temporal (epoch ms) de la última vez que se abrió el chat de una quedada. */
    fun getChatSeen(context: Context, groupId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_CHAT_SEEN + groupId, 0L)

    fun setChatSeen(context: Context, groupId: Int, atMillis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CHAT_SEEN + groupId, atMillis).apply()
    }

    fun setFilters(context: Context, type: String?, category: String?, freeOnly: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FILTER_TYPE, type)
            .putString(KEY_FILTER_CATEGORY, category)
            .putBoolean(KEY_FILTER_FREE, freeOnly)
            .apply()
    }
}
