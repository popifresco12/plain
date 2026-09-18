package com.plain.app.ui.screens

import com.plain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.ApiClient
import com.plain.app.data.AuthManager
import com.plain.app.data.BusinessLoginRequest
import com.plain.app.data.BusinessRegisterRequest
import kotlinx.coroutines.launch

/**
 * Acceso de empresas: registro e inicio de sesión en la misma pantalla.
 * El token de negocio se guarda aparte del de usuario (AuthManager), así que
 * se puede estar dentro como particular y como negocio a la vez.
 */
@Composable
fun BusinessAuthScreen(
    onAuthenticated: () -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRegister by remember { mutableStateOf(false) }
    var company by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "PLΛIN Business",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.biz_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Selector de modo
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isRegister,
                    onClick = { isRegister = false; error = null },
                    label = { Text(stringResource(R.string.biz_login)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = isRegister,
                    onClick = { isRegister = true; error = null },
                    label = { Text(stringResource(R.string.register_submit)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            Spacer(Modifier.height(20.dp))

            if (isRegister) {
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text(stringResource(R.string.biz_company)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || (isRegister && company.isBlank())) {
                        error = context.getString(R.string.err_fill_all)
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val resp = if (isRegister) {
                                ApiClient.service.registerBusiness(
                                    BusinessRegisterRequest(company.trim(), email.trim(), password)
                                )
                            } else {
                                ApiClient.service.loginBusiness(
                                    BusinessLoginRequest(email.trim(), password)
                                )
                            }
                            if (resp.isSuccessful) {
                                val body = resp.body()
                                if (body?.accessToken.isNullOrBlank()) {
                                    error = context.getString(R.string.err_no_session)
                                } else {
                                    AuthManager.saveBusinessToken(body!!.accessToken)
                                    onAuthenticated()
                                }
                            } else {
                                error = apiErrorMessage(resp)
                            }
                        } catch (e: Exception) {
                            error = "Error de conexión: ${e.localizedMessage}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.small,
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isRegister) "Crear cuenta de empresa" else "Entrar", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.biz_are_you_user),
                modifier = Modifier.clickable { onBack() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

/** Extrae el mensaje del backend ({"detail": "..."}) para no mostrar "Error 400" a secas. */
internal fun apiErrorMessage(resp: retrofit2.Response<*>): String {
    return try {
        val raw = resp.errorBody()?.string()
        if (raw.isNullOrBlank()) "Error ${resp.code()}"
        else org.json.JSONObject(raw).optString("detail").ifBlank { "Error ${resp.code()}" }
    } catch (_: Exception) {
        "Error ${resp.code()}"
    }
}
