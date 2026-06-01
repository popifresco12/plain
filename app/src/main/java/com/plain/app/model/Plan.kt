package com.plain.app.model

data class Plan(
    val id: Int,
    val title: String,
    val description: String,
    val location: String,
    val price: String,       // "0€", "5-10€", etc
    val type: PlanType,      // Solo / Pareja
    val duration: String,    // "2h", "Todo el día", etc
    val category: String,    // "Naturaleza", "Cultura", "Gastronomía"
    val city: City,
    val emoji: String
)

enum class PlanType { SOLO, PAREJA, AMBOS }

enum class City { BARCELONA, VILLENA }
