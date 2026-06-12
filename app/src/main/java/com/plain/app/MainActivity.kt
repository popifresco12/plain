package com.plain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plain.app.data.AuthManager
import com.plain.app.data.auth.BiometricAuthHelper
import com.plain.app.ui.screens.BiometricSettingsScreen
import com.plain.app.ui.screens.BiometricUnlockScreen
import com.plain.app.ui.screens.CitySelectionScreen
import com.plain.app.ui.screens.LoginScreen
import com.plain.app.ui.screens.RegisterScreen
import com.plain.app.ui.screens.SettingsScreen
import com.plain.app.ui.screens.SwipeScreen
import com.plain.app.ui.theme.PLAINTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved token
        AuthManager.init(this)
        val savedToken = AuthManager.getToken()
        val isLoggedIn = savedToken != null

        setContent {
            PLAINTheme {
                PLAINApp(this, isLoggedIn = savedToken != null)
            }
        }
    }
}

@Composable
fun PLAINApp(activity: MainActivity, isLoggedIn: Boolean) {
    val navController = rememberNavController()
    val biometricHelper = remember { BiometricAuthHelper(activity) }
    val startDestination = if (isLoggedIn) "city_selection" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("city_selection") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGoToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("city_selection") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onGoToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable("city_selection") {
            CitySelectionScreen(
                onCitySelected = { city ->
                    navController.navigate("swipe/$city")
                }
            )
        }

        composable("swipe/{cityName}") { backStackEntry ->
            val cityName = backStackEntry.arguments?.getString("cityName") ?: "BARCELONA"
            SwipeScreen(
                city = cityName,
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBiometricSettings = { navController.navigate("biometric_settings") }
            )
        }

        composable("biometric_settings") {
            BiometricSettingsScreen(
                biometricHelper = biometricHelper,
                onBack = { navController.popBackStack() },
                onTestBiometric = { navController.navigate("biometric_unlock") }
            )
        }

        composable("biometric_unlock") {
            BiometricUnlockScreen(
                biometricHelper = biometricHelper,
                onAuthenticated = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}
