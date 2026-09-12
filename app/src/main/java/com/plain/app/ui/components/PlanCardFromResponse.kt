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
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
        shape = RoundedCornerShape(24.dp),
        border = if (dragProgress > 0.02f) {
            BorderStroke(
                width = (2f + dragProgress * 3f).dp,
                color = accent.copy(alpha = dragProgress * 0.95f)
            )
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp + (dragProgress * 10f).dp
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Banner: imagen por categoría (si existe) o color plano con emoji
            val catRes = categoryImageRes(plan.category)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
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
                    Text(text = plan.emoji, fontSize = 64.sp)
                }

                // Sello ME GUSTA / NO: aparece y se intensifica con el arrastre
                if (dragProgress > 0.05f) {
                    Text(
                        text = if (offsetX > 0) "ME GUSTA" else "NO",
                        modifier = Modifier
                            .align(if (offsetX > 0) Alignment.TopStart else Alignment.TopEnd)
                            .padding(20.dp)
                            .graphicsLayer {
                                alpha = dragProgress
                                scaleX = 0.7f + dragProgress * 0.4f
                                scaleY = 0.7f + dragProgress * 0.4f
                            }
                            .background(
                                color = accent.copy(alpha = 0.35f + dragProgress * 0.65f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .rotate(if (offsetX > 0) -15f else 15f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }

            // Content
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = plan.price,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))

                // Tags (FlowRow: los chips fluyen sin desbordar)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(plan.category, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    val typeColor = when (plan.planType) {
                        "SOLO" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        "PAREJA" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = typeColor) {
                        val label = when (plan.planType) {
                            "SOLO" -> "🧑 Solo"
                            "PAREJA" -> "💑 Pareja"
                            else -> "👥 Ambos"
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("⏱ ${plan.duration}", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    // Chip de disponibilidad: ámbar si es de fechas concretas, neutro si es todo el año
                    if (plan.availability.isNotBlank() && plan.availability != "Todo el año") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                "📅 ${plan.availability}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    // Badge de disponibilidad actual: verde si se puede ahora, ámbar si no
                    if (!plan.isAvailableNow) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = NopeRed.copy(alpha = 0.15f)
                        ) {
                            Text("🔒 Fuera de temporada", style = MaterialTheme.typography.labelSmall,
                                color = NopeRed,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    } else if (plan.availability.isNotBlank() && plan.availability != "Todo el año") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = LikeGreen.copy(alpha = 0.15f)
                        ) {
                            Text("✅ En temporada", style = MaterialTheme.typography.labelSmall,
                                color = LikeGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calendar button
                    FilledTonalButton(
                        onClick = { addToCalendar(context, plan) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Calendario", style = MaterialTheme.typography.labelSmall)
                    }

                    // Share (WhatsApp / any app)
                    FilledTonalButton(
                        onClick = { sharePlan(context, plan) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Compartir", style = MaterialTheme.typography.labelSmall)
                    }

                    // "Ver más"
                    TextButton(onClick = { onMoreInfo(plan) }) {
                        Text("▶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }

                    // Send to agent
                    if (webhookAvailable) {
                        FilledTonalButton(
                            onClick = { onSendToAgent(plan) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = LikeGreen.copy(alpha = 0.15f)
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
        append("— vía PLΛIN")
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
