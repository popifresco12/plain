package com.plain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.MatchItem
import com.plain.app.data.TripGroupCreateRequest

private sealed interface MatchAction {
    data class Join(val groupId: Int, val title: String) : MatchAction
    data class Create(val planId: Int, val planTitle: String) : MatchAction
}

/**
 * «Tus match»: planes que te gustan y que también gustan a otras personas.
 * Desde aquí te unes a su quedada o creas una (y se les avisa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onBack: () -> Unit,
    onOpenChat: (Int, String) -> Unit
) {
    var items by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<MatchAction?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        try {
            val r = ApiClient.service.getMatches()
            if (r.isSuccessful) {
                items = r.body()?.items ?: emptyList()
                error = null
            } else {
                error = "No se pudieron cargar (${r.code()})"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Sin conexión"
        } finally {
            loading = false
        }
    }

    // Unirse / crear quedada: red en un efecto (nunca en el onClick) y navegar lo último
    LaunchedEffect(action) {
        val a = action ?: return@LaunchedEffect
        busy = true
        var goTo: Pair<Int, String>? = null
        try {
            when (a) {
                is MatchAction.Join -> {
                    val r = ApiClient.service.joinGroup(a.groupId)
                    val g = r.body()
                    when {
                        !r.isSuccessful -> {
                            // El servidor explica el motivo (completa, solo chicas/chicos…)
                            val detail = r.errorBody()?.string()
                                ?.let { Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                            snackbar.showSnackbar(detail ?: "No se pudo unir (${r.code()})")
                        }
                        g?.myStatus == "pending" -> {
                            snackbar.showSnackbar("🙋 Solicitud enviada: te avisaremos cuando te acepten")
                            reloadKey++
                        }
                        else -> goTo = a.groupId to a.title
                    }
                }
                is MatchAction.Create -> {
                    val r = ApiClient.service.createGroup(
                        a.planId, TripGroupCreateRequest(planId = a.planId, title = "Quedada: ${a.planTitle}".take(100))
                    )
                    val g = r.body()
                    if (r.isSuccessful && g != null) goTo = g.id to g.title
                    else snackbar.showSnackbar("No se pudo crear la quedada (${r.code()})")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            snackbar.showSnackbar("Sin conexión")
        }
        busy = false
        action = null
        goTo?.let { (id, title) -> onOpenChat(id, title) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("✨ Tus match", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && items.isEmpty() -> EmptyNotice("📡", error ?: "", Modifier.align(Alignment.Center))
                items.isEmpty() -> EmptyNotice(
                    "💫",
                    "Todavía no coincides con nadie.\nDa «me gusta» a planes: cuando a otra persona le guste el mismo, aparecerá aquí.",
                    Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.planId }) { m ->
                        MatchCard(
                            m = m,
                            enabled = !busy,
                            onJoin = { gid, title -> action = MatchAction.Join(gid, title) },
                            onOpen = { gid, title -> onOpenChat(gid, title) },
                            onCreate = { action = MatchAction.Create(m.planId, m.title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchCard(
    m: MatchItem,
    enabled: Boolean,
    onJoin: (Int, String) -> Unit,
    onOpen: (Int, String) -> Unit,
    onCreate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.emoji, fontSize = 30.sp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (m.city.isNotBlank()) {
                        Text(
                            m.city.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val nombres = m.people.take(3).joinToString(", ") { "@${it.username}" }
            val resto = m.peopleCount - m.people.take(3).size
            Text(
                buildString {
                    append("💞 ")
                    append(nombres)
                    if (resto > 0) append(" y $resto más")
                    append(if (m.peopleCount == 1) " también quiere hacer este plan" else " también quieren hacer este plan")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (m.groups.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                m.groups.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "👥 ${g.seatsTaken}/${g.seats}" + if (g.joinMode == "approval") " · con aprobación" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when {
                            g.iAmMember -> FilledTonalButton(onClick = { onOpen(g.id, g.title) }, enabled = enabled) { Text("Abrir chat") }
                            g.seatsTaken >= g.seats -> Text("Completa", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Button(onClick = { onJoin(g.id, g.title) }, enabled = enabled) {
                                Text(if (g.joinMode == "approval") "Pedir unirme" else "Unirme")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCreate, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (m.groups.isEmpty()) "🚗 Crear quedada y avisarles" else "🚗 Crear otra quedada")
            }
        }
    }
}
