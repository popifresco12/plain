package com.plain.app.ui.screens

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.plain.app.data.ApiClient
import com.plain.app.data.AuthManager
import com.plain.app.data.BudgetTopUpRequest
import com.plain.app.data.SponsoredPlanCreateRequest
import com.plain.app.data.SponsoredPlanResponse
import com.plain.app.data.BusinessStats
import com.plain.app.ui.theme.LikeGreen
import com.plain.app.ui.theme.NopeRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDashboardScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var stats by remember { mutableStateOf<BusinessStats?>(null) }
    var plans by remember { mutableStateOf<List<SponsoredPlanResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Load data
    fun loadData(showLoading: Boolean = true) {
        scope.launch {
            if (showLoading) loading = true else refreshing = true
            error = null
            try {
                val statsResp = ApiClient.service.getBusinessStats()
                val plansResp = ApiClient.service.getBusinessPlans()
                if (statsResp.isSuccessful) stats = statsResp.body()
                if (plansResp.isSuccessful) plans = plansResp.body() ?: emptyList()
            } catch (e: Exception) {
                error = "Error: ${e.localizedMessage}"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    // Top-up dialog state
    var showTopUp by remember { mutableStateOf(false) }
    var topUpAmount by remember { mutableStateOf("1000") }
    var topUpLoading by remember { mutableStateOf(false) }

    // Create plan dialog state
    var showCreatePlan by remember { mutableStateOf(false) }
    var createPlanLoading by remember { mutableStateOf(false) }
    var createPlanError by remember { mutableStateOf<String?>(null) }
    var planTitle by remember { mutableStateOf("") }
    var planEmoji by remember { mutableStateOf("🏷️") }
    var planDesc by remember { mutableStateOf("") }
    var planLocation by remember { mutableStateOf("") }
    var planPrice by remember { mutableStateOf("0€") }
    var planCity by remember { mutableStateOf("BARCELONA") }
    var planType by remember { mutableStateOf("AMBOS") }
    var planDuration by remember { mutableStateOf("2h") }
    var planCategory by remember { mutableStateOf("Ocio") }
    var planTags by remember { mutableStateOf("comida, romantico") }
    var planBudget by remember { mutableStateOf("500") }
    var planCpl by remember { mutableStateOf("10") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PLΛIN Business", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showTopUp = true }) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Recargar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onLogout) {
                        Text("Salir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando dashboard...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(error!!, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadData() }) { Text("Reintentar") }
                    }
                }
                else -> {
                    RefreshIndicator(
                        refreshing = refreshing,
                        onRefresh = { loadData(showLoading = false) },
                        indicatorScale = 1.2f
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Stats Cards
                            stats?.let { s ->
                                StatsGrid(stats = s)
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Top-up button (if no stats yet)
                            if (stats == null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Sin datos aún", style = MaterialTheme.typography.headlineSmall)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Recarga saldo y crea tu primer plan para ver estadísticas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { showTopUp = true }) { Text("Recargar saldo") }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Plans Section
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                crossAlignment = CrossAxisAlignment.Start
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📋 Mis Planes Patrocinados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Button(onClick = { showCreatePlan = true; resetCreatePlanForm() }) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Nuevo Plan")
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                if (plans.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("📭", fontSize = 48.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("No hay planes aún", style = MaterialTheme.typography.titleMedium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Crea tu primer plan patrocinado para empezar a recibir likes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(plans, key = { it.id }) { plan ->
                                            BusinessPlanCard(
                                                plan = plan,
                                                onPreview = { /* TODO: preview */ },
                                                onDelete = { deletePlan(plan.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top-up Dialog
            if (showTopUp) {
                TopUpDialog(
                    amount = topUpAmount,
                    onAmountChange = { topUpAmount = it },
                    onConfirm = { confirmTopUp() },
                    onDismiss = { showTopUp = false; topUpAmount = "1000" },
                    loading = topUpLoading
                )
            }

            // Create Plan Dialog
            if (showCreatePlan) {
                CreatePlanDialog(
                    title = planTitle, onTitleChange = { planTitle = it },
                    emoji = planEmoji, onEmojiChange = { planEmoji = it },
                    desc = planDesc, onDescChange = { planDesc = it },
                    location = planLocation, onLocationChange = { planLocation = it },
                    price = planPrice, onPriceChange = { planPrice = it },
                    city = planCity, onCityChange = { planCity = it },
                    type = planType, onTypeChange = { planType = it },
                    duration = planDuration, onDurationChange = { planDuration = it },
                    category = planCategory, onCategoryChange = { planCategory = it },
                    tags = planTags, onTagsChange = { planTags = it },
                    budget = planBudget, onBudgetChange = { planBudget = it },
                    cpl = planCpl, onCplChange = { planCpl = it },
                    error = createPlanError,
                    loading = createPlanLoading,
                    onConfirm = { confirmCreatePlan() },
                    onDismiss = { showCreatePlan = false; resetCreatePlanForm() }
                )
            }
        }
    }

    fun resetCreatePlanForm() {
        planTitle = ""
        planEmoji = "🏷️"
        planDesc = ""
        planLocation = ""
        planPrice = "0€"
        planCity = "BARCELONA"
        planType = "AMBOS"
        planDuration = "2h"
        planCategory = "Ocio"
        planTags = "comida, romantico"
        planBudget = "500"
        planCpl = "10"
        createPlanError = null
    }

    fun confirmTopUp() {
        val amount = topUpAmount.toIntOrNull() ?: return
        if (amount < 100 || amount > 100000) return
        topUpLoading = true
        scope.launch {
            try {
                val resp = ApiClient.service.topUpBalance(BudgetTopUpRequest(amount))
                if (resp.isSuccessful) {
                    showTopUp = false
                    topUpAmount = "1000"
                    loadData()
                }
            } catch (e: Exception) {
                // Error handled silently
            } finally {
                topUpLoading = false
            }
        }
    }

    fun confirmCreatePlan() {
        createPlanError = null
        if (planTitle.isBlank() || planDesc.isBlank() || planLocation.isBlank()) {
            createPlanError = "Título, descripción y ubicación son obligatorios"
            return
        }
        val tags = planTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (tags.isEmpty()) {
            createPlanError = "Añade al menos un tag"
            return
        }
        val budget = planBudget.toIntOrNull() ?: return
        val cpl = planCpl.toIntOrNull() ?: return
        if (budget < 100 || cpl < 1) {
            createPlanError = "Presupuesto mínimo 100¢, coste mínimo 1¢"
            return
        }

        createPlanLoading = true
        scope.launch {
            try {
                val resp = ApiClient.service.createSponsoredPlan(
                    SponsoredPlanCreateRequest(
                        title = planTitle,
                        description = planDesc,
                        location = planLocation,
                        price = planPrice,
                        planType = planType,
                        duration = planDuration,
                        category = planCategory,
                        city = planCity,
                        emoji = planEmoji,
                        tags = tags,
                        budgetCents = budget,
                        costPerLikeCents = cpl
                    )
                )
                if (resp.isSuccessful) {
                    showCreatePlan = false
                    resetCreatePlanForm()
                    loadData()
                } else {
                    createPlanError = "Error: ${resp.code()} - ${resp.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                createPlanError = "Error de conexión: ${e.localizedMessage}"
            } finally {
                createPlanLoading = false
            }
        }
    }

    fun deletePlan(planId: Int) {
        scope.launch {
            try {
                val resp = ApiClient.service.deleteSponsoredPlan(planId)
                if (resp.isSuccessful) {
                    loadData()
                }
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun StatsGrid(stats: BusinessStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // First row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = "${(stats.balanceCents / 100).toString().replace(".", ",")}€",
                label = "Saldo",
                sub = "Disponible",
                icon = Icons.Default.AttachMoney,
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                value = stats.totalLikes.toString(),
                label = "Likes Totales",
                sub = "Recibidos",
                icon = Icons.Default.CheckCircle,
                color = LikeGreen
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Second row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = "${stats.activePlans}/${stats.totalPlans}",
                label = "Planes",
                sub = "Activos/Total",
                icon = Icons.Default.Business,
                color = MaterialTheme.colorScheme.tertiary
            )
            StatCard(
                value = "${(stats.totalSpentCents / 100).toString().replace(".", ",")}€",
                label = "Gastado",
                sub = "En likes",
                icon = Icons.Default.Error,
                color = NopeRed
            )
        }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    sub: String,
    icon: androidx.compose.material.icons.filled.Icon,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun BusinessPlanCard(
    plan: SponsoredPlanResponse,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = if (plan.budgetCents > 0) (plan.spentCents.toFloat() / plan.budgetCents * 100).roundToInt() else 0
    val isActive = plan.isActive
    val progressColor = when {
        pct >= 90 -> NopeRed
        pct >= 70 -> MaterialTheme.colorScheme.tertiary
        else -> LikeGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plan.emoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PlanChip(label = "${plan.city}", color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        PlanChip(label = "${plan.costPerLikeCents}¢/like", color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        PlanChip(label = plan.typeLabel, color = when (plan.planType) {
                            "SOLO" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            "PAREJA" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        })
                    }
                }

                if (isActive) {
                    Text("✓ Activo", style = MaterialTheme.typography.labelMedium, color = LikeGreen, fontWeight = FontWeight.Bold)
                } else {
                    Text("✗ Agotado", style = MaterialTheme.typography.labelMedium, color = NopeRed, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress
            Column(
                modifier = Modifier.fillMaxWidth(),
                crossAlignment = CrossAxisAlignment.Start
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gastado ${(plan.spentCents/100).toString().replace(".", ",")}€ de ${(plan.budgetCents/100).toString().replace(".", ",")}€", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${plan.likesRemaining} likes restantes", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (plan.likesRemaining > 10) LikeGreen else NopeRed)
                }
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                ) {
                    val bgColor = MaterialTheme.colorScheme.surfaceVariant
                    val fillColor = progressColor
                    drawRect(bgColor, RoundedCornerShape(4.dp).createOutline(this.size, this.layoutDirection))
                    drawRect(fillColor, RoundedCornerShape(4.dp).createOutline(this.size.copy(width = this.size.width * pct / 100f), this.layoutDirection))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver en app")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NopeRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
fun PlanChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

val SponsoredPlanResponse.typeLabel: String
    get() = when (planType) {
        "SOLO" -> "🧑 Solo"
        "PAREJA" -> "💑 Pareja"
        else -> "👥 Ambos"
    }

// ===== DIALOGS =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopUpDialog(
    amount: String,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("💰 Recargar Saldo", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column {
                Text("Añade saldo para crear planes patrocinados.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text("Cantidad (céntimos)") },
                    placeholder = { Text("1000 = 10€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !loading
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Mínimo 100¢ (1€) · Máximo 100.000¢ (1.000€)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!loading) onConfirm() },
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Recargar")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!loading) onDismiss() }) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    emoji: String,
    onEmojiChange: (String) -> Unit,
    desc: String,
    onDescChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    price: String,
    onPriceChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    duration: String,
    onDurationChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    budget: String,
    onBudgetChange: (String) -> Unit,
    cpl: String,
    onCplChange: (String) -> Unit,
    error: String?,
    loading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Since we can't easily scroll AlertDialog, we use a custom dialog-like surface
    val context = LocalContext.current
    // Using a Material3 Dialog with vertical scroll
    androidx.compose.material3.Dialog(
        onDismissRequest = { if (!loading) onDismiss() },
        properties = androidx.compose.material3.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(90.dp.min(380.dp))
                .height(720.dp.also { /* capped */ }),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("➕ Nuevo Plan Patrocinado", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Los usuarios lo verán mezclado con planes gratuitos. Pagas por like.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))

                // Form fields
                OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Título *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = !loading)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = emoji, onValueChange = onEmojiChange, label = { Text("Emoji") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !loading)
                    OutlinedTextField(value = price, onValueChange = onPriceChange, label = { Text("Precio") }, placeholder = { Text("0€") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !loading)
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = desc, onValueChange = onDescChange,
                    label = { Text("Descripción *") },
                    modifier = Modifier.fillMaxWidth().minHeight(100.dp),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    shape = RoundedCornerShape(12.dp), enabled = !loading
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = location, onValueChange = onLocationChange, label = { Text("Ubicación *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = !loading)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = onCityChange,
                        label = { Text("Ciudad *") },
                        placeholder = { Text("BARCELONA o VILLENA") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !loading
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = onTypeChange,
                        label = { Text("Tipo *") },
                        placeholder = { Text("AMBOS, SOLO, PAREJA") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !loading
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = duration, onValueChange = onDurationChange, label = { Text("Duración") }, placeholder = { Text("2h") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !loading)
                    OutlinedTextField(value = category, onValueChange = onCategoryChange, label = { Text("Categoría") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !loading)
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = tags, onValueChange = onTagsChange, label = { Text("Tags (coma separada) *") }, placeholder = { Text("comida, romantico, vistas") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = !loading)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = budget, onValueChange = onBudgetChange, label = { Text("Presupuesto (¢) *") }, placeholder = { Text("500") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !loading)
                    OutlinedTextField(value = cpl, onValueChange = onCplChange, label = { Text("Coste/like (¢) *") }, placeholder = { Text("10") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = !loading)
                }

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (!loading) onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancelar") }
                    Button(
                        onClick = { if (!loading) onConfirm() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !loading
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Crear Plan")
                    }
                }
            }
        }
    }
}