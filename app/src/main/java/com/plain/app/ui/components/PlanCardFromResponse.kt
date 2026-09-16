package com.plain.app.ui.components

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.data.FavoritePlan
import com.plain.app.data.PlanResponse
import com.plain.app.ui.theme.LikeGreen
import com.plain.app.ui.theme.NopeRed

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlanCardFromResponse(
    plan: PlanResponse,
    offsetX: Float,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onMoreInfo: (PlanResponse) -> Unit,
    onSendToAgent: (PlanResponse) -> Unit,
    webhookAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rotation = offsetX * 0.08f
    val scale = 1f - (kotlin.math.abs(offsetX) / 2000f).coerceAtMost(0.15f)

    // Progreso del gesto 0..1 (para intensidad del borde y del sello)
    val dragProgress = (kotlin.math.abs(offsetX) / 420f).coerceIn(0f, 1f)
    val accent = if (offsetX > 0) LikeGreen else NopeRed

    Card(
        modifier = modifier
            .offset(x = offsetX.dp)
            .rotate(rotation)
            .scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        border = if (dragProgress > 0.02f) {
            BorderStroke(
                width = (2f + dragProgress * 3f).dp,
                color = accent.copy(alpha = dragProgress * 0.95f)
            )
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp + (dragProgress * 14f).dp
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // ── CABECERA: imagen a sangre con título encima ──
            val catRes = categoryImageRes(plan.category)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(262.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (catRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(catRes),
                        contentDescription = plan.category,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = plan.emoji, fontSize = 88.sp)
                }

                // Degradado inferior para que el título se lea sobre la foto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                            )
                        )
                )

                // Chip de categoría (arriba izquierda)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                ) {
                    Text(
                        text = "${plan.emoji} ${plan.category}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Sello ME GUSTA / NO (crece con el arrastre)
                if (dragProgress > 0.05f) {
                    Text(
                        text = if (offsetX > 0) "ME GUSTA" else "NO",
                        modifier = Modifier
                            .align(if (offsetX > 0) Alignment.TopEnd else Alignment.BottomStart)
                            .padding(20.dp)
                            .graphicsLayer {
                                alpha = dragProgress
                                scaleX = 0.7f + dragProgress * 0.4f
                                scaleY = 0.7f + dragProgress * 0.4f
                            }
                            .background(
                                color = accent.copy(alpha = 0.35f + dragProgress * 0.65f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .rotate(if (offsetX > 0) 12f else -12f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                // Título + lugar sobre el degradado
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = plan.location,
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── CUERPO ──
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {

                // Píldoras de datos clave
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Distancia a la ciudad elegida (aparece solo al ampliar el radio)
                    val dist = plan.distanceKm
                    if (dist != null && dist > 0.5) {
                        InfoPill(
                            "📍 ${dist.toInt()} km · ${plan.city.lowercase().replaceFirstChar { it.uppercase() }}",
                            MaterialTheme.colorScheme.secondary
                        )
                    }
                    InfoPill("💰 ${plan.price}", MaterialTheme.colorScheme.primary)
                    InfoPill("⏱ ${plan.duration}", MaterialTheme.colorScheme.secondary)
                    val (pEmoji, pLabel, pColor) = when (plan.planType) {
                        "SOLO" -> Triple("🧑", "Solo", MaterialTheme.colorScheme.tertiary)
                        "PAREJA" -> Triple("💑", "Pareja", MaterialTheme.colorScheme.secondary)
                        else -> Triple("👥", "Amigos", MaterialTheme.colorScheme.primary)
                    }
                    InfoPill("$pEmoji $pLabel", pColor)

                    // Disponibilidad: solo cuando aporta información
                    if (plan.availability.isNotBlank() && plan.availability != "Todo el año") {
                        if (plan.isAvailableNow) {
                            InfoPill("📅 ${plan.availability}", LikeGreen)
                        } else {
                            InfoPill("🔒 ${plan.availability}", NopeRed)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (plan.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = plan.tags.joinToString("  ·  ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { addToCalendar(context, plan) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Calendario", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = { sharePlan(context, plan) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Compartir", style = MaterialTheme.typography.labelSmall)
                    }

                    // Detalles + quedadas
                    Button(
                        onClick = { onMoreInfo(plan) },
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("Ver más", style = MaterialTheme.typography.labelSmall)
                    }

                    if (webhookAvailable) {
                        FilledTonalButton(
                            onClick = { onSendToAgent(plan) },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = LikeGreen.copy(alpha = 0.18f)
                            )
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Píldora de dato clave (precio, duración, tipo…). */
@Composable
private fun InfoPill(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ── Helper functions ──

fun addToCalendar(context: Context, plan: PlanResponse) {
    addToCalendar(context, plan.emoji, plan.title, plan.description, plan.location, plan.price, plan.duration, plan.tags)
}

fun addToCalendar(context: Context, plan: FavoritePlan) {
    addToCalendar(context, plan.emoji, plan.title, plan.description, plan.location, plan.price, plan.duration, plan.tags)
}

private fun addToCalendar(context: Context, emoji: String, title: String, description: String, location: String, price: String, duration: String, tags: List<String>) {
    val fullTitle = "$emoji $title"
    val desc = buildString {
        appendLine(description)
        appendLine()
        append("📍 $location")
        if (price != "0€") append(" · 💰 $price")
        appendLine()
        if (tags.isNotEmpty()) append("🏷️ ${tags.joinToString(", ")}")
        appendLine()
        appendLine("— vía PLΛIN")
    }

    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, fullTitle)
        putExtra(CalendarContract.Events.DESCRIPTION, desc)
        putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        // Default: 2 hours from now
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600_000)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 3600_000 + parseDurationMillis(duration))
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

fun sharePlan(context: Context, plan: PlanResponse) {
    sharePlan(context, plan.emoji, plan.title, plan.location, plan.price, plan.description)
}

fun sharePlan(context: Context, plan: FavoritePlan) {
    sharePlan(context, plan.emoji, plan.title, plan.location, plan.price, plan.description)
}

private fun sharePlan(context: Context, emoji: String, title: String, location: String, price: String, description: String) {
    val text = buildString {
        appendLine("$emoji $title")
        appendLine("📍 $location")
        append("💰 $price")
        appendLine()
        appendLine()
        append(description)
        appendLine()
        appendLine()
        appendLine("— vía PLΛIN")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir plan"))
}

/** Parse duration strings like "2h", "1.5h", "30min", "Todo el día" into milliseconds. */
fun parseDurationMillis(duration: String): Long {
    val trimmed = duration.trim().lowercase()
    return when {
        trimmed.contains("todo el día") || trimmed.contains("dia") -> 8 * 3600_000L
        trimmed.contains("h") -> {
            val num = trimmed.replace("h", "").trim().toFloatOrNull() ?: 2f
            (num * 3600_000).toLong()
        }
        trimmed.contains("min") -> {
            val num = trimmed.replace("min", "").trim().toFloatOrNull() ?: 30f
            (num * 60_000).toLong()
        }
        else -> 2 * 3600_000L // default 2 hours
    }
}

/** Mapa categoría -> imagen de banner (7 categorías). Null si no hay imagen. */
fun categoryImageRes(category: String): Int? {
    return when (category.trim().lowercase()) {
        "ocio" -> com.plain.app.R.drawable.cat_ocio
        "cultura" -> com.plain.app.R.drawable.cat_cultura
        "gastronomía", "gastronomia", "comida" -> com.plain.app.R.drawable.cat_gastronomia
        "naturaleza" -> com.plain.app.R.drawable.cat_naturaleza
        "compras" -> com.plain.app.R.drawable.cat_compras
        "música", "musica" -> com.plain.app.R.drawable.cat_musica
        "deporte" -> com.plain.app.R.drawable.cat_deporte
        else -> null
    }
}
