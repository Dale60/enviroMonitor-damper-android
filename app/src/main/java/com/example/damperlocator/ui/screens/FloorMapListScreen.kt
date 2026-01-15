package com.example.damperlocator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damperlocator.floorplan.FloorPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FloorMapListScreen(
    floorPlans: List<FloorPlan>,
    onNewPlan: () -> Unit,
    onContinuePlan: (String) -> Unit,
    onViewPlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Floor Plans",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNewPlan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "New Floor Plan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (floorPlans.isEmpty()) {
            Text(text = "No floor plans yet. Create one to get started.")
        } else {
            Text(text = "Saved Plans (${floorPlans.size})")
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(floorPlans) { plan ->
                    FloorPlanCard(
                        plan = plan,
                        onContinue = { onContinuePlan(plan.id) },
                        onView = { onViewPlan(plan.id) },
                        onDelete = { onDeletePlan(plan.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Back to Scanner")
        }
    }
}

@Composable
private fun FloorPlanCard(
    plan: FloorPlan,
    onContinue: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDate(plan.modifiedAtMs),
                        fontSize = 12.sp
                    )
                }

                val statusText = if (plan.isClosed) "Closed" else "Open"
                Text(
                    text = statusText,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                Text(
                    text = "${plan.pins.size} pins",
                    fontSize = 12.sp
                )
                plan.perimeterMeters?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Perimeter: ${formatDistance(it)}",
                        fontSize = 12.sp
                    )
                }
                plan.areaSquareMeters?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Area: ${formatArea(it)}",
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onContinue) {
                    Text(text = "Continue")
                }
                OutlinedButton(onClick = onView) {
                    Text(text = "View")
                }
                TextButton(onClick = onDelete) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

private fun formatDate(timeMs: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timeMs))
}

private fun formatDistance(meters: Float): String {
    return if (meters < 1f) {
        "${(meters * 100).roundToInt()} cm"
    } else {
        "${String.format(Locale.US, "%.1f", meters)} m"
    }
}

private fun formatArea(sqMeters: Float): String {
    return if (sqMeters < 1f) {
        "${(sqMeters * 10000).roundToInt()} cm²"
    } else {
        "${String.format(Locale.US, "%.1f", sqMeters)} m²"
    }
}
