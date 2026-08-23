package com.plain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.plain.app.data.ApiClient
import com.plain.app.data.AuthManager
import com.plain.app.data.WebhookRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onBiometricSettings: () -> Unit,
    onChangeCity: () -> Unit
) {
    var webhookUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load existing webhook config
    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.service.getWebhook()
            if (resp.isSuccessful) {
                resp.body()?.let {
                    webhookUrl = it.url ?: ""
                    apiKey = it.apiKey ?: ""
                }
            }
        } catch (_: Exception) {}
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Webhook section
            Text(
                text = "🔗 Tu Agente Personal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Conecta tu agente de IA (Hermes, ChatGPT, Claude, Grok...) para que al hacer swipe right reciba el plan y lo ejecute automáticamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it; saved = false },
                label = { Text("URL del Webhook") },
                placeholder = { Text("https://tu-agente.com/webhook") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                label = { Text("API Key (opcional)") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            )

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (webhookUrl.isBlank()) {
                        error = "Introduce una URL de webhook"
                        return@Button
                    }
                    saving = true
                    error = null
                    saved = false
                    scope.launch {
                        try {
                            val resp = ApiClient.service.saveWebhook(
                                WebhookRequest(webhookUrl.trim(), apiKey.trim().ifBlank { null })
                            )
                            if (resp.isSuccessful) {
                                saved = true
                            } else {
                                error = "Error al guardar (${resp.code()})"
                            }
                        } catch (e: Exception) {
                            error = "Error de conexión: ${e.localizedMessage}"
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Guardar Webhook")
                }
            }

            if (saved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("✅ Webhook guardado correctamente", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider()

            Spacer(modifier = Modifier.height(24.dp))

            // City section
            Text(
                text = "📍 Ciudad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "La app detecta tu ciudad por ubicación automáticamente. Cámbiala aquí si quieres explorar otra.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onChangeCity,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cambiar ciudad", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider()

            Spacer(modifier = Modifier.height(24.dp))

            // Logout
            Text(
                text = "Cuenta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    AuthManager.clearToken()
                    ApiClient.setToken(null)
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cerrar sesión")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Divider()

            Spacer(modifier = Modifier.height(24.dp))

            // Biometric section
            Text(
                text = "Seguridad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBiometricSettings,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = "Biometría", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Autenticación biométrica",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
