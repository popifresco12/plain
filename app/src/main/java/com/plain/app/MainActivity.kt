package com.plain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.plain.app.data.ApiClient
import com.plain.app.data.AuthManager
import com.plain.app.data.CityPreferences
import com.plain.app.data.LocationHelper
import com.plain.app.data.auth.BiometricAuthHelper
import com.plain.app.ui.screens.BiometricSettingsScreen
import com.plain.app.ui.screens.BiometricUnlockScreen
import com.plain.app.ui.screens.CitySelectionScreen
import com.plain.app.ui.screens.CreatePlanScreen
import com.plain.app.ui.screens.FavoritesScreen
import com.plain.app.ui.screens.LoginScreen
import com.plain.app.ui.screens.RegisterScreen
import com.plain.app.ui.screens.SettingsScreen
import com.plain.app.ui.screens.SwipeScreen
import com.plain.app.ui.theme.PLAINTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved token
        AuthManager.init(this)
        val appContext = applicationContext

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
                // Si ya hay sesión guardada, ir directo a resolver ciudad (sin login)
                val startDest = if (AuthManager.getUserToken() != null) "resolve_city" else "login"
                NavHost(navController = navController, startDestination = startDest) {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate("resolve_city") {
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
                                navController.navigate("resolve_city") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onGoToLogin = { navController.popBackStack() },
                            onGoToBusiness = { }
                        )
                    }

                    // Resolver ciudad: guardada > GPS > selector manual
                    composable("resolve_city") {
                        val context = appContext
                        var resolved by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            resolved = withContext(Dispatchers.IO) {
                                CityPreferences.getCity(context) ?: run {
                                    val gps = LocationHelper.detectCity(context)
                                    if (gps != null) {
                                        CityPreferences.setGpsDetectedCity(context, gps)
                                        gps
                                    } else null
                                }
                            }
                        }
                        val city = resolved
                        if (city != null) {
                            LaunchedEffect(city) {
                                navController.navigate("swipe/$city") {
                                    popUpTo("resolve_city") { inclusive = true }
                                }
                            }
                        } else {
                            CitySelectionScreen(
                                onCitySelected = { c ->
                                    CityPreferences.setCity(context, c)
                                    navController.navigate("swipe/$c") {
                                        popUpTo("resolve_city") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    // User main flow
                    composable("swipe/{cityName}") { backStackEntry ->
                        val cityName = backStackEntry.arguments?.getString("cityName") ?: "BARCELONA"
                        CityPreferences.setCity(appContext, cityName) // recordar la última
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val planCreated = navBackStackEntry?.savedStateHandle?.get<Boolean>("plan_created") ?: false
                        SwipeScreen(
                            city = cityName,
                            onBack = { navController.popBackStack() },
                            onSettings = { navController.navigate("settings") },
                            onFavorites = { navController.navigate("favorites") },
                            onCreatePlan = {
                                navController.navigate("create_plan/$cityName") {
                                    launchSingleTop = true
                                }
                            },
                            planCreated = planCreated
                        )
                    }

                    composable("create_plan/{cityName}") { backStackEntry ->
                        val cityName = backStackEntry.arguments?.getString("cityName") ?: "BARCELONA"
                        CreatePlanScreen(
                            city = cityName,
                            onBack = { navController.popBackStack() },
                            onCreated = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("plan_created", true)
                                navController.popBackStack()
                            }
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
                            onBiometricSettings = { navController.navigate("biometric_settings") },
                            onChangeCity = {
                                navController.navigate("city_picker") {
                                    popUpTo("settings") { inclusive = false }
                                }
                            }
                        )
                    }

                    // Selector de ciudad desde ajustes (cambiar manualmente)
                    composable("city_picker") {
                        CitySelectionScreen(
                            onCitySelected = { c ->
                                CityPreferences.setCity(appContext, c)
                                navController.navigate("swipe/$c") {
                                    popUpTo("city_picker") { inclusive = true }
                                }
                            }
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
