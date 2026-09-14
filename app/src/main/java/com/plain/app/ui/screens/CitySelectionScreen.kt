package com.plain.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Selector de ciudad con el listado mundial completo (226 países, ~2.700 regiones,
 * ~25.000 ciudades) cargado desde assets/world_places.json.
 *
 * Si la ciudad elegida no tiene planes, el backend los genera al momento
 * (llamada a POST /api/cities/{city}/bootstrap desde la pantalla de swipe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySelectionScreen(
    onCitySelected: (String) -> Unit
) {
    val context = LocalContext.current
    var places by remember { mutableStateOf<Map<String, Map<String, List<String>>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedCountry by remember { mutableStateOf("España") }
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var customCity by remember { mutableStateOf("") }

    // Cargar el listado mundial desde assets (una sola vez)
    LaunchedEffect(Unit) {
        places = withContext(Dispatchers.IO) { loadWorldPlaces(context) }
        loading = false
    }

    val countryNames = remember(places) {
        val list = places.keys.toMutableList()
        list.sort()
        if ("España" in list) { list.remove("España"); list.add(0, "España") }
        list
    }
    val regions = remember(places, selectedCountry) {
        (places[selectedCountry]?.keys ?: emptySet()).sorted()
    }
    val citiesInRegion = remember(places, selectedCountry, selectedRegion) {
        selectedRegion?.let { places[selectedCountry]?.get(it) } ?: emptyList()
    }

    // Búsqueda global (todas las ciudades de todos los países)
    val searchResults = remember(places, query) {
        val q = query.trim()
        if (q.length < 2) emptyList()
        else buildList {
            for ((country, regionsMap) in places) {
                for ((region, cities) in regionsMap) {
                    for (city in cities) {
                        if (city.startsWith(q, ignoreCase = true) ||
                            city.contains(" $q", ignoreCase = true)
                        ) {
                            add(Triple(city, region, country))
                            if (size >= 60) return@buildList
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "PLΛIN",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "¿Dónde quieres planear?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // Buscador global de ciudades
        OutlinedTextField(
            value = query,
            onValueChange = { if (it.length <= 40) query = it },
            placeholder = { Text("Busca tu ciudad (ej. Villena, Málaga…)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Cargando ciudades del mundo…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {

        Spacer(Modifier.height(12.dp))

        if (query.trim().length >= 2) {
            // ---- RESULTADOS DE BÚSQUEDA ----
            if (searchResults.isEmpty()) {
                EmptyState("No encontramos \"${query.trim()}\"", Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(searchResults) { (city, region, country) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCitySelected(city.uppercase()) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(city, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                Text("$region · $country",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider()
                    }
                }
            }
        } else {
            // ---- NAVEGACIÓN PAÍS → REGIÓN → CIUDAD ----
            var countryExpanded by remember { mutableStateOf(false) }
            var regionExpanded by remember { mutableStateOf(false) }

            Row(modifier = Modifier.fillMaxWidth()) {
                // País
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { countryExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(selectedCountry, maxLines = 1, fontSize = 13.sp)
                    }
                    DropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false },
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        countryNames.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, fontSize = 14.sp) },
                                onClick = {
                                    selectedCountry = c
                                    selectedRegion = null
                                    countryExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Región
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (regions.isNotEmpty()) regionExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(selectedRegion ?: "${regions.size} regiones",
                            maxLines = 1, fontSize = 13.sp)
                    }
                    DropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false },
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        regions.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r, fontSize = 14.sp) },
                                onClick = { selectedRegion = r; regionExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedRegion == null) {
                EmptyState("Elige una región (${regions.size} en $selectedCountry)", Modifier.weight(1f))
            } else if (citiesInRegion.isEmpty()) {
                EmptyState("Sin ciudades listadas", Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(citiesInRegion) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCitySelected(city.uppercase()) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(city, style = MaterialTheme.typography.bodyLarge)
                        }
                        Divider()
                    }
                }
            }
        }

        // ---- CIUDAD QUE NO ESTÉ EN LA LISTA ----
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customCity,
                onValueChange = { if (it.length <= 40) customCity = it },
                placeholder = { Text("¿No está? Escribe tu ciudad") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val c = customCity.trim()
                    if (c.isNotEmpty()) onCitySelected(c.uppercase())
                },
                enabled = customCity.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ir")
            }
        }
        Text(
            text = "Si aún no hay planes en tu ciudad, los creamos al momento ✨",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    )
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** Lee assets/world_places.json → { país: { región: [ciudades] } } */
private fun loadWorldPlaces(context: Context): Map<String, Map<String, List<String>>> {
    return try {
        val json = context.assets.open("world_places.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        val out = LinkedHashMap<String, Map<String, List<String>>>()
        for (country in root.keys()) {
            val regionsObj = root.optJSONObject(country) ?: continue
            val regions = LinkedHashMap<String, List<String>>()
            for (region in regionsObj.keys()) {
                val arr = regionsObj.optJSONArray(region) ?: continue
                val cities = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { cities.add(it) }
                regions[region] = cities
            }
            out[country] = regions
        }
        out
    } catch (e: Exception) {
        emptyMap()
    }
}
