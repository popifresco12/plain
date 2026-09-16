package com.plain.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.plain.app.data.*
import kotlinx.coroutines.launch

/**
 * Panel de empresa: saldo, métricas de las campañas y gestión de planes
 * patrocinados (crear / borrar / recargar). Antes no existía ninguna pantalla,
 * así que aunque el backend llevaba tiempo listo, ningún negocio podía pagar
 * por aparecer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDashboardScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var business by remember { mutableStateOf<BusinessResponse?>(null) }
    var stats by remember { mutableStateOf<BusinessStats?>(null) }
    var plans by remember { mutableStateOf<List<SponsoredPlanResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var showCreate by remember { mutableStateOf(false) }
    var showTopUp by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SponsoredPlanResponse?>(null) }
    var processing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        loading = true
        try {
            val me = ApiClient.service.getBusinessMe()
            if (me.isSuccessful) business = me.body() else error = apiErrorMessage(me)

            val st = ApiClient.service.getBusinessStats()
            if (st.isSuccessful) stats = st.body() else error = apiErrorMessage(st)

            val pl = ApiClient.service.getBusinessPlans()
            if (pl.isSuccessful) plans = pl.body() ?: emptyList() else error = apiErrorMessage(pl)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Error de conexión: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    fun deletePlan(plan: SponsoredPlanResponse) {
        processing = true
        scope.launch {
            try {
                val resp = ApiClient.service.deleteSponsoredPlan(plan.id)
                if (resp.isSuccessful) {
                    val refund = resp.body()?.refundedCents ?: 0
                    feedback = if (refund > 0)
                        "Campaña eliminada · devueltos ${euros(refund)}"
                    else "Campaña eliminada"
                    refreshKey++
                } else error = apiErrorMessage(resp)
            } catch (e: Exception) {
                error = "Error de conexión: ${e.localizedMessage}"
            } finally {
                processing = false
                toDelete = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(business?.companyName ?: "Panel Business", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Saldo ${euros(business?.balanceCents ?: 0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = {
                        AuthManager.clearBusinessToken()
                        onLoggedOut()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (loading && stats == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (error != null) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    error!!, color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    if (feedback != null) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    feedback!!, color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                "Saldo", euros(business?.balanceCents ?: 0),
                                MaterialTheme.colorScheme.primary, Modifier.weight(1f)
                            )
                            MetricCard(
                                "Gastado", euros(stats?.totalSpentCents ?: 0),
                                MaterialTheme.colorScheme.secondary, Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                "Campañas", "${stats?.activePlans ?: 0} / ${stats?.totalPlans ?: 0}",
                                MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)
                            )
                            MetricCard(
                                "Me gusta", "${stats?.totalLikes ?: 0}",
                                LikeGreenLocal(), Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showCreate = true },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Nueva campaña")
                            }
                            OutlinedButton(
                                onClick = { showTopUp = true },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Recargar")
                            }
                        }
                    }

                    item {
                        Text(
                            "Tus campañas",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (plans.isEmpty()) {
                        item {
                            Text(
                                "Aún no has publicado ninguna campaña.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(plans, key = { it.id }) { plan ->
                        SponsoredPlanRow(plan, processing) { toDelete = plan }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateCampaignDialog(
            balanceCents = business?.balanceCents ?: 0,
            onDismiss = { showCreate = false },
            onCreated = { msg ->
                feedback = msg
                showCreate = false
                refreshKey++
            },
            onError = { error = it }
        )
    }

    if (showTopUp) {
        TopUpDialog(
            onDismiss = { showTopUp = false },
            onManualTopUp = { amountCents ->
                processing = true
                scope.launch {
                    try {
                        val resp = ApiClient.service.topUpBalance(BudgetTopUpRequest(amountCents))
                        if (resp.isSuccessful) {
                            feedback = "Saldo recargado · nuevo saldo ${euros(resp.body()?.newBalanceCents ?: 0)}"
                            showTopUp = false
                            refreshKey++
                        } else {
                            error = apiErrorMessage(resp)
                            showTopUp = false
                        }
                    } catch (e: Exception) {
                        error = "Error de conexión: ${e.localizedMessage}"
                    } finally {
                        processing = false
                    }
                }
            },
            onStripeCheckout = { amountCents ->
                processing = true
                scope.launch {
                    try {
                        val resp = ApiClient.service.createCheckoutSession(amountCents)
                        val url = resp.body()?.url
                        if (resp.isSuccessful && !url.isNullOrBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            showTopUp = false
                        } else {
                            error = apiErrorMessage(resp)
                            showTopUp = false
                        }
                    } catch (e: Exception) {
                        error = "Error de conexión: ${e.localizedMessage}"
                    } finally {
                        processing = false
                    }
                }
            }
        )
    }

    toDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar campaña?") },
            text = {
                Text(
                    "«${plan.title}» dejará de mostrarse. El presupuesto no gastado " +
                        "(${euros(plan.budgetCents - plan.spentCents)}) vuelve a tu saldo."
                )
            },
            confirmButton = {
                TextButton(onClick = { deletePlan(plan) }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

// ── helpers de formato ──────────────────────────────────────────────────────

internal fun euros(cents: Int): String {
    val v = cents / 100.0
    return if (v % 1.0 == 0.0) "${v.toInt()}€" else String.format("%.2f€", v)
}

@Composable
private fun LikeGreenLocal() = com.plain.app.ui.theme.LikeGreen

@Composable
private fun MetricCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.14f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SponsoredPlanRow(plan: SponsoredPlanResponse, busy: Boolean, onDelete: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${plan.emoji} ${plan.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (plan.isActive) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        if (plan.isActive) "Activa" else "Sin presupuesto",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (plan.isActive) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "${plan.city} · ${plan.category} · ${plan.duration} · ${plan.price}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Presupuesto ${euros(plan.budgetCents)}", style = MaterialTheme.typography.labelSmall)
                Text("Gastado ${euros(plan.spentCents)}", style = MaterialTheme.typography.labelSmall)
                Text("Quedan ${plan.likesRemaining} me gusta", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Formulario de campaña. El backend descuenta el presupuesto del saldo al crearla,
 * así que avisamos antes si no llega.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCampaignDialog(
    balanceCents: Int,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
    onError: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("2h") }
    var emoji by remember { mutableStateOf("✨") }
    var planType by remember { mutableStateOf("AMBOS") }
    var category by remember { mutableStateOf("Ocio") }
    var budget by remember { mutableStateOf("10") }
    var costPerLike by remember { mutableStateOf("0.50") }
    var localError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = listOf("Cultura", "Naturaleza", "Gastronomía", "Compras", "Ocio", "Deporte", "Música")
    val types = listOf("SOLO" to "Solo", "PAREJA" to "Pareja", "AMBOS" to "Amigos")

    fun cents(text: String): Int? {
        val v = text.replace(",", ".").trim().toDoubleOrNull() ?: return null
        return Math.round(v * 100).toInt()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva campaña") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Título") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descripción") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = city, onValueChange = { city = it }, label = { Text("Ciudad") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                    supportingText = { Text("Aparecerá también en ciudades cercanas (radio del usuario)") }
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it }, label = { Text("Dónde (dirección)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = price, onValueChange = { price = it }, label = { Text("Precio") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small
                    )
                    OutlinedTextField(
                        value = duration, onValueChange = { duration = it }, label = { Text("Duración") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small
                    )
                    OutlinedTextField(
                        value = emoji, onValueChange = { if (it.length <= 2) emoji = it },
                        label = { Text("Icono") }, singleLine = true,
                        modifier = Modifier.width(84.dp), shape = MaterialTheme.shapes.small
                    )
                }

                Text("¿Con quién?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { (value, label) ->
                        FilterChip(
                            selected = planType == value, onClick = { planType = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Categoría", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = category == c, onClick = { category = c },
                            label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = budget, onValueChange = { budget = it }, label = { Text("Presupuesto €") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small
                    )
                    OutlinedTextField(
                        value = costPerLike, onValueChange = { costPerLike = it },
                        label = { Text("€ por me gusta") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small
                    )
                }
                Text(
                    "Saldo disponible: ${euros(balanceCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                localError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    val budgetCents = cents(budget)
                    val cpl = cents(costPerLike)
                    localError = when {
                        title.isBlank() || description.isBlank() -> "Título y descripción son obligatorios"
                        city.isBlank() -> "Indica la ciudad"
                        budgetCents == null || budgetCents < 100 -> "Presupuesto mínimo 1€"
                        cpl == null || cpl < 1 -> "El coste por me gusta debe ser al menos 0,01€"
                        budgetCents > balanceCents -> "Saldo insuficiente: tienes ${euros(balanceCents)}. Recarga antes de publicar."
                        else -> null
                    }
                    if (localError != null) return@TextButton

                    sending = true
                    scope.launch {
                        try {
                            val resp = ApiClient.service.createSponsoredPlan(
                                SponsoredPlanCreateRequest(
                                    title = title.trim(),
                                    description = description.trim(),
                                    location = location.ifBlank { city }.trim(),
                                    price = price.ifBlank { "0€" }.trim(),
                                    planType = planType,
                                    duration = duration.trim(),
                                    category = category,
                                    city = city.trim().uppercase(),
                                    emoji = emoji,
                                    tags = listOf(category.lowercase()),
                                    budgetCents = budgetCents!!,
                                    costPerLikeCents = cpl!!
                                )
                            )
                            if (resp.isSuccessful) {
                                onCreated("Campaña publicada en ${city.uppercase()}")
                            } else {
                                localError = apiErrorMessage(resp)
                            }
                        } catch (e: Exception) {
                            localError = "Error de conexión: ${e.localizedMessage}"
                        } finally {
                            sending = false
                        }
                    }
                }
            ) { Text("Publicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Recarga: intenta Stripe Checkout y, si no está configurado, usa el saldo simulado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopUpDialog(
    onDismiss: () -> Unit,
    onManualTopUp: (Int) -> Unit,
    onStripeCheckout: (Int) -> Unit
) {
    var amount by remember { mutableStateOf(20) }
    val options = listOf(10, 20, 50, 100)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recargar saldo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "El saldo se consume según los me gusta que reciben tus campañas.",
                    style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { value ->
                        FilterChip(
                            selected = amount == value,
                            onClick = { amount = value },
                            label = { Text("${value}€") }
                        )
                    }
                }
                Text("Total: $amount€", style = MaterialTheme.typography.titleSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onStripeCheckout(amount * 100) }) { Text("Pagar con tarjeta") }
        },
        dismissButton = {
            TextButton(onClick = { onManualTopUp(amount * 100) }) { Text("Añadir saldo (pruebas)") }
        }
    )
}
