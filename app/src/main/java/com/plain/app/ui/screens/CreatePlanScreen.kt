package com.plain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.PlanCreateRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    city: String,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("0€") }
    var duration by remember { mutableStateOf("2h") }
    var availability by remember { mutableStateOf("Todo el año") }
    var availableFrom by remember { mutableStateOf("") }
    var availableUntil by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Ocio") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = listOf("Ocio", "Cultura", "Gastronomía", "Naturaleza", "Compras", "Música", "Deporte")

    val cityLabel = when (city) {
        "BARCELONA" -> "🌊 Barcelona"
        "ALICANTE" -> "🌴 Alicante"
        else -> "🏰 Villena"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear plan · $cityLabel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Comparte un plan para que otros se apunten ✨",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Categoría (desplegable)
            var catExpanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Categoría",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { catExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category, fontWeight = FontWeight.Medium)
                            Text("▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    category = c
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título del plan *") },
                placeholder = { Text("Ej: Quedada para ver el atardecer en el castillo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción *") },
                placeholder = { Text("Qué se hace, qué llevar, cómo nos reconocemos...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Lugar *") },
                placeholder = { Text("Ej: Puerta del castillo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Precio") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duración") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Categoría") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("📅 Disponibilidad (opcional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = availableFrom,
                    onValueChange = { availableFrom = it },
                    label = { Text("Desde (AAAA-MM-DD)") },
                    placeholder = { Text("2026-09-04") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = availableUntil,
                    onValueChange = { availableUntil = it },
                    label = { Text("Hasta (AAAA-MM-DD)") },
                    placeholder = { Text("2026-09-08") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = recurring,
                onValueChange = { recurring = it },
                label = { Text("Días recurrentes (opcional)") },
                placeholder = { Text("MON,WED,FRI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (success) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("✅ Plan creado. ¡Ahora otros pueden unirse!", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank() || location.isBlank()) {
                        error = "Completa título, descripción y lugar"
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val resp = ApiClient.service.createPlan(
                                PlanCreateRequest(
                                    title = title.trim(),
                                    description = description.trim(),
                                    location = location.trim(),
                                    price = price.trim().ifBlank { "0€" },
                                    planType = "AMBOS",
                                    duration = duration.trim().ifBlank { "2h" },
                                    availability = availability,
                                    category = category.trim().ifBlank { "Ocio" },
                                    city = city,
                                    emoji = "📍",
                                    availableFrom = availableFrom.trim().ifBlank { null },
                                    availableUntil = availableUntil.trim().ifBlank { null },
                                    recurring = recurring.trim().ifBlank { null }
                                )
                            )
                            if (resp.isSuccessful) {
                                success = true
                                title = ""; description = ""; location = ""
                                onCreated()
                            } else {
                                error = "Error al crear (${resp.code()})"
                            }
                        } catch (e: Exception) {
                            error = "Error de conexión: ${e.localizedMessage}"
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Publicar plan", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
