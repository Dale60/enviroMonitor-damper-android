package com.example.damperlocator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damperlocator.floorplan.Vector2
import kotlin.math.abs
import kotlin.math.max

/**
 * A real-time minimap showing the path being traced
 */
@Composable
fun PathMinimap(
    pathPoints: List<Vector2>,
    currentPosition: Vector2?,
    isRecording: Boolean,
    distanceTraveled: Float,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val borderColor = if (isRecording) {
        Color.Red.copy(alpha = pulseAlpha)
    } else {
        Color.Gray
    }

    Column(modifier = modifier) {
        // Recording indicator text
        if (isRecording) {
            Text(
                text = "REC",
                color = Color.Red.copy(alpha = pulseAlpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Map display
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    Color.Black.copy(alpha = 0.85f),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    width = if (isRecording) 4.dp else 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            if (pathPoints.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRecording) "Start walking!" else "Your map",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isRecording) "Move around the room" else "appears here",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            } else {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val padding = 20f

                // Get start point (anchor) - always the first recorded point
                val startPoint = pathPoints.first()

                // Calculate bounds centered on start point
                // Include all path points and current position
                var maxDistFromStart = 1f  // Minimum 1 meter radius to prevent jittery scaling

                pathPoints.forEach { point ->
                    val dx = abs(point.x - startPoint.x)
                    val dy = abs(point.y - startPoint.y)
                    maxDistFromStart = max(maxDistFromStart, max(dx, dy))
                }

                currentPosition?.let { pos ->
                    val dx = abs(pos.x - startPoint.x)
                    val dy = abs(pos.y - startPoint.y)
                    maxDistFromStart = max(maxDistFromStart, max(dx, dy))
                }

                // Add some margin so points aren't right at the edge
                val viewRadius = maxDistFromStart * 1.3f

                // Scale: map viewRadius to fit in half the canvas (start point in center)
                val availableSize = minOf(canvasWidth, canvasHeight) - padding * 2
                val scale = availableSize / (viewRadius * 2)

                // Start point is always at the center of the canvas
                val centerX = canvasWidth / 2
                val centerY = canvasHeight / 2

                fun toCanvas(point: Vector2): Offset {
                    return Offset(
                        x = centerX + (point.x - startPoint.x) * scale,
                        y = centerY + (point.y - startPoint.y) * scale
                    )
                }

                // Draw grid lines (fixed to canvas, not world)
                val gridColor = Color.Gray.copy(alpha = 0.2f)
                for (i in 0..4) {
                    val x = i * canvasWidth / 4
                    val y = i * canvasHeight / 4
                    drawLine(gridColor, Offset(x, 0f), Offset(x, canvasHeight), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, y), Offset(canvasWidth, y), strokeWidth = 1f)
                }

                // Draw path line - always draw from start to current position
                val path = Path()
                val first = toCanvas(pathPoints.first())
                path.moveTo(first.x, first.y)

                // Draw through all recorded points
                for (i in 1 until pathPoints.size) {
                    val point = toCanvas(pathPoints[i])
                    path.lineTo(point.x, point.y)
                }

                // Draw to current position if recording (live line to where you are now)
                if (isRecording && currentPosition != null) {
                    val current = toCanvas(currentPosition)
                    path.lineTo(current.x, current.y)
                }

                // Draw the path (thicker, more visible)
                drawPath(
                    path = path,
                    color = Color.Cyan,
                    style = Stroke(width = 5f)
                )

                // Draw start point (GREEN - fixed at center) - draw it larger
                val startCanvas = toCanvas(startPoint)
                // Outer glow
                drawCircle(
                    color = Color.Green.copy(alpha = 0.3f),
                    radius = 16f,
                    center = startCanvas
                )
                // Inner solid
                drawCircle(
                    color = Color.Green,
                    radius = 10f,
                    center = startCanvas
                )

                // Draw current position (YELLOW - moves as you walk)
                if (currentPosition != null) {
                    val current = toCanvas(currentPosition)
                    // Outer glow
                    drawCircle(
                        color = Color.Yellow.copy(alpha = 0.3f),
                        radius = 14f,
                        center = current
                    )
                    // Inner solid
                    drawCircle(
                        color = Color.Yellow,
                        radius = 8f,
                        center = current
                    )
                }
            }
        }

            // Distance label at bottom
            if (distanceTraveled > 0) {
                Text(
                    text = String.format("%.1fm walked", distanceTraveled),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // North indicator
            Text(
                text = "N",
                color = Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend - explaining the dots
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "WHAT YOU'LL SEE:",
                color = Color.Yellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Green dot legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Green, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "GREEN = Start",
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fixed in center",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Yellow dot legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Yellow, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "YELLOW = You",
                        color = Color.Yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Moves as you walk",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cyan line legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(5.dp)
                        .background(Color.Cyan, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "BLUE = Your path",
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Traces where you've been",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
