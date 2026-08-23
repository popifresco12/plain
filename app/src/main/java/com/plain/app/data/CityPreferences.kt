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
}
