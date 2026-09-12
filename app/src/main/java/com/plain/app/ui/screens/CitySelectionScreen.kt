package com.plain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Estructura: país -> regiones -> ciudades soportadas por la app
private data class CityOption(val code: String, val name: String)
private data class RegionOption(val name: String, val cities: List<CityOption>)
private data class CountryOption(val name: String, val regions: List<RegionOption>)

private val COUNTRIES = listOf(
    CountryOption(
        name = "España",
        regions = listOf(
            RegionOption(
                name = "Comunidad Valenciana",
                cities = listOf(
                    CityOption("ALICANTE", "Alicante"),
                    CityOption("VILLENA", "Villena"),
                    CityOption("VALENCIA", "Valencia"),
                )
            ),
            RegionOption(
                name = "Cataluña",
                cities = listOf(CityOption("BARCELONA", "Barcelona"))
            ),
            RegionOption(
                name = "Comunidad de Madrid",
                cities = listOf(CityOption("MADRID", "Madrid"))
            ),
            RegionOption(
                name = "Andalucía",
                cities = listOf(CityOption("SEVILLA", "Sevilla"))
            ),
        )
    ),
)

@Composable
fun CitySelectionScreen(
    onCitySelected: (String) -> Unit
) {
    var selectedCountry by remember { mutableStateOf(COUNTRIES.first().name) }
    var selectedRegion by remember { mutableStateOf<String?>(null) }

    val country = COUNTRIES.firstOrNull { it.name == selectedCountry } ?: COUNTRIES.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Text(
                text = "PLΛIN",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Planes reales, sin complicaciones",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "¿Dónde quieres planear?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // País
            DropdownSelector(
                label = "País",
                value = selectedCountry,
                options = COUNTRIES.map { it.name },
                onSelect = {
                    selectedCountry = it
                    selectedRegion = null
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Región (una vez elegido país)
            DropdownSelector(
                label = "Región",
                value = selectedRegion ?: "Selecciona una región",
                options = country.regions.map { it.name },
                onSelect = { selectedRegion = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ciudades de la región seleccionada
            val region = country.regions.firstOrNull { it.name == selectedRegion }
            if (region != null) {
                region.cities.forEach { city ->
                    CityRow(
                        name = city.name,
                        planCount = "Planes disponibles",
                        onClick = { onCitySelected(city.code) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                Text(
                    text = "Elige una región para ver las ciudades",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Otra ciudad: escribir cualquier ciudad. Si no tiene planes,
            // PLΛIN genera planes locales automáticamente.
            var customCity by remember { mutableStateOf("") }
            Text(
                text = "¿No está tu ciudad?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = customCity,
                onValueChange = { if (it.length <= 40) customCity = it },
                placeholder = { Text("Escribe tu ciudad…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (customCity.isNotBlank()) {
                        IconButton(onClick = {
                            onCitySelected(customCity.trim().uppercase())
                        }) {
                            Text("→", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Si aún no hay planes, los creamos al momento ✨",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Desliza → te gusta  |  Desliza ← siguiente",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, fontWeight = FontWeight.Medium)
                    Text(if (expanded) "▲" else "▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CityRow(
    name: String,
    planCount: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = planCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "→",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
