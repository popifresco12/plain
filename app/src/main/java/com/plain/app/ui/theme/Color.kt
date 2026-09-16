package com.plain.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta basada en **Radix Colors** (radix-ui.com/colors), un sistema pensado para
 * interfaces: 12 pasos por escala con un uso asignado y contraste garantizado
 * (objetivos APCA). No son colores inventados a ojo.
 *
 *   1-2   fondos          3-5   componentes   6-8   bordes
 *   9-10  sólidos         11-12 texto
 *
 * Escalas usadas:
 *   · orange → marca/acción (cálida, la identidad de PLAIN)
 *   · sand   → neutros cálidos (armonizan con el naranja; un gris puro lo apagaría)
 *   · blue   → información (duración, secundario)
 *   · teal   → tercera categoría (tipo de plan)
 *   · green / red / amber → estados (like, nope, patrocinado)
 */

// ── Light ────────────────────────────────────────────────────────────────────
val LightPrimary = Color(0xFFCC4E00)                 // orange-11 (4.51:1 con blanco; el -9 se quedaba en 2.97:1)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFDFB5)        // orange-4  contenedor
val LightOnPrimaryContainer = Color(0xFF582D1D)     // orange-12 texto sobre él

val LightSecondary = Color(0xFF0D74CE)                // blue-11   con contraste de texto
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD5EFFF)        // blue-4
val LightOnSecondaryContainer = Color(0xFF113264)     // blue-12
val LightTertiary = Color(0xFF008573)                 // teal-11
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCF3EA)         // teal-4
val LightOnTertiaryContainer = Color(0xFF0D3D38)      // teal-12

val LightBackground = Color(0xFFF9F9F8)                // sand-2    fondo (cálido, no blanco puro)
val LightOnBackground = Color(0xFF21201C)             // sand-12
val LightSurface = Color(0xFFFFFFFF)                       // tarjetas en blanco: destacan sobre el fondo
val LightOnSurface = Color(0xFF21201C)
val LightSurfaceVariant = Color(0xFFE9E8E6)            // chips, campos
val LightOnSurfaceVariant = Color(0xFF63635E)         // texto secundario
val LightError = Color(0xFFCE2C31)                      // red-11 (4.95:1 como texto; el -9 daba 3.72:1)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDBDC)             // red-4
val LightOnErrorContainer = Color(0xFF641723)          // red-12
val LightOutline = Color(0xFF8D8D86)                   // sand-9 bordes de componentes (3.17:1; el -7 daba 1.49:1)
val LightOutlineVariant = Color(0xFFDAD9D6)            // sand-6 separadores decorativos

// ── Dark ─────────────────────────────────────────────────────────────────────
val DarkPrimary = Color(0xFFFFA057)                 // orange-dark-11: naranja claro para destacar en oscuro
val DarkOnPrimary = Color(0xFF111110)                // texto oscuro sobre el naranja claro
val DarkPrimaryContainer = Color(0xFF331E0B)
val DarkOnPrimaryContainer = Color(0xFFFFE0C2)

val DarkSecondary = Color(0xFF70B8FF)
val DarkOnSecondary = Color(0xFF0D1520)
val DarkSecondaryContainer = Color(0xFF0D2847)
val DarkOnSecondaryContainer = Color(0xFFC2E6FF)
val DarkTertiary = Color(0xFF0BD8B6)
val DarkOnTertiary = Color(0xFF0D1514)
val DarkTertiaryContainer = Color(0xFF0D2D2A)
val DarkOnTertiaryContainer = Color(0xFFADF0DD)

val DarkBackground = Color(0xFF111110)
val DarkOnBackground = Color(0xFFEEEEEC)
val DarkSurface = Color(0xFF191918)
val DarkOnSurface = Color(0xFFEEEEEC)
val DarkSurfaceVariant = Color(0xFF222221)
val DarkOnSurfaceVariant = Color(0xFFB5B3AD)
val DarkError = Color(0xFFFF9592)                   // red-dark-11
val DarkOnError = Color(0xFF111110)
val DarkErrorContainer = Color(0xFF3B1219)
val DarkOnErrorContainer = Color(0xFFFFD1D9)
val DarkOutline = Color(0xFF62605B)
val DarkOutlineVariant = Color(0xFF3B3A37)

// ── Estados del swipe ────────────────────────────────────────────────────────
val LikeGreen = Color(0xFF218358)                     // green-11 (legible como texto en claro)
val LikeGreenDark = Color(0xFF3DD68C)            // green-11 (más luminoso en oscuro)
val NopeRed = Color(0xFFCE2C31)                        // red-11
val NopeRedDark = Color(0xFFFF9592)                // red-11
val SponsoredGold = Color(0xFFAB6400)                // amber-11 (legible como texto)
val SponsoredGoldDark = Color(0xFFFFCA16)
