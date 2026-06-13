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
import com.plain.app.ui.screens.BusinessAuthScreen
import com.plain.app.ui.screens.BusinessDashboardScreen
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
        val savedUserToken = AuthManager.getUserToken()
        val savedBusinessToken = AuthManager.getBusinessToken()
        val isLoggedIn = savedUserToken != null
        val isBusinessLoggedIn = savedBusinessToken != null

        setContent {
            PLAINTheme {
                PLAINApp(
                    activity = this,
                    isLoggedIn = isLoggedIn,
                    isBusinessLoggedIn = isBusinessLoggedIn
                )
            }
        }
    }
}

@Composable
fun PLAINApp(
    activity: MainActivity,
    isLoggedIn: Boolean,
    isBusinessLoggedIn: Boolean
) {
    val navController = rememberNavController()
    val biometricHelper = remember { BiometricAuthHelper(activity) }

    // Determine start destination
    val startDestination = when {
        isBusinessLoggedIn -> "business_dashboard"
        isLoggedIn -> "city_selection"
        else -> "login"
    }

    // Listen for session expiration (token expired / 401)
    LaunchedEffect(Unit) {
        ApiClient.sessionExpired.collect {
            // Check which token expired based on current route
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            if (currentRoute?.contains("business") == true) {
                navController.navigate("business_login") {
                    popUpTo(0) { inclusive = true }
                }
            } else {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        // User Auth
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("city_selection") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate("register") },
                onGoToBusiness = { navController.navigate("business_login") }
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
                onGoToBusiness = { navController.navigate("business_login") }
            )
        }

        // Business Auth
        composable("business_login") {
            BusinessAuthScreen.BusinessLoginScreen(
                onLoginSuccess = {
                    navController.navigate("business_dashboard") {
                        popUpTo("business_login") { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate("business_register") },
                onBackToUser = { navController.navigate("login") { popUpTo(0) { inclusive = true } } }
            )
        }

        composable("business_register") {
            BusinessAuthScreen.BusinessRegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("business_dashboard") {
                        popUpTo("business_register") { inclusive = true }
                    }
                },
                onGoToLogin = { navController.popBackStack() },
                onBackToUser = { navController.navigate("login") { popUpTo(0) { inclusive = true } } }
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

        // Business flow
        composable("business_dashboard") {
            BusinessDashboardScreen(
                onBack = { navController.navigate("business_login") { popUpTo(0) { inclusive = true } } },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
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