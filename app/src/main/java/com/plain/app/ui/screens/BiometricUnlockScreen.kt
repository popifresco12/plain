package com.plain.app.ui.screens

import com.plain.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.auth.BiometricAuthHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricUnlockScreen(
    biometricHelper: BiometricAuthHelper,
    onAuthenticated: () -> Unit,
    onCancel: () -> Unit
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // stringResource() solo se puede llamar en composición, y performAuth() es una
    // función normal: los textos se resuelven aquí y la función los recibe hechos.
    val txtUnlock = stringResource(R.string.bio_unlock)
    val txtRequired = stringResource(R.string.bio_required)
    val txtUse = stringResource(R.string.bio_use)
    val txtFailed = stringResource(R.string.bio_failed)

    // Authenticate function
    fun performAuth() {
        if (isAuthenticating) return
        isAuthenticating = true
        errorMessage = null
        scope.launch {
            val success = biometricHelper.authenticate(
                owner = lifecycleOwner,
                title = txtUnlock,
                subtitle = txtRequired,
                description = txtUse
            )
            isAuthenticating = false
            if (success) {
                onAuthenticated()
            } else {
                errorMessage = txtFailed
            }
        }
    }

    // Auto-trigger on first composition
    LaunchedEffect(Unit) {
        performAuth()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = stringResource(R.string.set_biometrics),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAIN",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isAuthenticating) "Autenticando..." else "Desbloquear con biometría",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            if (!isAuthenticating) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { performAuth() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = "")
                            Text(stringResource(R.string.bio_try_again), fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onCancel
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}