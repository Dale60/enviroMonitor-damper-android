package com.example.damperlocator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BubbleLevelIndicator(
    pitch: Float,  // Forward/backward tilt in degrees
    roll: Float,   // Left/right tilt in degrees
    modifier: Modifier = Modifier
) {
    val maxTilt = 15f  // Degrees at edge of indicator
    val isLevel = abs(pitch) < 2f && abs(roll) < 2f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            Canvas(modifier = Modifier.size(80.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 8.dp.toPx()

                // Draw outer circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw center target
                drawCircle(
                    color = if (isLevel) Color.Green else Color.White.copy(alpha = 0.5f),
                    radius = 8.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Draw crosshairs
                val crossSize = 6.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(center.x - crossSize, center.y),
                    end = Offset(center.x + crossSize, center.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(center.x, center.y - crossSize),
                    end = Offset(center.x, center.y + crossSize),
                    strokeWidth = 1.dp.toPx()
                )

                // Calculate bubble position based on tilt
                // Clamp to maxTilt and scale to radius
                val clampedPitch = pitch.coerceIn(-maxTilt, maxTilt)
                val clampedRoll = roll.coerceIn(-maxTilt, maxTilt)

                val bubbleX = center.x + (clampedRoll / maxTilt) * (radius - 12.dp.toPx())
                val bubbleY = center.y + (clampedPitch / maxTilt) * (radius - 12.dp.toPx())

                // Draw bubble
                val bubbleColor = if (isLevel) Color.Green else Color.Yellow
                drawCircle(
                    color = bubbleColor,
                    radius = 10.dp.toPx(),
                    center = Offset(bubbleX, bubbleY)
                )
                drawCircle(
                    color = bubbleColor.copy(alpha = 0.5f),
                    radius = 10.dp.toPx(),
                    center = Offset(bubbleX, bubbleY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        val statusText = if (isLevel) "Level" else "Tilt: ${abs(pitch).roundToInt()}°"
        Text(
            text = statusText,
            color = if (isLevel) Color.Green else Color.White,
            fontSize = 12.sp
        )
    }
}
