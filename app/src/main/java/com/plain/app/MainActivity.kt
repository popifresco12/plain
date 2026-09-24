package com.plain.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.plain.app.data.CrashReporter
import com.plain.app.data.Analytics
import com.plain.app.data.SwipeHistory
import com.plain.app.data.LocationHelper
import com.plain.app.data.NotificationWorker
import com.plain.app.data.PlainNotifier
import com.plain.app.data.RealtimeClient
import com.plain.app.ui.screens.MatchesScreen
import com.plain.app.ui.screens.NotificationsScreen
import com.plain.app.data.auth.BiometricAuthHelper
import com.plain.app.ui.screens.BusinessAuthScreen
import com.plain.app.ui.screens.BusinessDashboardScreen
import com.plain.app.ui.screens.BiometricSettingsScreen
import com.plain.app.ui.screens.BiometricUnlockScreen
import com.plain.app.ui.screens.CitySelectionScreen
import com.plain.app.ui.screens.CreatePlanScreen
import com.plain.app.ui.screens.GroupChatScreen
import com.plain.app.ui.screens.FavoritesScreen
import com.plain.app.ui.screens.LoginScreen
import com.plain.app.ui.screens.ProfileScreen
import com.plain.app.ui.screens.ForgotPasswordScreen
import com.plain.app.ui.screens.HistoryScreen
import com.plain.app.ui.screens.RegisterScreen
import com.plain.app.ui.screens.SettingsScreen
import com.plain.app.ui.screens.SwipeScreen
import com.plain.app.ui.theme.PLAINTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Aviso tocado en la barra de notificaciones, pendiente de abrir. */
    private val pendingNav = mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(PlainNotifier.EXTRA_KIND)) pendingNav.value = intent
    }

    override fun onStart() {
        super.onStart()
        if (AuthManager.getUserToken() != null) RealtimeClient.start(this)
    }

    override fun onStop() {
        super.onStop()
        RealtimeClient.stop()   // en segundo plano no se mantiene el socket (batería)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved token
        AuthManager.init(this)
        PlainNotifier.ensureChannels(this)
        if (AuthManager.getUserToken() != null) NotificationWorker.schedule(this)
        if (intent?.hasExtra(PlainNotifier.EXTRA_KIND) == true) pendingNav.value = intent

        // Captura de fallos: engancha el handler y manda lo pendiente de la
        // sesión anterior (si la app petó, el informe llega aquí).
        Analytics.init(this)
        SwipeHistory.init(this)
        CrashReporter.install(this)
        CrashReporter.flush(this)
        val appContext = applicationContext

        setContent {
            val navController = rememberNavController()
            val biometricHelper = remember { BiometricAuthHelper(this) }

            val backStack by navController.currentBackStackEntryAsState()
            val route = backStack?.destination?.route
            val loggedInRoute = route != null && route !in setOf("login", "register", "forgot_password", "business", "resolve_city") &&
                !route.startsWith("business")

            // Con sesión iniciada: tiempo real + avisos en segundo plano (idempotente)
            LaunchedEffect(loggedInRoute) {
                if (loggedInRoute && AuthManager.getUserToken() != null) {
                    RealtimeClient.start(appContext)
                    NotificationWorker.schedule(appContext)
                    NotificationWorker.runNow(appContext)
                }
            }

            // Android 13+: pedir permiso de avisos una sola vez, ya dentro de la app
            val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(loggedInRoute) {
                if (!loggedInRoute || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                val prefs = appContext.getSharedPreferences("plain_notifs", MODE_PRIVATE)
                if (!PlainNotifier.canPost(appContext) && !prefs.getBoolean("asked_permission", false)) {
                    prefs.edit().putBoolean("asked_permission", true).apply()
                    notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Tocar una notificación: abrir el chat / los match cuando ya hay sesión y pantalla
            val navIntent = pendingNav.value
            LaunchedEffect(navIntent, loggedInRoute) {
                val i = navIntent ?: return@LaunchedEffect
                if (!loggedInRoute) return@LaunchedEffect
                pendingNav.value = null
                val kind = i.getStringExtra(PlainNotifier.EXTRA_KIND)
                val gid = i.getIntExtra(PlainNotifier.EXTRA_GROUP_ID, -1)
                val title = i.getStringExtra(PlainNotifier.EXTRA_GROUP_TITLE) ?: "Quedada"
                when {
                    gid > 0 && kind in setOf("message", "join", "request", "approved") ->
                        navController.navigate("group_chat/$gid/${android.net.Uri.encode(title)}")
                    kind in setOf("match", "group_new", "rejected") -> navController.navigate("matches")
                    else -> navController.navigate("notifications")
                }
            }

            // Observe session expiration - redirect to login
            LaunchedEffect(Unit) {
                ApiClient.sessionExpired.collect {
                    RealtimeClient.stop()
                    NotificationWorker.cancel(appContext)
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
                            onGoToForgot = { navController.navigate("forgot_password") },
                            onGoToBusiness = { navController.navigate("business") }
                        )
                    }

                    // Panel de empresa: si ya hay sesión de negocio entra directo al
                    // dashboard; si no, registro/login (el token de negocio es aparte
                    // del de usuario, así que se puede usar la app como particular).
                    composable("business") {
                        var businessKey by remember { mutableIntStateOf(0) }
                        val businessLoggedIn = remember(businessKey) {
                            AuthManager.getBusinessToken() != null
                        }
                        key(businessKey) {
                            if (businessLoggedIn) {
                                BusinessDashboardScreen(
                                    onBack = { navController.popBackStack() },
                                    onLoggedOut = { businessKey++ }
                                )
                            } else {
                                BusinessAuthScreen(
                                    onAuthenticated = { businessKey++ },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    composable("register") {
                        RegisterScreen(
                            onRegisterSuccess = {
                                navController.navigate("resolve_city") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onGoToLogin = { navController.popBackStack() },
                            onGoToBusiness = { navController.navigate("business") }
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
                            onBack = {
                                // El swipe es la raíz del stack: popBackStack() no hacía nada.
                                // Volvemos explícitamente al selector de ciudad mundial.
                                navController.navigate("city_picker") {
                                    popUpTo(backStackEntry.destination.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onSettings = { navController.navigate("settings") },
                            onFavorites = { navController.navigate("favorites") },
                            onCreatePlan = {
                                navController.navigate("create_plan/$cityName") {
                                    launchSingleTop = true
                                }
                            },
                            onNotifications = { navController.navigate("notifications") },
                            onMatches = { navController.navigate("matches") },
                            onOpenChat = { groupId, groupTitle ->
                                navController.navigate("group_chat/$groupId/${android.net.Uri.encode(groupTitle)}")
                            },
                            onSwitchCity = { nueva ->
                                // GPS: el usuario está en otra ciudad; cambiamos sin pasar por el selector
                                CityPreferences.setCity(appContext, nueva)
                                navController.navigate("swipe/$nueva") {
                                    popUpTo(backStackEntry.destination.id) { inclusive = true }
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

                    // Chat de una quedada grupal
                    composable(
                        "group_chat/{groupId}/{groupTitle}",
                        arguments = listOf(
                            androidx.navigation.navArgument("groupId") { type = androidx.navigation.NavType.IntType },
                            androidx.navigation.navArgument("groupTitle") { type = androidx.navigation.NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments?.getInt("groupId") ?: 0
                        val groupTitle = backStackEntry.arguments?.getString("groupTitle") ?: "Quedada"
                        GroupChatScreen(
                            groupId = groupId,
                            groupTitle = groupTitle,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // 0.9.0: bandeja de avisos y match
                    composable("notifications") {
                        NotificationsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenChat = { gid, title ->
                                navController.navigate("group_chat/$gid/${android.net.Uri.encode(title)}")
                            },
                            onOpenMatches = { navController.navigate("matches") }
                        )
                    }

                    composable("matches") {
                        MatchesScreen(
                            onBack = { navController.popBackStack() },
                            onOpenChat = { gid, title ->
                                navController.navigate("group_chat/$gid/${android.net.Uri.encode(title)}")
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
                                RealtimeClient.stop()
                                NotificationWorker.cancel(appContext)
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onBiometricSettings = { navController.navigate("biometric_settings") },
                            onProfile = { navController.navigate("profile") },
                            onChangeCity = {
                                navController.navigate("city_picker") {
                                    popUpTo("settings") { inclusive = false }
                                }
                            }
                        )
                    }


                    // Perfil: ver y corregir la cuenta (antes solo se podía cambiar
                    // la contraseña al registrarse)
                    composable("profile") {
                        ProfileScreen(
                            onBack = { navController.popBackStack() },
                            onChangePassword = { navController.navigate("forgot_password") },
                            onHistory = { navController.navigate("history") }
                        )
                    }

                    // Recuperar contraseña (o cambiarla estando dentro)
                    composable("forgot_password") {
                        ForgotPasswordScreen(
                            onBack = { navController.popBackStack() },
                            onDone = { navController.popBackStack() }
                        )
                    }

                    // Historial de descartados y guardados
                    composable("history") {
                        HistoryScreen(onBack = { navController.popBackStack() })
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
