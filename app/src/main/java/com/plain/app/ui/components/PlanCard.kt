package com.plain.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plain.app.model.Plan
import com.plain.app.model.PlanType
import com.plain.app.ui.theme.LikeGreen
import com.plain.app.ui.theme.NopeRed

@Composable
fun PlanCard(
    plan: Plan,
    offsetX: Float,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onMoreInfo: (Plan) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation = offsetX * 0.08f
    val alpha = 1f - (kotlin.math.abs(offsetX) / 600f).coerceAtMost(0.5f)
    val scale = 1f - (kotlin.math.abs(offsetX) / 2000f).coerceAtMost(0.15f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset(x = offsetX.dp)
            .rotate(rotation)
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp)
        ) {
            // Top section - emoji/category banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = plan.emoji,
                    fontSize = 64.sp
                )

                // Swipe hints overlay
                if (kotlin.math.abs(offsetX) > 50) {
                    Text(
                        text = if (offsetX > 0) "ME GUSTA" else "NO",
                        modifier = Modifier
                            .align(if (offsetX > 0) Alignment.TopStart else Alignment.TopEnd)
                            .padding(20.dp)
                            .background(
                                color = if (offsetX > 0) LikeGreen else NopeRed,
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
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tags row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = plan.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    // Type chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when (plan.type) {
                            PlanType.SOLO -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            PlanType.PAREJA -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            PlanType.AMBOS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = when (plan.type) {
                                PlanType.SOLO -> "🧑 Solo"
                                PlanType.PAREJA -> "💑 Pareja"
                                PlanType.AMBOS -> "👥 Ambos"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    // Duration chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "⏱ ${plan.duration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // More info button
                TextButton(
                    onClick = { onMoreInfo(plan) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Ver más detalles ▶",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
