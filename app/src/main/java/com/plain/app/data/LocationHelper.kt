package com.plain.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Detecta la ciudad del usuario por GPS (aproximada por cercanía a las ciudades soportadas).
 * Sin dependencias externas: usa el LocationManager del sistema y distancias Haversine.
 */
object LocationHelper {

    // Ciudades soportadas: nombre -> (lat, lon)
    private val CITIES = mapOf(
        "BARCELONA" to City(41.3874, 2.1686),
        "VILLENA" to City(38.6354, -0.8661),
        "ALICANTE" to City(38.3452, -0.4810),
        "MADRID" to City(40.4168, -3.7038),
        "VALENCIA" to City(39.4699, -0.3763),
        "SEVILLA" to City(37.3891, -5.9845),
    )

    private data class City(val lat: Double, val lon: Double)

    /** Comprueba si tenemos permiso de ubicación. */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /** Última ubicación conocida del dispositivo (sin diálogos, lo que haya). */
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) return loc
            } catch (_: Exception) {}
        }
        return null
    }

    /** Devuelve la ciudad soportada más cercana (o null si no hay ubicación). */
    fun detectCity(context: Context): String? {
        val loc = lastKnownLocation(context) ?: return null
        var best: String? = null
        var bestDist = Double.MAX_VALUE
        for ((name, city) in CITIES) {
            val d = haversine(loc.latitude, loc.longitude, city.lat, city.lon)
            if (d < bestDist) {
                bestDist = d
                best = name
            }
        }
        // Solo si estamos "cerca" de alguna ciudad soportada (< 60 km)
        return if (bestDist < 60_000) best else null
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
