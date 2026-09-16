package com.plain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.plain.app.data.ApiClient
import com.plain.app.data.ImageSearchResult
import kotlinx.coroutines.launch

/**
 * Elige la foto de un plan buscando en Openverse (Creative Commons, licencia
 * comercial, sin API key).
 *
 * NO se suben ficheros: el servidor de Render borra el disco en cada despliegue,
 * así que se guarda la URL de la imagen elegida. Se muestra autor y licencia
 * porque la atribución es obligatoria en estas licencias.
 */
@Composable
fun ImageSearchDialog(
    sugerencia: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var consulta by remember { mutableStateOf(sugerencia) }
    var resultados by remember { mutableStateOf<List<ImageSearchResult>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun buscar() {
        if (consulta.isBlank()) return
        buscando = true
        error = null
        scope.launch {
            try {
                val r = ApiClient.service.searchImages(consulta.trim())
                resultados = r.body() ?: emptyList()
                if (!r.isSuccessful) error = "El buscador respondió ${r.code()}"
                if (resultados.isEmpty()) error = "Sin resultados para «${consulta.trim()}»"
            } catch (e: Exception) {
                error = "Sin conexión: ${e.message}"
            }
            buscando = false
        }
    }

    LaunchedEffect(sugerencia) { if (sugerencia.isNotBlank()) buscar() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Pon una foto al plan", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    "Se buscan fotos con licencia libre (se cita al autor). Tú pegas la URL si prefieres la tuya.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = consulta,
                        onValueChange = { consulta = it },
                        label = { Text("Buscar (ej: ramen, sushi, café)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { buscar() }, enabled = !buscando) {
                        Text(if (buscando) "…" else "Buscar")
                    }
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(10.dp))

                if (buscando) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(resultados) { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onPick(f.url) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = f.url,
                                    contentDescription = f.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(8.dp)
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        f.title.ifBlank { "Sin título" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                    Text(
                                        listOf(f.attribution, f.license)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar")
                }
            }
        }
    }
}
