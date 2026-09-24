package com.plain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.MarkReadRequest
import com.plain.app.data.NotificationItem
import com.plain.app.data.RealtimeClient
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Bandeja de avisos: quién se une, a quién aceptan, mensajes y match. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenChat: (Int, String) -> Unit,
    onOpenMatches: () -> Unit
) {
    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var markAll by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf<NotificationItem?>(null) }

    LaunchedEffect(reloadKey) {
        try {
            val r = ApiClient.service.getNotifications(limit = 50)
            if (r.isSuccessful) {
                val body = r.body()
                items = body?.items ?: emptyList()
                RealtimeClient.setUnread(body?.unread ?: 0)
                error = null
            } else {
                error = "No se pudieron cargar los avisos (${r.code()})"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Sin conexión. Desliza hacia atrás y vuelve a entrar."
        } finally {
            loading = false
        }
    }

    // Llega uno nuevo por WebSocket con la bandeja abierta → recargar
    LaunchedEffect(Unit) {
        RealtimeClient.events.collect { ev ->
            if (ev.get("type")?.asString == "notification") reloadKey++
        }
    }

    LaunchedEffect(markAll) {
        if (markAll == 0) return@LaunchedEffect
        try {
            val r = ApiClient.service.markNotificationsRead(MarkReadRequest(all = true))
            if (r.isSuccessful) {
                items = items.map { it.copy(read = true) }
                RealtimeClient.setUnread(0)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // Abrir un aviso: marcarlo leído y navegar a donde corresponda (lo último)
    LaunchedEffect(opening) {
        val n = opening ?: return@LaunchedEffect
        try {
            if (!n.read) {
                ApiClient.service.markNotificationsRead(MarkReadRequest(ids = listOf(n.id)))
                    .body()?.let { RealtimeClient.setUnread(it.unread) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        val gid = n.intData("group_id")
        val title = n.strData("group_title") ?: "Quedada"
        opening = null
        when {
            // Con acceso al chat: mensajes, alguien se une/pide unirse (soy el creador), me aceptan
            gid != null && n.kind in setOf("message", "join", "request", "approved") -> onOpenChat(gid, title)
            else -> onOpenMatches()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔔 Avisos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                actions = {
                    if (items.any { !it.read }) {
                        IconButton(onClick = { markAll++ }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Marcar todo como leído")
                        }
                    }
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
                    "🔕",
                    "Aún no tienes avisos.\nTe avisaremos cuando alguien se una a tus quedadas, te escriban o coincidáis en un plan.",
                    Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(items, key = { it.id }) { n ->
                        NotificationRow(n) { opening = n }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, onClick: () -> Unit) {
    val emoji = when (n.kind) {
        "message" -> "💬"
        "join" -> "👋"
        "request" -> "🙋"
        "approved" -> "✅"
        "rejected" -> "🚫"
        "match" -> "✨"
        "group_new" -> "🚗"
        else -> "🔔"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (n.read) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(emoji, fontSize = 26.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                n.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (n.body.isNotBlank()) {
                Text(
                    n.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                relativeTime(n.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (!n.read) {
            Box(
                Modifier
                    .padding(start = 8.dp, top = 6.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
internal fun EmptyNotice(emoji: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** «hace 5 min», «hace 2 h», «hace 3 d» (el servidor guarda UTC sin zona). */
internal fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val t = LocalDateTime.parse(iso.take(19)).toInstant(ZoneOffset.UTC)
        val mins = Duration.between(t, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            mins < 1 -> "ahora"
            mins < 60 -> "hace $mins min"
            mins < 60 * 24 -> "hace ${mins / 60} h"
            else -> "hace ${mins / (60 * 24)} d"
        }
    } catch (_: Exception) {
        ""
    }
}
