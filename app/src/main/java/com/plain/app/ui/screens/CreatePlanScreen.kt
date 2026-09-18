package com.plain.app.ui.screens

import com.plain.app.R
import androidx.compose.ui.res.stringResource
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
import com.plain.app.ui.components.ImageSearchDialog
import com.plain.app.data.PlanCreateRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    city: String,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    var createAttempt by remember { mutableIntStateOf(0) }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var buscaFoto by remember { mutableStateOf(false) }

    // Crear el plan desde un LaunchedEffect (no desde scope.launch en onClick):
    // si la composición sale, la corrutina se cancela limpiamente y nunca escribe
    // en estado muerto (evita "coroutine scope left the composition")
    LaunchedEffect(createAttempt) {
        if (createAttempt > 0) {
            saving = true
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
                        imageUrl = imageUrl,
                        availableFrom = availableFrom.trim().ifBlank { null },
                        availableUntil = availableUntil.trim().ifBlank { null },
                        recurring = recurring.trim().ifBlank { null }
                    )
                )
                if (resp.isSuccessful) {
                    // Marcar success como ÚLTIMA acción: navegar dispara la salida
                    // de composición, y cualquier write posterior (p.ej. saving=false
                    // en finally) lanzaría "coroutine scope left the composition".
                    success = true
                    return@LaunchedEffect
                } else {
                    error = "Error al crear (${resp.code()})"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelación limpia (p.ej. el usuario navegó atrás): no tocar estado
                return@LaunchedEffect
            } catch (e: Exception) {
                error = "Error de conexión: ${e.localizedMessage}"
            }
            saving = false
        }
    }

    // Navegar SOLO cuando success se pone a true y la corrutina ya terminó
    LaunchedEffect(success) {
        if (success) {
            onCreated()
        }
    }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                text = stringResource(R.string.plan_share_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Categoría (desplegable)
            var catExpanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.swipe_category),
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
                label = { Text(stringResource(R.string.plan_title)) },
                placeholder = { Text(stringResource(R.string.plan_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.plan_desc_label)) },
                placeholder = { Text(stringResource(R.string.plan_desc_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text(stringResource(R.string.plan_place)) },
                placeholder = { Text(stringResource(R.string.plan_place_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.plan_price)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text(stringResource(R.string.plan_duration)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.swipe_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.plan_availability), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = availableFrom,
                    onValueChange = { availableFrom = it },
                    label = { Text(stringResource(R.string.plan_from)) },
                    placeholder = { Text("2026-09-04") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = availableUntil,
                    onValueChange = { availableUntil = it },
                    label = { Text(stringResource(R.string.plan_until)) },
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
                label = { Text(stringResource(R.string.plan_recurring)) },
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
                Text(stringResource(R.string.plan_created), color = MaterialTheme.colorScheme.primary)
            }

            // Foto del plan: se busca online con licencia libre (o se pega una URL).
            // No se suben ficheros: el servidor borra el disco en cada despliegue.
            OutlinedButton(
                onClick = { buscaFoto = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (imageUrl == null) "📷 Añadir foto (opcional)" else "📷 Foto elegida ✓",
                    fontWeight = FontWeight.Medium
                )
            }
            if (imageUrl != null) {
                Text(
                    imageUrl ?: "",
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { imageUrl = null }) { Text(stringResource(R.string.plan_remove_photo)) }
            }
            if (buscaFoto) {
                ImageSearchDialog(
                    sugerencia = title.trim().ifBlank { category },
                    onDismiss = { buscaFoto = false },
                    onPick = { url ->
                        imageUrl = url
                        buscaFoto = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank() || location.isBlank()) {
                        error = context.getString(R.string.err_need_title)
                        return@Button
                    }
                    if (!saving) {
                        error = null
                        createAttempt++
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.plan_publish), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
