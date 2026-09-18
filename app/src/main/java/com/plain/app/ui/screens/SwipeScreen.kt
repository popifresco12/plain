package com.plain.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.plain.app.R

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.plain.app.data.ApiClient
import com.plain.app.data.Analytics
import com.plain.app.data.SwipeHistory
import com.plain.app.data.CityPreferences
import com.plain.app.data.LocationHelper
import android.content.Context
import com.plain.app.data.PlanResponse
import com.plain.app.data.TripGroupCreateRequest
import com.plain.app.data.TripGroupResponse
import com.plain.app.ui.components.PlanCardFromResponse
import com.plain.app.ui.components.addToCalendar
import com.plain.app.ui.components.sharePlan
import com.plain.app.ui.theme.LikeGreen
import com.plain.app.ui.theme.NopeRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    city: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
    onCreatePlan: () -> Unit,
    onOpenChat: (Int, String) -> Unit = { _, _ -> },
    onSwitchCity: (String) -> Unit = {},
    planCreated: Boolean = false
) {
    var plans by remember { mutableStateOf<List<PlanResponse>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val reportScope = androidx.compose.runtime.rememberCoroutineScope()
    var isAnimating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf<PlanResponse?>(null) }
    var webhookAvailable by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Estado de grupos: vive al nivel del SwipeScreen (no del diálogo) para
    // evitar "coroutine scope left the composition" al cerrar el diálogo
    var groups by remember { mutableStateOf<List<TripGroupResponse>>(emptyList()) }
    // Mensajes sin leer por quedada (contador de avisos)
    var unread by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var groupsLoading by remember { mutableStateOf(false) }
    var groupMsg by remember { mutableStateOf<String?>(null) }
    var currentGroupsPlan by remember { mutableStateOf<Int?>(null) }

    // Contexto y prefs van ANTES de loadGroups: esa función los usa para saber
    // qué mensajes de cada quedada ya se han visto.
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("plain_swipes", Context.MODE_PRIVATE) }

    fun loadGroups(planId: Int) {
        groupsLoading = true
        groupMsg = null
        scope.launch {
            try {
                val resp = ApiClient.service.getPlanGroups(planId)
                if (resp.isSuccessful) {
                    groups = resp.body() ?: emptyList()
                    // Avisos: cuenta los mensajes posteriores a la última visita de
                    // cada quedada (sin servidor de push, así que se calcula al listar)
                    val nuevos = mutableMapOf<Int, Int>()
                    for (g in groups) {
                        try {
                            val msgs = ApiClient.service.getGroupMessages(g.id)
                            val lista = msgs.body() ?: emptyList()
                            val visto = CityPreferences.getChatSeen(context, g.id)
                            val pendientes = lista.count { m ->
                                val t = parseIsoMillis(m.createdAt)
                                t != null && t > visto
                            }
                            if (pendientes > 0) nuevos[g.id] = pendientes
                        } catch (_: Exception) { /* si un grupo falla, seguimos con el resto */ }
                    }
                    unread = nuevos
                }
            } catch (_: Exception) {}
            groupsLoading = false
        }
    }

    // Cargar grupos cuando se abre el diálogo de info de un plan
    LaunchedEffect(showInfoDialog?.id) {
        val pid = showInfoDialog?.id
        if (pid != null) {
            currentGroupsPlan = pid
            groups = emptyList()
            loadGroups(pid)
        }
    }

    // Radio de búsqueda en km (0 = solo esta ciudad). Permite ver planes de
    // ciudades cercanas sin tener que cambiar de ciudad. Persistente.
    var radiusKm by remember { mutableIntStateOf(CityPreferences.getRadiusKm(context)) }

    // Filtros del swipe: el backend ya acepta plan_type y category, y con el
    // radio activo se acumulan planes de varias ciudades, así que hacen falta.
    var filterType by remember { mutableStateOf(CityPreferences.getFilterType(context)) }
    var filterCategory by remember { mutableStateOf(CityPreferences.getFilterCategory(context)) }
    var filterFree by remember { mutableStateOf(CityPreferences.getFilterFree(context)) }
    var showFilters by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // Ciudad detectada por GPS: si no es la elegida, ofrecemos cambiarla
    var gpsCity by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // La lista completa se guarda aparte para poder buscar sin recargar del servidor
    var allPlans by remember { mutableStateOf<List<PlanResponse>>(emptyList()) }
    val filterCount = (if (filterType != null) 1 else 0) +
        (if (filterCategory != null) 1 else 0) + (if (filterFree) 1 else 0)

    fun markSwiped(planId: Int) {
        val seen = prefs.getStringSet("seen_plan_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        seen.add(planId.toString())
        prefs.edit().putStringSet("seen_plan_ids", seen).apply()
    }

    // Recarga de planes al cambiar de ciudad o al pedir refresco.
    // IMPORTANTE: planCreated NO está entre las claves; se consume en un efecto
    // aparte. Si incrementásemos refreshKey dentro de este mismo efecto, Compose
    // cancelaría el efecto a media petición y el catch mostraría
    // "The coroutine scope left the composition" como si fuera un error real.
    LaunchedEffect(city, refreshKey, radiusKm, filterType, filterCategory, filterFree) {
        try {
            var resp = ApiClient.service.getPlans(city = city, radiusKm = radiusKm, planType = filterType, category = filterCategory, onlyAvailable = true)

            // Si la ciudad no tiene planes (ciudad nueva), generar planes locales
            // automáticamente (backend idempotente: no duplica si ya existen)
            if (resp.isSuccessful && (resp.body()?.isEmpty() == true)) {
                error = "Ciudad nueva: generando planes de ${city.lowercase().replaceFirstChar { it.uppercase() }}…"
                try {
                    val boot = ApiClient.service.bootstrapCity(city)
                    val created = boot.body()?.created ?: 0
                    if (created > 0) {
                        feedbackText = "✨ ¡Hemos creado $created planes en ${city.lowercase().replaceFirstChar { it.uppercase() }}!"
                    }
                    resp = ApiClient.service.getPlans(city = city, radiusKm = radiusKm, planType = filterType, category = filterCategory, onlyAvailable = true)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Si falla el bootstrap, seguimos con la lista vacía
                }
            }

            if (resp.isSuccessful) {
                // Filtrar los que ya se swiparon en esta ciudad (persistente)
                val seen = prefs.getStringSet("seen_plan_ids", mutableSetOf()) ?: mutableSetOf()
                var loaded = resp.body()?.filter { it.id.toString() !in seen } ?: emptyList()
                if (filterFree) loaded = loaded.filter { it.price.trim().startsWith("0") }
                val fresh = loaded.shuffled()
                allPlans = fresh
                plans = filterPlans(fresh, query)
                error = if (fresh.isEmpty()) {
                    "Ya has visto todos los planes de esta ciudad. ¡Vuelve mañana para más!"
                } else null
            } else {
                error = "Error al cargar planes (${resp.code()})"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // cancelación normal (recomposición/navegación): NO es un error
        } catch (e: Exception) {
            error = "Error de conexión: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    // GPS: solo consulta si hay permiso (si no, no molestamos con nada)
    LaunchedEffect(city) {
        gpsCity = withContext(Dispatchers.IO) {
            if (LocationHelper.hasLocationPermission(context)) LocationHelper.detectCity(context) else null
        }
    }

    // Búsqueda: filtra en memoria (no vuelve a pedir al servidor) y reinicia el mazo
    LaunchedEffect(query) {
        plans = filterPlans(allPlans, query)
        currentIndex = 0
    }

    // Al volver de crear un plan, refrescar la lista (efecto propio para no
    // cancelar la carga en curso)
    LaunchedEffect(planCreated) {
        if (planCreated) refreshKey++
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

    if (showFilters) {
        FilterDialog(
            type = filterType,
            category = filterCategory,
            freeOnly = filterFree,
            onApply = { t, c, f ->
                filterType = t
                filterCategory = c
                filterFree = f
                CityPreferences.setFilters(context, t, c, f)
                showFilters = false
            },
            onDismiss = { showFilters = false }
        )
    }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (city) {
                                "BARCELONA" -> "🌊 Barcelona"
                                "ALICANTE" -> "🌴 Alicante"
                                "MADRID" -> "🐻 Madrid"
                                "VALENCIA" -> "🥘 Valencia"
                                "SEVILLA" -> "💃 Sevilla"
                                else -> "🏰 ${city.lowercase().replaceFirstChar { it.uppercase() }}"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        if (plans.isNotEmpty()) {
                            Text(
                                text = "${plans.size} plan${if (plans.size == 1) "" else "es"} por descubrir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onCreatePlan) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_create_plan), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onFavorites) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.cd_favorites), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) query = ""
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search),
                            tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.cd_filters),
                            tint = if (filterCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
            RadiusSelector(
                radiusKm = radiusKm,
                plans = plans,
                onChange = { km ->
                    radiusKm = km
                    CityPreferences.setRadiusKm(context, km)
                }
            )
            val detected = gpsCity
            if (detected != null && !detected.equals(city, ignoreCase = true)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            "📍 Estás cerca de ${detected.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onSwitchCity(detected) }) {
                            Text(stringResource(R.string.swipe_view_plans))
                        }
                    }
                }
            }
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.swipe_search)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    shape = MaterialTheme.shapes.small
                )
                if (query.isNotBlank()) {
                    Text(
                        text = if (plans.isEmpty()) "Sin resultados para «$query»"
                        else "${plans.size} resultado${if (plans.size == 1) "" else "s"} para «$query»",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    )
                }
            }
            }
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
                    // Botón NO con feedback de pulsación
                    val nopeInteraction = remember { MutableInteractionSource() }
                    val nopePressed by nopeInteraction.collectIsPressedAsState()
                    val nopeScale by animateFloatAsState(
                        targetValue = if (nopePressed) 0.86f else 1f,
                        animationSpec = tween(120, easing = FastOutSlowInEasing),
                        label = "nopeScale"
                    )
                    FilledIconButton(
                        onClick = {
                            if (topCard != null && !isAnimating) {
                                offsetX = -500f
                                isAnimating = true
                            }
                        },
                        interactionSource = nopeInteraction,
                        modifier = Modifier.size(70.dp).graphicsLayer {
                            scaleX = nopeScale
                            scaleY = nopeScale
                            shadowElevation = 10f
                            shape = RoundedCornerShape(35.dp)
                            clip = false
                        },
                        shape = RoundedCornerShape(35.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NopeRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "No", modifier = Modifier.size(32.dp))
                    }

                    // Botón ME GUSTA con feedback de pulsación
                    val likeInteraction = remember { MutableInteractionSource() }
                    val likePressed by likeInteraction.collectIsPressedAsState()
                    val likeScale by animateFloatAsState(
                        targetValue = if (likePressed) 0.86f else 1f,
                        animationSpec = tween(120, easing = FastOutSlowInEasing),
                        label = "likeScale"
                    )
                    FilledIconButton(
                        onClick = {
                            if (topCard != null && !isAnimating) {
                                offsetX = 500f
                                isAnimating = true
                            }
                        },
                        interactionSource = likeInteraction,
                        modifier = Modifier.size(78.dp).graphicsLayer {
                            scaleX = likeScale
                            scaleY = likeScale
                            shadowElevation = 12f
                            shape = RoundedCornerShape(39.dp)
                            clip = false
                        },
                        shape = RoundedCornerShape(39.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = LikeGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_like), modifier = Modifier.size(36.dp))
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                loading = true
                                error = null
                                refreshKey++
                            }) {
                                Text(stringResource(R.string.common_retry))
                            }
                            OutlinedButton(onClick = { onSettings() }) {
                                Text(stringResource(R.string.common_change_city))
                            }
                        }
                    }
                }
                topCard == null -> {
                    EmptySwipeState(
                        onReset = {
                            scope.launch {
                                loading = true
                                try {
                                    val resp = ApiClient.service.getPlans(city = city, radiusKm = radiusKm, planType = filterType, category = filterCategory, onlyAvailable = true)
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

                        // Vuelo de salida: tween rápido con easing (más "snappy"
                        // que el spring por defecto, que flotaba demasiado)
                        val resultOffsetX by animateFloatAsState(
                            targetValue = if (isAnimating && offsetX > 0) 2000f
                            else if (isAnimating && offsetX < 0) -2000f
                            else offsetX,
                            animationSpec = tween(
                                durationMillis = if (isAnimating) 260 else 180,
                                easing = FastOutSlowInEasing
                            ),
                            label = "swipe"
                        )

                        LaunchedEffect(resultOffsetX) {
                            if (isAnimating && kotlin.math.abs(resultOffsetX) >= 1500f) {
                                val currentPlan = topCard

                                if (offsetX > 0 && currentPlan != null) {
                                    // Analítica e historial: antes no quedaba rastro de lo visto
                                    Analytics.track("like", currentPlan.id, currentPlan.city)
                                    SwipeHistory.add(currentPlan, liked = true)
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
                                    Analytics.track("dislike", currentPlan.id, currentPlan.city)
                                    SwipeHistory.add(currentPlan, liked = false)
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

                                // Eliminar el plan swipado de la lista (no vuelve a salir
                                // aunque recargues la pantalla en esta sesión)
                                if (currentPlan != null) {
                                    markSwiped(currentPlan.id)
                                    plans = plans.filter { it.id != currentPlan.id }
                                    currentIndex = 0
                                }
                                offsetX = 0f
                                isAnimating = false
                            }
                        }

                        // Entrada de la nueva tarjeta: aparece creciendo suavemente
                        // (da sensación de fluidez al pasar de un plan a otro)
                        val cardEntrance = remember { Animatable(1f) }
                        LaunchedEffect(topCard?.id) {
                            if (topCard != null) {
                                cardEntrance.snapTo(0.9f)
                                cardEntrance.animateTo(
                                    1f,
                                    tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                )
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
                                            markSwiped(plan.id)
                                            plans = plans.filter { it.id != plan.id }
                                            currentIndex = 0
                                            offsetX = 0f
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            webhookAvailable = webhookAvailable,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .graphicsLayer {
                                    scaleX = cardEntrance.value
                                    scaleY = cardEntrance.value
                                    alpha = 0.55f + cardEntrance.value * 0.45f
                                }
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
                    Spacer(Modifier.height(10.dp))
                    // Reportar: hasta ahora no había forma de avisar de un plan malo
                    TextButton(onClick = {
                        val idParaReportar = plan.id
                        showInfoDialog = null
                        reportScope.launch {
                            try {
                                ApiClient.service.reportPlan(
                                    idParaReportar,
                                    com.plain.app.data.PlanReportRequest(reason = "inapropiado")
                                )
                                feedbackText = "Gracias, revisaremos este plan"
                            } catch (_: Exception) {}
                        }
                    }) {
                        Text(stringResource(R.string.swipe_report), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Para: ${plan.planType.lowercase().replaceFirstChar { it.uppercase() }}", color = MaterialTheme.colorScheme.primary)
                    if (plan.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Etiquetas: ${plan.tags.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(20.dp))

                    // --- Trip groups (BlaBlaCar-style) ---
                    Text(stringResource(R.string.swipe_meetups_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    if (groupsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else if (groups.isEmpty()) {
                        Text(stringResource(R.string.swipe_no_meetups), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        groups.forEach { g ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(g.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${g.ownerUsername} · ${g.transport.lowercase()} · ${g.seatsTaken}/${g.seats} plazas" +
                                                (g.meetingPoint?.let { " · 📍 $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // Chat de la quedada (con aviso de mensajes nuevos)
                                    Box {
                                        IconButton(
                                            onClick = {
                                                CityPreferences.setChatSeen(context, g.id, System.currentTimeMillis())
                                                unread = unread - g.id
                                                onOpenChat(g.id, g.title)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("💬", fontSize = 16.sp)
                                        }
                                        val pend = unread[g.id] ?: 0
                                        if (pend > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(9.dp),
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    if (pend > 9) "9+" else "$pend",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (g.seatsTaken < g.seats) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                try {
                                                    val resp = ApiClient.service.joinGroup(g.id)
                                                    groupMsg = if (resp.isSuccessful) "✅ Te has unido a \"${g.title}\"" else "Error al unirse (${resp.code()})"
                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                    throw e
                                                } catch (_: Exception) {
                                                    groupMsg = "Error de conexión al unirse"
                                                }
                                                loadGroups(plan.id)
                                            }
                                        }) {
                                            Text(stringResource(R.string.swipe_join), color = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        Text(stringResource(R.string.swipe_complete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    groupMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val resp = ApiClient.service.createGroup(
                                        plan.id,
                                        TripGroupCreateRequest(
                                            planId = plan.id,
                                            title = "Quedada para ${plan.title} 🚗",
                                            meetingPoint = plan.location,
                                            seats = 4,
                                            transport = "COCHE",
                                            notes = "¿Vamos juntos? Crea la quedada y compártela."
                                        )
                                    )
                                    groupMsg = if (resp.isSuccessful) "✅ Quedada creada" else "Error al crear (${resp.code()})"
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    groupMsg = "Error de conexión al crear la quedada"
                                }
                                loadGroups(plan.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.swipe_create), style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.height(16.dp))

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
                            Text(stringResource(R.string.swipe_calendar), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { sharePlan(context, plan) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.swipe_share), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = null }) { Text(stringResource(R.string.common_close)) } }
        )
    }
}

@Composable
private fun EmptySwipeState(onReset: () -> Unit) {
    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.swipe_empty), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.swipe_empty_hint), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.swipe_restart))
        }
    }
}

/**
 * Selector de radio: 0 = solo la ciudad elegida; el resto amplía la búsqueda a
 * ciudades cercanas (el backend calcula la distancia con las coordenadas del
 * índice de ciudades). Evita tener que cambiar de ciudad a mano para ver qué
 * hay alrededor.
 */
@Composable
private fun RadiusSelector(
    radiusKm: Int,
    plans: List<PlanResponse>,
    onChange: (Int) -> Unit
) {
    val options = listOf(0 to "Esta ciudad", 25 to "25 km", 50 to "50 km", 100 to "100 km", 250 to "250 km")
    val nearby = plans.count { (it.distanceKm ?: 0.0) > 0.0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (km, label) ->
                FilterChip(
                    selected = radiusKm == km,
                    onClick = { onChange(km) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
        if (radiusKm > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (nearby > 0)
                    "🌍 $nearby plan${if (nearby == 1) "" else "es"} de ciudades a menos de $radiusKm km"
                else
                    "Buscando también en ciudades a menos de $radiusKm km",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Filtros del swipe: con quién, categoría y solo gratis.
 * Tipo y categoría los filtra el servidor (plan_type / category); «solo gratis»
 * se filtra en el cliente porque el precio es texto libre ("0€", "5-10€").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(
    type: String?,
    category: String?,
    freeOnly: Boolean,
    onApply: (String?, String?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var t by remember { mutableStateOf(type) }
    var c by remember { mutableStateOf(category) }
    var f by remember { mutableStateOf(freeOnly) }

    val types = listOf(null to "Todos", "SOLO" to "Solo", "PAREJA" to "Pareja", "AMBOS" to "Amigos")
    val categories = listOf(
        null, "Cultura", "Naturaleza", "Gastronomía", "Compras", "Ocio", "Deporte", "Música"
    )
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swipe_filters), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        stringResource(R.string.swipe_who),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        types.forEach { (value, label) ->
                            FilterChip(
                                selected = t == value,
                                onClick = { t = value },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = chipColors
                            )
                        }
                    }
                }
                Column {
                    Text(
                        stringResource(R.string.swipe_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { value ->
                            FilterChip(
                                selected = c == value,
                                onClick = { c = value },
                                label = {
                                    Text(value ?: "Todas", style = MaterialTheme.typography.labelMedium)
                                },
                                colors = chipColors
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.swipe_free_only), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.swipe_free_plans),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = f, onCheckedChange = { f = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(t, c, f) }) { Text(stringResource(R.string.common_apply)) } },
        dismissButton = {
            TextButton(onClick = { t = null; c = null; f = false }) { Text(stringResource(R.string.common_clear)) }
        }
    )
}

/**
 * Búsqueda en memoria sobre lo ya cargado: título, descripción, lugar, ciudad y
 * etiquetas. Se hace en el cliente para que filtrar mientras escribes sea
 * instantáneo (el backend no tiene endpoint de búsqueda).
 */
private fun filterPlans(source: List<PlanResponse>, query: String): List<PlanResponse> {
    val q = query.trim()
    if (q.length < 2) return source
    return source.filter { plan ->
        plan.title.contains(q, ignoreCase = true) ||
            plan.description.contains(q, ignoreCase = true) ||
            plan.location.contains(q, ignoreCase = true) ||
            plan.city.contains(q, ignoreCase = true) ||
            plan.category.contains(q, ignoreCase = true) ||
            plan.tags.any { it.contains(q, ignoreCase = true) }
    }
}

/** Convierte el created_at del backend (ISO con o sin zona) a epoch millis. */
private fun parseIsoMillis(raw: String): Long? {
    return try {
        java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.LocalDateTime.parse(raw)
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
