package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.pathfinding.TurnByTurnGuidance
import com.example.domain.pathfinding.TurnByTurnNavigator
import com.example.domain.pathfinding.TurnDirection

private fun iconForTurn(dir: TurnDirection): ImageVector = when (dir) {
    TurnDirection.STRAIGHT -> Icons.Default.ArrowUpward
    TurnDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    TurnDirection.TURN_LEFT -> Icons.Default.TurnLeft
    TurnDirection.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    TurnDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    TurnDirection.TURN_RIGHT -> Icons.Default.TurnRight
    TurnDirection.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    TurnDirection.U_TURN -> Icons.Default.UTurnLeft
    TurnDirection.ARRIVED -> Icons.Default.Flag
}

/**
 * Google Maps-style Turn-by-Turn Floating Navigation Sign Banner.
 * Shows primary maneuver ("50m phía trước rẽ trái" / "Vị trí cửa thoát hiểm: 500m phía trước"),
 * upcoming next-step bar ("Lượt chờ: Đi thẳng"), local mute badge, and a button to open/toggle the 2D Map.
 */
@Composable
fun TurnByTurnNavigationBanner(
    guidance: TurnByTurnGuidance,
    isMapCurrentlyVisible: Boolean,
    isLocalAlarmMuted: Boolean,
    onToggleMapView: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryBg = when (guidance.targetType) {
        "EXIT" -> Color(0xFF0F766E)         // Google Maps teal-green
        "FIRE_SENSOR" -> Color(0xFFB91C1C)  // Urgent fire red
        "TWO_STAGE" -> Color(0xFF6D28D9)    // Two-stage purple
        else -> Color(0xFF1D4ED8)           // Extinguisher blue
    }
    val subBarBg = when (guidance.targetType) {
        "EXIT" -> Color(0xFF115E59)
        "FIRE_SENSOR" -> Color(0xFF7F1D1D)
        "TWO_STAGE" -> Color(0xFF4C1D95)
        else -> Color(0xFF1E3A8A)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("turn_by_turn_navigation_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = primaryBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Google Maps-style Direction Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconForTurn(guidance.currentStep.direction),
                        contentDescription = guidance.currentStep.shortAction,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = TurnByTurnNavigator.formatDistanceVietnamese(guidance.currentStep.distanceMeters),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (guidance.isOutsideHouse) "NGOÀI NHÀ · ~${guidance.etaSeconds}s" else "TRONG NHÀ · ~${guidance.etaSeconds}s",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = guidance.currentStep.primaryText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Đích: ${guidance.targetName} (Tổng ${TurnByTurnNavigator.formatDistanceVietnamese(guidance.totalRemainingMeters)})",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onStopNavigation,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.22f))
                        .testTag("btn_stop_turn_by_turn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dừng dẫn đường",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Next Step ("Lượt chờ / Tiếp theo") + Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(subBarBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                guidance.nextStep?.let { next ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = iconForTurn(next.direction),
                            contentDescription = null,
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = next.primaryText,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLocalAlarmMuted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Đã tạm tắt báo động máy này",
                                color = Color(0xFFFDE68A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Button(
                        onClick = onToggleMapView,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = primaryBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_toggle_2d_map_from_banner")
                    ) {
                        Icon(
                            imageVector = if (isMapCurrentlyVisible) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isMapCurrentlyVisible) "Quay lại Tổng quan" else "Xem Bản Đồ 2D",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
