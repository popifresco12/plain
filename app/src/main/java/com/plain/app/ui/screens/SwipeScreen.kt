package com.plain.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.plain.app.data.ApiClient
import com.plain.app.data.PlanResponse
import com.plain.app.ui.components.PlanCardFromResponse
import com.plain.app.ui.components.addToCalendar
import com.plain.app.ui.components.sharePlan
import com.plain.app.ui.theme.LikeGreen
import com.plain.app.ui.theme.NopeRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    city: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
    onCreatePlan: () -> Unit
) {
    var plans by remember { mutableStateOf<List<PlanResponse>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf<PlanResponse?>(null) }
    var webhookAvailable by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Load plans from API
    LaunchedEffect(city) {
        try {
            val resp = ApiClient.service.getPlans(city = city, onlyAvailable = true)
            if (resp.isSuccessful) {
                plans = resp.body()?.shuffled() ?: emptyList()
            } else {
                error = "Error al cargar planes (${resp.code()})"
            }
        } catch (e: Exception) {
            error = "Error de conexión: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    // Check if webhook is configured
    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.service.getWebhook()
            webhookAvailable = resp.isSuccessful && resp.body()?.url?.isNotBlank() == true
        } catch (_: Exception) {}
    }

    val topCard = plans.getOrNull(currentIndex)
    val nextCard = plans.getOrNull(currentIndex + 1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (city) {
                            "BARCELONA" -> "🌊 Barcelona"
                            "ALICANTE" -> "🌴 Alicante"
                            else -> "🏰 Villena"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onCreatePlan) {
                        Icon(Icons.Default.Add, contentDescription = "Crear plan", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onFavorites) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favoritos", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (topCard != null && !isAnimating) {
                                offsetX = -500f
                                isAnimating = true
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NopeRed.copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "No", tint = NopeRed, modifier = Modifier.size(28.dp))
                    }

                    FilledTonalButton(
                        onClick = {
                            if (topCard != null && !isAnimating) {
                                offsetX = 500f
                                isAnimating = true
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(36.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LikeGreen.copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Me gusta", tint = LikeGreen, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Shimmer-like loading cards
                        repeat(3) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .height(320.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f + it * 0.1f)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    RoundedCornerShape(30.dp)
                                                )
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .height(24.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .height(16.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.5f)
                                                .height(16.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error!!, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onBack() }) {
                            Text("Volver")
                        }
                    }
                }
                topCard == null -> {
                    EmptySwipeState(
                        onReset = {
                            scope.launch {
                                loading = true
                                try {
                                    val resp = ApiClient.service.getPlans(city = city, onlyAvailable = true)
                                    if (resp.isSuccessful) {
                                        plans = resp.body()?.shuffled() ?: emptyList()
                                        currentIndex = 0
                                        offsetX = 0f
                                        isAnimating = false
                                    }
                                } catch (_: Exception) {}
                                loading = false
                            }
                        }
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (nextCard != null) {
                            PlanCardFromResponse(
                                plan = nextCard,
                                offsetX = 0f,
                                onSwipeLeft = {},
                                onSwipeRight = {},
                                onMoreInfo = { showInfoDialog = it },
                                onSendToAgent = {},
                                webhookAvailable = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .scale(0.95f)
                            )
                        }

                        val resultOffsetX by animateFloatAsState(
                            targetValue = if (isAnimating && offsetX > 0) 2000f
                            else if (isAnimating && offsetX < 0) -2000f
                            else offsetX,
                            label = "swipe"
                        )

                        LaunchedEffect(resultOffsetX) {
                            if (isAnimating && kotlin.math.abs(resultOffsetX) >= 1500f) {
                                val currentPlan = topCard

                                if (offsetX > 0 && currentPlan != null) {
                                    // SWIPE RIGHT — Save to favorites
                                    scope.launch {
                                        try {
                                            ApiClient.service.addFavorite(currentPlan.id)
                                        } catch (_: Exception) {}
                                    }
                                    // Trigger webhook if configured
                                    if (webhookAvailable) {
                                        scope.launch {
                                            try {
                                                ApiClient.service.triggerWebhook(currentPlan.id)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                } else if (offsetX < 0 && currentPlan != null) {
                                    // SWIPE LEFT — Record disliked tags
                                    if (currentPlan.tags.isNotEmpty()) {
                                        scope.launch {
                                            try {
                                                val resp = ApiClient.service.dislikeTags(
                                                    com.plain.app.data.DislikeTagsRequest(currentPlan.tags)
                                                )
                                                if (resp.isSuccessful) {
                                                    feedbackText = "Se mostrarán menos planes similares"
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }

                                currentIndex++
                                offsetX = 0f
                                isAnimating = false
                            }
                        }

                        PlanCardFromResponse(
                            plan = topCard,
                            offsetX = resultOffsetX,
                            onSwipeLeft = {
                                if (!isAnimating) { offsetX = -500f; isAnimating = true }
                            },
                            onSwipeRight = {
                                if (!isAnimating) { offsetX = 500f; isAnimating = true }
                            },
                            onMoreInfo = { showInfoDialog = it },
                            onSendToAgent = { plan ->
                                scope.launch {
                                    try {
                                        val resp = ApiClient.service.triggerWebhook(plan.id)
                                        if (resp.isSuccessful) {
                                            // Also save as favorite
                                            try { ApiClient.service.addFavorite(plan.id) } catch (_: Exception) {}
                                            currentIndex++
                                            offsetX = 0f
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            webhookAvailable = webhookAvailable,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (!isAnimating) offsetX += dragAmount.x
                                        },
                                        onDragEnd = {
                                            if (!isAnimating) {
                                                if (kotlin.math.abs(offsetX) > 250) isAnimating = true
                                                else offsetX = 0f
                                            }
                                        }
                                    )
                                }
                        )

                        // Feedback toast
                        feedbackText?.let { msg ->
                            LaunchedEffect(msg) {
                                kotlinx.coroutines.delay(2500)
                                feedbackText = null
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = msg,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Text(
                            text = "${currentIndex + 1} / ${plans.size}",
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    // Info dialog
    showInfoDialog?.let { plan ->
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text("${plan.emoji} ${plan.title}", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column {
                    Text(plan.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column { Text("📍 ${plan.location}") }
                        Column { Text("💰 ${plan.price}") }
                        Column { Text("⏱ ${plan.duration}") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Para: ${plan.planType.lowercase().replaceFirstChar { it.uppercase() }}", color = MaterialTheme.colorScheme.primary)
                    if (plan.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Etiquetas: ${plan.tags.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(20.dp))

                    // Action buttons
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { addToCalendar(context, plan) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Calendario", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { sharePlan(context, plan) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun EmptySwipeState(onReset: () -> Unit) {
    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("¡Ya has visto todos los planes!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Vuelve a empezar o elige otra ciudad.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Empezar de nuevo")
        }
    }
}
