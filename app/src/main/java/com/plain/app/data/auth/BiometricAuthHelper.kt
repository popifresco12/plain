package com.plain.app.data.auth

import android.content.Context
import android.hardware.biometrics.BiometricManager
import androidx.biometric.BiometricManager as BiometricManagerCompat
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

class BiometricAuthHelper(private val context: Context) {

    /** BiometricManager público para que las screens puedan consultar disponibilidad */
    val biometricManager: BiometricManagerCompat = BiometricManagerCompat.from(context)

    /** Comprueba si biometría está disponible y enrollada */
    fun canAuthenticate(): Int = biometricManager.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)

    /** Prompt de autenticación biométrica (corrutina) */
    suspend fun authenticate(
        owner: LifecycleOwner,
        title: String = "Autenticación biométrica",
        subtitle: String = "Usa tu huella o Face ID",
        description: String = "Confirma tu identidad para acceder a PLAIN"
    ): Boolean = suspendCoroutine<Boolean> { cont: Continuation<Boolean> ->
        val executor = context.mainExecutor
        val prompt = BiometricPrompt(owner as androidx.fragment.app.FragmentActivity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                cont.resumeWith(kotlin.Result.success(true))
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                cont.resumeWith(kotlin.Result.success(false))
            }
            override fun onAuthenticationFailed() {
                cont.resumeWith(kotlin.Result.success(false))
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo)
        
        // Note: Standard Continuation doesn't have invokeOnCancellation
        // Cancellation handling would need a different approach in production
    }
}