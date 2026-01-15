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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun CompassIndicator(
    heading: Float,
    modifier: Modifier = Modifier
) {
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

                // Draw cardinal directions (fixed)
                val directions = listOf("N", "E", "S", "W")
                val angles = listOf(0f, 90f, 180f, 270f)

                // Rotate the compass needle (pointing to north)
                rotate(-heading, pivot = center) {
                    // Draw north arrow
                    val arrowPath = Path().apply {
                        moveTo(center.x, center.y - radius + 12.dp.toPx())
                        lineTo(center.x - 8.dp.toPx(), center.y - 4.dp.toPx())
                        lineTo(center.x, center.y + 4.dp.toPx())
                        lineTo(center.x + 8.dp.toPx(), center.y - 4.dp.toPx())
                        close()
                    }
                    drawPath(arrowPath, color = Color.Red)

                    // Draw south part
                    val southPath = Path().apply {
                        moveTo(center.x, center.y + radius - 12.dp.toPx())
                        lineTo(center.x - 6.dp.toPx(), center.y + 4.dp.toPx())
                        lineTo(center.x, center.y - 4.dp.toPx())
                        lineTo(center.x + 6.dp.toPx(), center.y + 4.dp.toPx())
                        close()
                    }
                    drawPath(southPath, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // N label at top (fixed position)
            Text(
                text = "N",
                color = Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        Text(
            text = "${heading.roundToInt()}°",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
