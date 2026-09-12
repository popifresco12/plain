package com.plain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.GroupMessageRequest
import com.plain.app.data.GroupMessageResponse
import kotlinx.coroutines.delay

/**
 * Chat de una quedada grupal: los miembros hablan para coordinar el plan.
 * Refresca automáticamente cada 5s para ver mensajes nuevos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: Int,
    groupTitle: String,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf<List<GroupMessageResponse>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var myUserId by remember { mutableStateOf<Int?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Saber quién soy, para pintar mis mensajes a la derecha
    LaunchedEffect(groupId) {
        try {
            val me = ApiClient.service.getMe()
            if (me.isSuccessful) myUserId = me.body()?.id
        } catch (_: Exception) {}
    }

    // Enviar mensaje desde un LaunchedEffect (patrón seguro: nunca escribe
    // estado si la composición sale)
    LaunchedEffect(pendingText) {
        val text = pendingText ?: return@LaunchedEffect
        try {
            val resp = ApiClient.service.sendGroupMessage(groupId, GroupMessageRequest(text))
            if (!resp.isSuccessful) {
                error = "No se pudo enviar (${resp.code()})"
            }
        } catch (_: Exception) {
            error = "No se pudo enviar: sin conexión"
        }
        pendingText = null
    }

    // Cargar mensajes + refresco automático cada 5s
    LaunchedEffect(groupId) {
        while (true) {
            try {
                val resp = ApiClient.service.getGroupMessages(groupId)
                if (resp.isSuccessful) {
                    val newMsgs = resp.body() ?: emptyList()
                    if (newMsgs.size != messages.size) {
                        messages = newMsgs
                    }
                    error = null
                } else if (resp.code() == 403) {
                    error = "Únete a la quedada para ver el chat"
                } else if (messages.isEmpty()) {
                    error = "Error al cargar (${resp.code()})"
                }
            } catch (_: Exception) {
                if (messages.isEmpty()) error = "Sin conexión. Reintentando…"
            }
            loading = false
            delay(5000)
        }
    }

    // Auto-scroll al último mensaje cuando llega uno nuevo
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("💬 $groupTitle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${messages.size} mensaje${if (messages.size == 1) "" else "s"}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= 500) input = it },
                        placeholder = { Text("Escribe un mensaje…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isEmpty() || sending) return@FilledIconButton
                            input = ""
                            pendingText = text
                        },
                        enabled = input.isNotBlank() && !sending
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null && messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("💬", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(error ?: "", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👋", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Aún no hay mensajes", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sé el primero en escribir para coordinar la quedada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { msg ->
                            MessageBubble(
                                message = msg,
                                isMine = myUserId != null && msg.userId == myUserId
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: GroupMessageResponse, isMine: Boolean) {
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isMine) {
                Text(
                    "@${message.username}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Hora del mensaje (si la fecha es parseable)
                    val hora = remember(message.createdAt) {
                        try {
                            val t = message.createdAt.substringAfter("T").take(5)
                            if (t.length == 5 && t[2] == ':') t else ""
                        } catch (_: Exception) { "" }
                    }
                    if (hora.isNotEmpty()) {
                        Text(
                            text = hora,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}
