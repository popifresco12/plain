package com.plain.app.ui.screens

import com.plain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.SwipeHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historial de planes descartados y guardados.
 *
 * Los ID ya se guardaban en el móvil para no repetir tarjetas, pero los datos no:
 * no había forma de volver a mirar lo que habías pasado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entradas by remember { mutableStateOf(SwipeHistory.list()) }
    var soloGuardados by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM · HH:mm", Locale("es", "ES")) }

    val visibles = remember(entradas, soloGuardados) {
        if (soloGuardados) entradas.filter { it.liked } else entradas
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hist_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (entradas.isNotEmpty()) {
                        IconButton(onClick = {
                            SwipeHistory.clear()
                            entradas = emptyList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.hist_clear))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !soloGuardados,
                    onClick = { soloGuardados = false },
                    label = { Text("Todos (${entradas.size})") }
                )
                FilterChip(
                    selected = soloGuardados,
                    onClick = { soloGuardados = true },
                    label = { Text("Guardados (${entradas.count { it.liked }})") }
                )
            }

            if (visibles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.hist_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibles, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(e.emoji, fontSize = 26.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text(
                                        "${e.category} · ${e.city} · ${e.price}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        fmt.format(Date(e.at)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(if (e.liked) "❤️" else "✖️", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
