package com.example.damperlocator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damperlocator.floorplan.FeatureType
import com.example.damperlocator.floorplan.FloorPlan
import com.example.damperlocator.floorplan.PolygonCalculator
import com.example.damperlocator.floorplan.RoomFeature
import com.example.damperlocator.floorplan.Vector2
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun FloorMapPreviewScreen(
    floorPlan: FloorPlan?,
    onContinue: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (floorPlan == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Floor plan not found")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) {
                Text(text = "Go Back")
            }
        }
        return
    }

    // Use cornerPoints if available (clean floor plan), otherwise fall back to pins
    val points2d = remember(floorPlan.cornerPoints, floorPlan.pins) {
        if (floorPlan.cornerPoints.isNotEmpty()) {
            floorPlan.cornerPoints
        } else {
            floorPlan.pins.map { it.position2d }
        }
    }

    val cornerCount = if (floorPlan.cornerPoints.isNotEmpty()) {
        // Don't count the closing point if it's the same as start
        if (floorPlan.isClosed && floorPlan.cornerPoints.size > 1) {
            floorPlan.cornerPoints.size - 1
        } else {
            floorPlan.cornerPoints.size
        }
    } else {
        floorPlan.pins.size
    }

    val boundingBox = remember(points2d) {
        PolygonCalculator.getBoundingBox(points2d)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = floorPlan.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Stats card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Corners", value = "$cornerCount")

                if (floorPlan.features.isNotEmpty()) {
                    StatItem(label = "Features", value = "${floorPlan.features.size}")
                }

                floorPlan.perimeterMeters?.let {
                    StatItem(label = "Perimeter", value = formatDistance(it))
                }

                if (floorPlan.isClosed) {
                    floorPlan.areaSquareMeters?.let {
                        StatItem(label = "Area", value = formatArea(it))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (floorPlan.isClosed) "Closed polygon" else "Open path",
                fontSize = 12.sp
            )
            Text(
                text = "North: ${floorPlan.northOffsetDegrees.roundToInt()}°",
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Canvas for 2D view
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF1A1A2E))
        ) {
            if (points2d.isEmpty()) {
                Text(
                    text = "No points to display",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                PolygonCanvas(
                    points = points2d,
                    features = floorPlan.features,
                    boundingBox = boundingBox,
                    isClosed = floorPlan.isClosed,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // North indicator
            Text(
                text = "N",
                color = Color.Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
            )

            // Scale indicator
            if (boundingBox.width > 0 || boundingBox.height > 0) {
                val maxDim = max(boundingBox.width, boundingBox.height)
                Text(
                    text = "Scale: ${formatDistance(maxDim)} max",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Continue Mapping")
            }

            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Export JSON")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Back to List")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
private fun PolygonCanvas(
    points: List<Vector2>,
    features: List<RoomFeature>,
    boundingBox: com.example.damperlocator.floorplan.BoundingBox,
    isClosed: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(24.dp)) {
        if (points.isEmpty()) return@Canvas

        val padding = 48.dp.toPx()
        val availableWidth = size.width - padding * 2
        val availableHeight = size.height - padding * 2

        // Calculate scale to fit
        val scaleX = if (boundingBox.width > 0.001f) availableWidth / boundingBox.width else 1f
        val scaleY = if (boundingBox.height > 0.001f) availableHeight / boundingBox.height else 1f
        val scale = minOf(scaleX, scaleY)

        // Transform function
        fun transform(point: Vector2): Offset {
            val x = padding + (point.x - boundingBox.minX) * scale + (availableWidth - boundingBox.width * scale) / 2
            val y = padding + (point.y - boundingBox.minY) * scale + (availableHeight - boundingBox.height * scale) / 2
            return Offset(x, y)
        }

        // Draw grid
        val gridColor = Color.White.copy(alpha = 0.1f)
        val gridSpacing = 50.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            x += gridSpacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            y += gridSpacing
        }

        // Draw polygon/path
        if (points.size >= 2) {
            val path = Path()
            val first = transform(points.first())
            path.moveTo(first.x, first.y)

            for (i in 1 until points.size) {
                val p = transform(points[i])
                path.lineTo(p.x, p.y)
            }

            if (isClosed) {
                path.close()

                // Fill
                drawPath(path, Color.Cyan.copy(alpha = 0.2f))
            }

            // Stroke
            drawPath(path, Color.Cyan, style = Stroke(width = 3.dp.toPx()))
        }

        // Draw corner points
        points.forEachIndexed { index, point ->
            val offset = transform(point)

            // Point marker
            drawCircle(
                color = if (index == 0) Color.Green else if (index == points.lastIndex && !isClosed) Color.Red else Color.White,
                radius = 8.dp.toPx(),
                center = offset
            )
            drawCircle(
                color = Color.Black,
                radius = 4.dp.toPx(),
                center = offset
            )
        }

        // Draw features with different colors per type
        features.forEach { feature ->
            val offset = transform(feature.position)
            val featureColor = when (feature.type) {
                FeatureType.DOOR -> Color(0xFF795548)      // Brown
                FeatureType.BEACON -> Color(0xFF00BCD4)    // Cyan
                FeatureType.DAMPER -> Color(0xFF9C27B0)    // Purple
                FeatureType.HVAC_VENT -> Color(0xFF607D8B) // Blue Grey
                FeatureType.HVAC_UNIT -> Color(0xFF3F51B5) // Indigo
                FeatureType.THERMOSTAT -> Color(0xFFFF5722) // Deep Orange
                FeatureType.PHOTO -> Color(0xFF8BC34A)     // Light Green
                FeatureType.OTHER -> Color(0xFF9E9E9E)     // Grey
            }

            // Outer glow
            drawCircle(
                color = featureColor.copy(alpha = 0.3f),
                radius = 14.dp.toPx(),
                center = offset
            )
            // Feature circle
            drawCircle(
                color = featureColor,
                radius = 10.dp.toPx(),
                center = offset
            )
            // Inner dot
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = offset
            )
        }
    }
}

private fun formatDistance(meters: Float): String {
    return if (meters < 1f) {
        "${(meters * 100).roundToInt()} cm"
    } else {
        "${String.format(Locale.US, "%.2f", meters)} m"
    }
}

private fun formatArea(sqMeters: Float): String {
    return if (sqMeters < 1f) {
        "${(sqMeters * 10000).roundToInt()} cm²"
    } else {
        "${String.format(Locale.US, "%.2f", sqMeters)} m²"
    }
}
