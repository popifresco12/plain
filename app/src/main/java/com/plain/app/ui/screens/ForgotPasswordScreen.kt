package com.plain.app.ui.screens

import com.plain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.ForgotPasswordRequest
import com.plain.app.data.ResetPasswordRequest
import kotlinx.coroutines.launch

/**
 * Recuperar contraseña en dos pasos: pides un código y con él pones una nueva.
 *
 * Antes esto no existía: si olvidabas la contraseña, la cuenta se perdía (no hay
 * soporte que la restaure a mano).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var email by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var paso by remember { mutableStateOf(1) }
    var cargando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forgot_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (paso == 1) "Escribe el email de tu cuenta y te damos un código."
                else "Introduce el código y tu nueva contraseña.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                enabled = paso == 1,
                modifier = Modifier.fillMaxWidth()
            )

            if (paso == 2) {
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it.uppercase() },
                    label = { Text(stringResource(R.string.forgot_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nueva,
                    onValueChange = { nueva = it },
                    label = { Text(stringResource(R.string.forgot_new_password)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            aviso?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            Button(
                onClick = {
                    error = null
                    aviso = null
                    cargando = true
                    scope.launch {
                        try {
                            if (paso == 1) {
                                val r = ApiClient.service.forgotPassword(
                                    ForgotPasswordRequest(email.trim())
                                )
                                val cuerpo = r.body()
                                if (r.isSuccessful && cuerpo != null) {
                                    paso = 2
                                    // Sin servidor de correo configurado, el código vuelve aquí
                                    // (modo pruebas) para poder seguir el flujo.
                                    aviso = if (cuerpo.devCode != null)
                                        "Modo pruebas: tu código es ${cuerpo.devCode}"
                                    else
                                        "Te hemos enviado un código a tu correo."
                                } else {
                                    error = "No se pudo pedir el código (${r.code()})"
                                }
                            } else {
                                val r = ApiClient.service.resetPassword(
                                    ResetPasswordRequest(email.trim(), codigo.trim(), nueva)
                                )
                                if (r.isSuccessful) {
                                    aviso = context.getString(R.string.msg_password_changed)
                                    onDone()
                                } else {
                                    error = "Código incorrecto o caducado (${r.code()})"
                                }
                            }
                        } catch (e: Exception) {
                            error = "Sin conexión: ${e.message}"
                        }
                        cargando = false
                    }
                },
                enabled = !cargando && (if (paso == 1) email.isNotBlank() else codigo.length >= 4 && nueva.length >= 8),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        cargando -> "Un momento…"
                        paso == 1 -> "Enviar código"
                        else -> "Cambiar contraseña"
                    }
                )
            }
        }
    }
}
