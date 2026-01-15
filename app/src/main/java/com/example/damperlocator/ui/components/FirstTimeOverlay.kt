package com.example.damperlocator.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PREFS_NAME = "floor_map_prefs"
private const val KEY_TUTORIAL_SEEN = "tutorial_seen"

/**
 * Check if the user has seen the first-time tutorial
 */
fun hasSeenTutorial(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
}

/**
 * Mark the tutorial as seen
 */
fun markTutorialSeen(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply()
}

/**
 * First-time tutorial overlay explaining how to map a floor
 */
@Composable
fun FirstTimeOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HOW TO MAP A ROOM",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Quick guide for HVAC professionals",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step 1
            TutorialStep(
                number = "1",
                title = "Start at a corner",
                description = "Stand at any corner of the room and tap START",
                color = Color.Green
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step 2
            TutorialStep(
                number = "2",
                title = "Walk & mark corners",
                description = "Walk along walls. At each corner, tap MARK WALL CORNER",
                color = Color(0xFF2196F3)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step 3
            TutorialStep(
                number = "3",
                title = "Mark HVAC equipment",
                description = "At dampers, vents, or units tap ADD DAMPER/VENT",
                color = Color(0xFF9C27B0)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step 4
            TutorialStep(
                number = "4",
                title = "Return to start",
                description = "Walk back to your starting corner and tap FINISH",
                color = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Tip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "TIP: Hold phone upright and point camera at the floor ahead of you",
                    color = Color.Yellow,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "GOT IT - LET'S MAP!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TutorialStep(
    number: String,
    title: String,
    description: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Number circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }
    }
}
