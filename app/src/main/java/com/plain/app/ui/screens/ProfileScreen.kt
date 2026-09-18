package com.plain.app.ui.screens

import com.plain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.MeStatus
import com.plain.app.data.ProfileUpdateRequest
import kotlinx.coroutines.launch

/**
 * Perfil del usuario: datos de la cuenta y estado de verificación del email.
 *
 * Antes lo único que se podía tocar era la contraseña (y solo al registrarse):
 * no había forma de ver ni corregir el email, ni de saber si estaba verificado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onHistory: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<MeStatus?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            ApiClient.service.getMe().body()?.let {
                username = it.username
                email = it.email
            }
            status = ApiClient.service.getMeStatus().body()
        } catch (e: Exception) {
            error = "No pude cargar tu perfil: ${e.message}"
        }
        cargando = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        if (cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.profile_username), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.email), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Estado de verificación (la verificación está implementada pero apagada:
            // el aviso solo aparece si el servidor la exige)
            val st = status
            if (st != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (st.emailVerified) "✅ Email verificado" else "⚠️ Email sin verificar",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            if (st.requiresVerification)
                                "Hace falta verificarlo para entrar: pide un código al servidor."
                            else
                                "La verificación está desactivada por ahora, así que puedes usar la app igual.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Puedes crear hasta ${st.maxPlans} planes.", fontSize = 12.sp)
                    }
                }
            }

            mensaje?.let {
                Text(it, color = Color(0xFF0F7B4F), fontSize = 13.sp)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    guardando = true
                    mensaje = null
                    error = null
                    kotlinx.coroutines.MainScope().launch {
                        try {
                            val r = ApiClient.service.updateProfile(
                                ProfileUpdateRequest(
                                    username = username.trim(),
                                    email = email.trim().ifBlank { null }
                                )
                            )
                            if (r.isSuccessful) {
                                mensaje = "Guardado ✓"
                            } else {
                                error = "No se pudo guardar (${r.code()})"
                            }
                        } catch (e: Exception) {
                            error = "Sin conexión: ${e.message}"
                        }
                        guardando = false
                    }
                },
                enabled = !guardando && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (guardando) "Guardando…" else "Guardar cambios")
            }

            HorizontalDivider()

            FilaAccion("🔑 Cambiar contraseña", "Con un código de un solo uso", onChangePassword)
            FilaAccion("🕓 Historial", "Planes que descartaste o guardaste", onHistory)
        }
    }
}

@Composable
private fun FilaAccion(titulo: String, subtitulo: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(subtitulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
