package com.plain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plain.app.data.ApiClient
import com.plain.app.data.AuthManager
import com.plain.app.data.auth.BiometricAuthHelper
import com.plain.app.ui.screens.BiometricSettingsScreen
import com.plain.app.ui.screens.BiometricUnlockScreen
import com.plain.app.ui.screens.CitySelectionScreen
import com.plain.app.ui.screens.FavoritesScreen
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

        setContent {
            val navController = rememberNavController()
            val biometricHelper = remember { BiometricAuthHelper(this) }

            // Observe session expiration - redirect to login
            LaunchedEffect(Unit) {
                ApiClient.sessionExpired.collect {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            PLAINTheme {
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate("city_selection") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onGoToRegister = { navController.navigate("register") },
                            onGoToBusiness = { }
                        )
                    }

                    composable("register") {
                        RegisterScreen(
                            onRegisterSuccess = {
                                navController.navigate("city_selection") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onGoToLogin = { navController.popBackStack() },
                            onGoToBusiness = { }
                        )
                    }

                    // User main flow
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
                            onSettings = { navController.navigate("settings") },
                            onFavorites = { navController.navigate("favorites") }
                        )
                    }

                    composable("favorites") {
                        FavoritesScreen(
                            onBack = { navController.popBackStack() }
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

                    // Biometric
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
        }
    }
}
